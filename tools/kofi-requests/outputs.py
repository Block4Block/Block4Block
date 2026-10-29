"""Discord posts, stream slots and clip markers for the Ko-fi request service.

Discord is reached through channel webhooks only (no bot token): one for #requests, one for #announcements.
A webhook URL is a secret, so each lives in its own file, never in config.json. A missing file means that
output is simply off.
"""
import json
import os
import threading
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

DAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
KIND_NAMES = {"sign": "sign", "tree": "cherry tree", "animal": "animal name"}


def read_url(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read().strip()
    except OSError:
        return ""


def propose_slots(windows, tz_hours, after, count=2, min_hours=24):
    """The next `count` stream times from the weekly windows, at least `min_hours` after `after` (UTC)."""
    tz = timezone(timedelta(hours=tz_hours))
    local = after.astimezone(tz) + timedelta(hours=min_hours)
    out = []
    for add in range(0, 22):
        day = (local + timedelta(days=add)).date()
        for w in windows:
            try:
                wd = DAYS.index(w["day"][:3].lower())
                hh, mm = [int(x) for x in w["time"].split(":")]
            except (KeyError, ValueError):
                continue
            if day.weekday() != wd:
                continue
            slot = datetime(day.year, day.month, day.day, hh, mm, tzinfo=tz)
            if slot >= local:
                out.append(slot.astimezone(timezone.utc))
    out.sort()
    return out[:count]


class Outputs:
    def __init__(self, svc):
        self.svc = svc
        cfg = svc.cfg
        self.requests_hook = read_url(cfg["requests_webhook_file"])
        self.announce_hook = read_url(cfg["announce_webhook_file"])
        self.markers_file = cfg["markers_file"]
        self.lock = threading.Lock()
        self.sent = []  # what was sent, for tests and the dashboard log

    # ---- low level ----------------------------------------------------
    def _http(self, method, url, obj=None, file=None):
        """JSON body, or multipart when `file` = (name, bytes, content type) is given."""
        if file:
            b = uuid.uuid4().hex
            payload = json.dumps(obj or {})
            body = (("--%s\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n%s\r\n"
                     "--%s\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n")
                    % (b, payload, b, file[0], file[2])).encode() + file[1] + ("\r\n--%s--\r\n" % b).encode()
            headers = {"Content-Type": "multipart/form-data; boundary=" + b}
        else:
            body = json.dumps(obj).encode() if obj is not None else None
            headers = {"Content-Type": "application/json"}
        headers["User-Agent"] = "b4b-requests/1"
        req = urllib.request.Request(url, body, headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=8) as r:
                data = r.read()
                return json.loads(data) if data else {}
        except (urllib.error.URLError, ValueError, OSError) as e:
            self.svc.store.log("discord error: %s" % e)
            return None

    def _meta_get(self, key):
        r = self.svc.store.db.execute("SELECT v FROM meta WHERE k=?", (key,)).fetchone()
        return r["v"] if r else None

    def _meta_set(self, key, val):
        self.svc.store.db.execute("INSERT INTO meta(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v", (key, val))
        self.svc.store.db.commit()

    # ---- the pinned status message -----------------------------------
    def status_text(self):
        s = self.svc.state()
        lines = ["**Ko-fi requests**", "Queued: %d of %d" % (s["open_count"], s["threshold_count"])]
        if s["oldest_age_days"] is not None:
            left = max(0, self.svc.cfg["threshold_days"] - s["oldest_age_days"])
            lines.append("Oldest: %.1f days old, fulfilled live within %d days (about %.0f left)" % (s["oldest_age_days"], self.svc.cfg["threshold_days"], left))
        when = self._meta_get("stream_at")
        if when:
            lines.append("Next stream: <t:%d:F> (<t:%d:R>)" % (int(float(when)), int(float(when))))
        elif s["open_count"] == 0:
            lines.append("No requests waiting.")
        return "\n".join(lines)

    def update_status(self):
        if not self.requests_hook:
            return
        text = self.status_text()
        mid = self._meta_get("status_message_id")
        if mid:
            r = self._http("PATCH", "%s/messages/%s" % (self.requests_hook, mid), {"content": text})
            if r is not None:
                self.sent.append(("status-edit", text))
                return
        r = self._http("POST", self.requests_hook + "?wait=true", {"content": text})
        if r and r.get("id"):
            self._meta_set("status_message_id", str(r["id"]))
            self.sent.append(("status-new", text))

    # ---- events -------------------------------------------------------
    def on_event(self, what, rid):
        threading.Thread(target=self._handle, args=(what, rid), daemon=True).start()

    def _handle(self, what, rid):
        with self.lock:
            try:
                if what == "done":
                    self.marker(rid)
                    self.thank(rid)
                self.update_status()
            except Exception as e:  # never let a Discord problem touch the queue
                self.svc.store.log("outputs error: %s" % e)

    def row(self, rid):
        # `rid` is a row id for the button, or a transaction id for intake
        r = self.svc.store.db.execute("SELECT * FROM requests WHERE id=?", (rid,)).fetchone()
        return dict(r) if r else None

    def marker(self, rid):
        """One line per finished request for the highlight pipeline to cut around."""
        r = self.row(rid)
        if not r:
            return
        line = {"at": r["done_at"], "id": r["id"], "kind": r["kind"], "location": r["location"],
                "buyer": r["buyer"] if r["is_public"] else None}
        with open(self.markers_file, "a", encoding="utf-8") as f:
            f.write(json.dumps(line) + "\n")

    def thank(self, rid, screenshot=None):
        r = self.row(rid)
        if not r or not self.requests_hook:
            return
        who = r["buyer"] if r["is_public"] else "a supporter"
        text = "Thank you %s! Your %s is done." % (who, KIND_NAMES.get(r["kind"], "request"))
        out = self._http("POST", self.requests_hook + "?wait=true", {"content": text}, screenshot)
        self.sent.append(("thanks", text))
        return out

    def send_screenshot(self, rid, png):
        """Called when the dashboard has a cropped screenshot (after the preview ran out)."""
        with self.lock:
            self.thank(rid, ("request-%s.png" % rid, png, "image/png"))
            self.svc.store.log("screenshot sent for %s" % rid)

    # ---- stream slots -------------------------------------------------
    def proposals(self):
        cfg = self.svc.cfg
        return [d.strftime("%Y-%m-%dT%H:%M:%SZ") for d in propose_slots(cfg["stream_windows"], cfg["tz_hours"], datetime.now(timezone.utc))]

    def schedule(self, slot_iso):
        """The owner taps a slot: remember it and announce it, naming the buyers' Discord names."""
        slot = datetime.strptime(slot_iso, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        if slot < datetime.now(timezone.utc) - timedelta(minutes=5):
            return False
        ts = int(slot.timestamp())
        self._meta_set("stream_at", str(ts))
        rows = [dict(r) for r in self.svc.store.db.execute("SELECT * FROM requests WHERE status='queued'")]
        names = sorted({r["discord"] for r in rows if r["discord"]})
        text = ("**Request stream:** <t:%d:F> (<t:%d:R>). %d request%s will be done live in Block4Block. "
                "Join in game and watch it happen." % (ts, ts, len(rows), "" if len(rows) == 1 else "s"))
        if names:
            text += "\nBuyers: " + ", ".join(names)
        if self.announce_hook:
            self._http("POST", self.announce_hook, {"content": text})
        self.sent.append(("announce", text))
        self.update_status()
        return True
