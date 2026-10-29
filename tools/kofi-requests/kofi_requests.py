#!/usr/bin/env python3
"""Ko-fi request service for Block4Block (standard library only).

Receives Ko-fi payments, checks the requested text, keeps the queue and serves
the owner's second-monitor dashboard. The Minecraft plugin only reports finished
signs and named animals to /api/plugin/complete. Nothing here runs in game.

Run:  python3 kofi_requests.py /path/to/config.json
"""
import hashlib
import hmac
import http.server
import json
import os
import re
import secrets
import sqlite3
import sys
import threading
import time
import urllib.parse
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
URL_RE = re.compile(r"(https?://|www\.|\b[a-z0-9-]+\.(com|net|org|gg|io|ly|me|xyz|ru|tv)\b)", re.I)
DEFAULTS = {
    "listen_host": "127.0.0.1",
    "listen_port": 8765,
    "db_path": "requests.db",
    "kofi_token_file": "kofi_token.txt",        # one line: Ko-fi verification token
    "dashboard_password_file": "dashboard_password.txt",
    "plugin_secret_file": "plugin_secret.txt",  # shared with the plugin
    "bad_words_file": "bad_words.txt",           # one word per line
    "threshold_count": 5,
    "threshold_days": 14,
    "sign_lines": 4,
    "sign_line_chars": 15,
    "name_max_chars": 32,
    # Ko-fi shop item direct link code -> request type (sign, tree, animal)
    "item_types": {},
    # fallback: words in the item name that give the type
    "type_keywords": {"sign": ["sign"], "tree": ["tree", "cherry"], "animal": ["animal", "name an", "name a"]},
}


def now():
    return datetime.now(timezone.utc)


def iso(dt=None):
    return (dt or now()).strftime("%Y-%m-%dT%H:%M:%SZ")


def read_secret(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read().strip()
    except OSError:
        return ""


class Store:
    def __init__(self, path):
        self.lock = threading.Lock()
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.execute(
            """CREATE TABLE IF NOT EXISTS requests(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            txn TEXT UNIQUE, buyer TEXT, is_public INTEGER, kind TEXT, text TEXT,
            amount TEXT, currency TEXT, paid_at TEXT, discord TEXT, support_code TEXT,
            status TEXT, flag_reason TEXT, position REAL,
            active INTEGER DEFAULT 0, done_at TEXT, location TEXT, done_by TEXT)"""
        )
        self.db.execute("CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY AUTOINCREMENT, at TEXT, what TEXT)")
        self.db.commit()

    def log(self, what):
        self.db.execute("INSERT INTO events(at,what) VALUES(?,?)", (iso(), what))
        self.db.commit()


def classify(cfg, item_code, item_name, message):
    """Return the request type: sign, tree, animal or unknown."""
    if item_code and item_code in cfg["item_types"]:
        return cfg["item_types"][item_code]
    hay = (item_name or "").lower()
    for kind, words in cfg["type_keywords"].items():
        if any(w in hay for w in words):
            return kind
    return "unknown"


def parse_message(message):
    """Pull the discord name and /support code out of the buyer's message; the rest is the text."""
    code = None
    m = re.search(r"\bB4B-[A-Z0-9]{4}\b", message or "")
    if m:
        code = m.group(0)
        message = message.replace(code, "")
    discord = None
    m = re.search(r"(?im)^\s*discord\s*[:=-]\s*(.+)$", message or "")
    if m:
        discord = m.group(1).strip()
        message = re.sub(r"(?im)^\s*discord\s*[:=-].*$", "", message)
    return message.strip(), discord, code


def auto_check(cfg, bad_words, kind, text):
    """Return None when fine, otherwise the reason it needs the owner."""
    if kind == "unknown":
        return "Could not tell what was ordered"
    if not text:
        return "No text or name in the message"
    low = text.lower()
    if URL_RE.search(text):
        return "Contains a link"
    for w in bad_words:
        if w and re.search(r"\b" + re.escape(w) + r"\b", low):
            return "Contains a word on the block list"
    if kind == "sign":
        lines = text.splitlines()
        if len(lines) > cfg["sign_lines"]:
            return "More than %d lines" % cfg["sign_lines"]
        for ln in lines:
            if len(ln) > cfg["sign_line_chars"]:
                return "A line is longer than %d characters" % cfg["sign_line_chars"]
    elif kind == "animal":
        if len(text) > cfg["name_max_chars"] or "\n" in text:
            return "Name is longer than %d characters or has several lines" % cfg["name_max_chars"]
    return None


def norm(s):
    return re.sub(r"\s+", " ", (s or "").strip()).lower()


class Service:
    def __init__(self, cfg):
        self.cfg = cfg
        self.store = Store(cfg["db_path"])
        self.kofi_token = read_secret(cfg["kofi_token_file"])
        self.dash_password = read_secret(cfg["dashboard_password_file"])
        self.plugin_secret = read_secret(cfg["plugin_secret_file"])
        self.bad_words = [w.strip().lower() for w in read_secret(cfg["bad_words_file"]).splitlines()]
        self.sessions = {}
        self.notifier = None  # set by later steps (Discord)

    # ---- intake -------------------------------------------------------
    def intake(self, payload):
        if not self.kofi_token or not hmac.compare_digest(str(payload.get("verification_token", "")), self.kofi_token):
            return 403, "bad token"
        ptype = payload.get("type")
        if ptype not in ("Commission", "Shop Order", "Donation", "Subscription"):
            return 200, "ignored"
        if ptype in ("Donation", "Subscription"):
            self.store.log("%s from %s (not a request)" % (ptype, payload.get("from_name")))
            return 200, "noted"
        txn = payload.get("kofi_transaction_id") or payload.get("message_id")
        if not txn:
            return 400, "no transaction id"
        item = (payload.get("shop_items") or [{}])[0]
        kind = classify(self.cfg, item.get("direct_link_code"), item.get("variation_name") or item.get("name") or payload.get("tier_name") or "", payload.get("message"))
        text, discord, code = parse_message(payload.get("message") or "")
        reason = auto_check(self.cfg, self.bad_words, kind, text)
        with self.store.lock:
            if self.store.db.execute("SELECT 1 FROM requests WHERE txn=?", (txn,)).fetchone():
                return 200, "duplicate"
            pos = time.time()
            self.store.db.execute(
                "INSERT INTO requests(txn,buyer,is_public,kind,text,amount,currency,paid_at,discord,support_code,status,flag_reason,position)"
                " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (txn, payload.get("from_name") or "Someone", 1 if payload.get("is_public", True) else 0, kind, text,
                 str(payload.get("amount") or ""), payload.get("currency") or "", payload.get("timestamp") or iso(),
                 discord, code, "flagged" if reason else "queued", reason, pos),
            )
            self.store.log("Request %s from %s: %s" % (txn, payload.get("from_name"), "flagged: " + reason if reason else "queued"))
            self.store.db.commit()
        if self.notifier:
            self.notifier("intake", txn)
        return 200, "ok"

    # ---- queue --------------------------------------------------------
    def rows(self, where, args=()):
        return [dict(r) for r in self.store.db.execute("SELECT * FROM requests WHERE " + where + " ORDER BY position", args)]

    def state(self):
        with self.store.lock:
            active = self.rows("active=1 AND status='queued'")
            queued = self.rows("status='queued' AND active=0")
            flagged = self.rows("status='flagged'")
            done = [dict(r) for r in self.store.db.execute("SELECT * FROM requests WHERE status='done' ORDER BY done_at DESC LIMIT 10")]
            open_rows = self.rows("status IN ('queued')")
        oldest = min((r["paid_at"] for r in open_rows), default=None)
        age_days = None
        if oldest:
            try:
                age_days = (now() - datetime.strptime(oldest[:19], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=timezone.utc)).total_seconds() / 86400
            except ValueError:
                age_days = None
        due = len(open_rows) >= self.cfg["threshold_count"] or (age_days is not None and age_days >= self.cfg["threshold_days"] - 2)
        return {
            "active": active[0] if active else None,
            "queued": queued,
            "flagged": flagged,
            "done": done,
            "open_count": len(open_rows),
            "threshold_count": self.cfg["threshold_count"],
            "oldest_age_days": age_days,
            "stream_due": due,
            "now": iso(),
        }

    def action(self, name, rid):
        with self.store.lock:
            db = self.store.db
            row = db.execute("SELECT * FROM requests WHERE id=?", (rid,)).fetchone() if rid else None
            if name == "next":
                db.execute("UPDATE requests SET active=0 WHERE active=1")
                nxt = db.execute("SELECT id FROM requests WHERE status='queued' ORDER BY position LIMIT 1").fetchone()
                if nxt:
                    db.execute("UPDATE requests SET active=1 WHERE id=?", (nxt["id"],))
            elif name == "done" and row:
                db.execute("UPDATE requests SET status='done', active=0, done_at=?, done_by='button' WHERE id=?", (iso(), rid))
            elif name == "skip" and row:
                db.execute("UPDATE requests SET active=0, position=? WHERE id=?", (time.time() + 1e6, rid))
            elif name == "flag" and row:
                db.execute("UPDATE requests SET status='flagged', active=0, flag_reason=COALESCE(flag_reason,'Flagged by owner') WHERE id=?", (rid,))
            elif name == "approve" and row:
                db.execute("UPDATE requests SET status='queued', flag_reason=NULL WHERE id=?", (rid,))
            elif name == "refund" and row:
                db.execute("UPDATE requests SET status='refund', active=0 WHERE id=?", (rid,))
            else:
                return False
            db.commit()
            self.store.log("%s %s" % (name, rid or ""))
        if name == "done" and self.notifier:
            self.notifier("done", rid)
        return True

    def plugin_complete(self, body):
        """The plugin reports a finished sign or named animal."""
        kind = body.get("kind")
        text = norm(body.get("text"))
        loc = "%s %s,%s,%s" % (body.get("world", ""), body.get("x"), body.get("y"), body.get("z"))
        with self.store.lock:
            row = self.store.db.execute("SELECT * FROM requests WHERE active=1 AND status='queued'").fetchone()
            if not row or row["kind"] != kind or norm(row["text"]) != text:
                return False
            self.store.db.execute("UPDATE requests SET status='done', active=0, done_at=?, location=?, done_by='plugin' WHERE id=?", (iso(), loc, row["id"]))
            self.store.db.commit()
            self.store.log("detected %s for %s at %s" % (kind, row["id"], loc))
            rid = row["id"]
        if self.notifier:
            self.notifier("done", rid)
        return True


PAGE = open(os.path.join(HERE, "dashboard.html"), encoding="utf-8").read() if os.path.exists(os.path.join(HERE, "dashboard.html")) else "<p>dashboard.html missing</p>"


def make_handler(svc):
    class H(http.server.BaseHTTPRequestHandler):
        server_version = "b4b-requests"

        def log_message(self, fmt, *a):
            pass

        def send(self, code, body, ctype="text/plain; charset=utf-8", headers=None):
            data = body if isinstance(body, bytes) else body.encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            for k, v in (headers or {}).items():
                self.send_header(k, v)
            self.end_headers()
            self.wfile.write(data)

        def body(self):
            n = int(self.headers.get("Content-Length") or 0)
            if n > 100000:
                return b""
            return self.rfile.read(n)

        def authed(self):
            c = self.headers.get("Cookie") or ""
            m = re.search(r"b4bdash=([A-Za-z0-9_-]+)", c)
            return bool(m and svc.sessions.get(m.group(1), 0) > time.time())

        def do_GET(self):
            path = urllib.parse.urlparse(self.path).path
            if path == "/dashboard":
                if not self.authed():
                    return self.send(200, LOGIN, "text/html; charset=utf-8")
                return self.send(200, PAGE, "text/html; charset=utf-8")
            if path == "/api/state":
                if not self.authed():
                    return self.send(401, "login")
                return self.send(200, json.dumps(svc.state()), "application/json")
            if path == "/health":
                return self.send(200, "ok")
            self.send(404, "not found")

        def do_POST(self):
            path = urllib.parse.urlparse(self.path).path
            raw = self.body()
            if path == "/hooks/kofi":
                form = urllib.parse.parse_qs(raw.decode("utf-8", "replace"))
                try:
                    payload = json.loads((form.get("data") or ["{}"])[0])
                except ValueError:
                    return self.send(400, "bad data")
                code, msg = svc.intake(payload)
                return self.send(code, msg)
            if path == "/login":
                form = urllib.parse.parse_qs(raw.decode("utf-8", "replace"))
                pw = (form.get("password") or [""])[0]
                if svc.dash_password and hmac.compare_digest(pw, svc.dash_password):
                    tok = secrets.token_urlsafe(24)
                    svc.sessions[tok] = time.time() + 12 * 3600
                    return self.send(303, "", headers={"Location": "/dashboard", "Set-Cookie": "b4bdash=%s; Path=/; HttpOnly; SameSite=Strict; Max-Age=43200" % tok})
                time.sleep(1)
                return self.send(303, "", headers={"Location": "/dashboard"})
            if path == "/api/action":
                if not self.authed():
                    return self.send(401, "login")
                try:
                    j = json.loads(raw or b"{}")
                except ValueError:
                    return self.send(400, "bad json")
                ok = svc.action(j.get("action"), j.get("id"))
                return self.send(200 if ok else 400, json.dumps({"ok": ok}), "application/json")
            if path == "/api/plugin/complete":
                if not svc.plugin_secret or not hmac.compare_digest(self.headers.get("X-B4B-Secret", ""), svc.plugin_secret):
                    return self.send(403, "bad secret")
                try:
                    j = json.loads(raw or b"{}")
                except ValueError:
                    return self.send(400, "bad json")
                return self.send(200, json.dumps({"matched": svc.plugin_complete(j)}), "application/json")
            self.send(404, "not found")

    return H


LOGIN = """<!doctype html><meta charset=utf-8><title>Requests</title>
<body style="font:18px sans-serif;background:#111;color:#eee;padding:40px">
<form method=post action=/login><input type=password name=password autofocus placeholder=Password style="font-size:18px;padding:8px">
<button style="font-size:18px;padding:8px">Open</button></form></body>"""


def load_config(path):
    cfg = dict(DEFAULTS)
    base = os.path.dirname(os.path.abspath(path))
    with open(path, "r", encoding="utf-8") as f:
        cfg.update(json.load(f))
    for k in ("db_path", "kofi_token_file", "dashboard_password_file", "plugin_secret_file", "bad_words_file"):
        if not os.path.isabs(cfg[k]):
            cfg[k] = os.path.join(base, cfg[k])
    return cfg


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    cfg = load_config(sys.argv[1])
    svc = Service(cfg)
    srv = http.server.ThreadingHTTPServer((cfg["listen_host"], cfg["listen_port"]), make_handler(svc))
    print("Ko-fi request service listening on %s:%s" % (cfg["listen_host"], cfg["listen_port"]))
    srv.serve_forever()


if __name__ == "__main__":
    main()
