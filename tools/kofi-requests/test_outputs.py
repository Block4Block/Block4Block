#!/usr/bin/env python3
"""Tests for Discord posts, stream slots, markers and screenshots against a fake Discord. Run: python3 test_outputs.py"""
import base64
import http.server
import json
import os
import tempfile
import threading
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone

import kofi_requests as k
import outputs as o

fails = []


def check(name, cond):
    print(("PASS " if cond else "FAIL ") + name)
    if not cond:
        fails.append(name)


# a fake Discord that records what it receives
got = []


class Fake(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def handle_any(self):
        n = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(n)
        got.append((self.command, self.path, self.headers.get("Content-Type", ""), body))
        out = json.dumps({"id": "msg1"}).encode()
        self.send_response(200)
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

    do_POST = do_PATCH = handle_any


fsrv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Fake)
threading.Thread(target=fsrv.serve_forever, daemon=True).start()
fbase = "http://127.0.0.1:%d" % fsrv.server_address[1]

tmp = tempfile.mkdtemp()
for name, val in (("kofi_token.txt", "TOK"), ("dashboard_password.txt", "pw"), ("plugin_secret.txt", "SEC"), ("bad_words.txt", ""),
                  ("discord_requests_webhook.txt", fbase + "/req"), ("discord_announce_webhook.txt", fbase + "/ann")):
    open(os.path.join(tmp, name), "w").write(val)
cfgp = os.path.join(tmp, "config.json")
json.dump({"item_types": {"signcode": "sign"}, "threshold_count": 2}, open(cfgp, "w"))
svc = k.Service(k.load_config(cfgp))
srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), k.make_handler(svc))
threading.Thread(target=srv.serve_forever, daemon=True).start()
base = "http://127.0.0.1:%d" % srv.server_address[1]


def wait(cond, secs=3):
    t = time.time()
    while time.time() - t < secs:
        if cond():
            return True
        time.sleep(0.05)
    return False


def kofi(txn, name, public=True, msg="Hello"):
    d = {"verification_token": "TOK", "type": "Commission", "kofi_transaction_id": txn, "from_name": name, "is_public": public,
         "message": msg + "\nDiscord: " + name.lower(), "amount": "1", "currency": "USD", "timestamp": k.iso(), "shop_items": [{"direct_link_code": "signcode"}]}
    urllib.request.urlopen(urllib.request.Request(base + "/hooks/kofi", urllib.parse.urlencode({"data": json.dumps(d)}).encode()))


# status message: first a POST (wait=true), then an edit
kofi("a1", "Sam")
check("status posted once", wait(lambda: any(g[0] == "POST" and "/req" in g[1] and "wait=true" in g[1] for g in got)))
kofi("a2", "Pat")
check("status then edited, not reposted", wait(lambda: any(g[0] == "PATCH" and g[1].startswith("/req/messages/msg1") for g in got)))
patch = [g for g in got if g[0] == "PATCH"][-1]
check("status shows 2 of 2", b"Queued: 2 of 2" in patch[3])

# slot proposals when due
cookie = None


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **kw):
        return None


try:
    urllib.request.build_opener(NoRedirect).open(urllib.request.Request(base + "/login", b"password=pw"))
except urllib.error.HTTPError as e:
    cookie = e.headers["Set-Cookie"].split(";")[0]


def api(path, obj):
    r = urllib.request.urlopen(urllib.request.Request(base + path, json.dumps(obj).encode(), {"Cookie": cookie}))
    return json.loads(r.read() or b"{}")


st = json.loads(urllib.request.urlopen(urllib.request.Request(base + "/api/state", headers={"Cookie": cookie})).read())
check("two proposals when due", st["stream_due"] and len(st["proposals"]) == 2)
check("proposals are Sat or Sun 20:00 in UTC+8, at least a day away",
      all(datetime.strptime(p, "%Y-%m-%dT%H:%M:%SZ").hour == 12 for p in st["proposals"]))
slot = st["proposals"][0]
api("/api/action", {"action": "schedule", "slot": slot})
check("announcement posted with buyers' discord names", wait(lambda: any("/ann" in g[1] and b"sam" in g[3] and b"pat" in g[3] for g in got)))
try:
    api("/api/action", {"action": "schedule", "slot": "2020-01-01T00:00:00Z"})
    check("a past slot is refused", False)
except urllib.error.HTTPError as e:
    check("a past slot is refused", e.code == 400)

# finishing: marker and thank-you; private buyer is not named
api("/api/action", {"action": "next"})
st = json.loads(urllib.request.urlopen(urllib.request.Request(base + "/api/state", headers={"Cookie": cookie})).read())
rid = st["active"]["id"]
api("/api/action", {"action": "done", "id": rid})
check("thank-you names the public buyer", wait(lambda: any(b"Thank you Sam" in g[3] or b"Thank you Pat" in g[3] for g in got)))
check("marker line written", wait(lambda: os.path.exists(svc.cfg["markers_file"]) and json.loads(open(svc.cfg["markers_file"]).readline())["id"] == rid))
kofi("a3", "Hidden", public=False)
api("/api/action", {"action": "next"})
st = json.loads(urllib.request.urlopen(urllib.request.Request(base + "/api/state", headers={"Cookie": cookie})).read())
rid3 = [r for r in st["queued"] + ([st["active"]] if st["active"] else []) if r["buyer"] == "Hidden"]
if not rid3:
    api("/api/action", {"action": "skip", "id": st["active"]["id"]})
    api("/api/action", {"action": "next"})
st = json.loads(urllib.request.urlopen(urllib.request.Request(base + "/api/state", headers={"Cookie": cookie})).read())
# make the private one active directly
svc.store.db.execute("UPDATE requests SET active=0"); svc.store.db.execute("UPDATE requests SET active=1 WHERE buyer='Hidden'"); svc.store.db.commit()
hid = svc.rows("buyer='Hidden'")[0]["id"]
api("/api/action", {"action": "done", "id": hid})
check("private buyer is thanked as a supporter", wait(lambda: any(b"Thank you a supporter" in g[3] for g in got)))
lines = [json.loads(l) for l in open(svc.cfg["markers_file"])]
check("private buyer's name is not in the marker", [l for l in lines if l["id"] == hid][0]["buyer"] is None)

# screenshot upload goes to Discord as a multipart file
png = b"\x89PNG\r\n\x1a\n" + b"0" * 50
api("/api/screenshot", {"id": rid, "png": base64.b64encode(png).decode()})
check("screenshot reached Discord as a file", wait(lambda: any(g[2].startswith("multipart/form-data") and b"filename=\"request-%d.png\"" % rid in g[3] for g in got)))
try:
    api("/api/screenshot", {"id": rid, "png": base64.b64encode(b"not a png").decode()})
    check("non-PNG refused", False)
except urllib.error.HTTPError as e:
    check("non-PNG refused", e.code == 400)

# slot maths on its own
after = datetime(2026, 11, 3, 3, 0, tzinfo=timezone.utc)  # Tue 11:00 in UTC+8
sl = o.propose_slots([{"day": "Sat", "time": "20:00"}], 8, after)
check("next Saturdays 20:00 PHT", [s.strftime("%Y-%m-%d %H:%M") for s in sl] == ["2026-11-07 12:00", "2026-11-14 12:00"])

print("\n%d failed" % len(fails))
raise SystemExit(1 if fails else 0)
