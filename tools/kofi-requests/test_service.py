#!/usr/bin/env python3
"""Self-test with fake Ko-fi payments. Run: python3 test_service.py"""
import json
import os
import tempfile
import threading
import urllib.parse
import urllib.request
import http.server
import kofi_requests as k

tmp = tempfile.mkdtemp()
for name, val in (("kofi_token.txt", "TOK"), ("dashboard_password.txt", "pw"), ("plugin_secret.txt", "SEC"), ("bad_words.txt", "badword\n")):
    open(os.path.join(tmp, name), "w").write(val)
cfgp = os.path.join(tmp, "config.json")
json.dump({"listen_port": 0, "item_types": {"signcode": "sign", "animalcode": "animal", "treecode": "tree"}}, open(cfgp, "w"))
cfg = k.load_config(cfgp)
svc = k.Service(cfg)
srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), k.make_handler(svc))
port = srv.server_address[1]
threading.Thread(target=srv.serve_forever, daemon=True).start()
base = "http://127.0.0.1:%d" % port
fails = []


def check(name, cond):
    print(("PASS " if cond else "FAIL ") + name)
    if not cond:
        fails.append(name)


def kofi(txn, code, msg, token="TOK", typ="Commission", name="Sam"):
    d = {"verification_token": token, "type": typ, "kofi_transaction_id": txn, "from_name": name, "is_public": True,
         "message": msg, "amount": "1.00", "currency": "USD", "timestamp": k.iso(), "shop_items": [{"direct_link_code": code}]}
    body = urllib.parse.urlencode({"data": json.dumps(d)}).encode()
    try:
        r = urllib.request.urlopen(urllib.request.Request(base + "/hooks/kofi", body))
        return r.status
    except urllib.error.HTTPError as e:
        return e.code


def post(path, obj, headers=None, cookie=None):
    h = dict(headers or {})
    if cookie:
        h["Cookie"] = cookie
    try:
        r = urllib.request.urlopen(urllib.request.Request(base + path, json.dumps(obj).encode(), h))
        return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, {}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **kw):
        return None


# intake
check("wrong token rejected", kofi("t0", "signcode", "hi", token="WRONG") == 403)
check("good sign accepted", kofi("t1", "signcode", "Hello\nWorld") == 200)
check("duplicate ignored", kofi("t1", "signcode", "Hello\nWorld") == 200)
check("one row after duplicate", len(svc.rows("txn='t1'")) == 1)
check("long line flagged", kofi("t2", "signcode", "x" * 16) == 200 and svc.rows("txn='t2'")[0]["status"] == "flagged")
check("link flagged", kofi("t3", "animalcode", "visit foo.com") == 200 and svc.rows("txn='t3'")[0]["status"] == "flagged")
check("bad word flagged", kofi("t4", "animalcode", "a badword here") == 200 and svc.rows("txn='t4'")[0]["status"] == "flagged")
check("five lines flagged", kofi("t5", "signcode", "a\nb\nc\nd\ne") == 200 and svc.rows("txn='t5'")[0]["status"] == "flagged")
check("animal queued", kofi("t6", "animalcode", "Sir Moo\nDiscord: sam#1\nB4B-7KQ2") == 200)
r6 = svc.rows("txn='t6'")[0]
check("discord and code parsed out", r6["text"] == "Sir Moo" and r6["discord"] == "sam#1" and r6["support_code"] == "B4B-7KQ2" and r6["status"] == "queued")
check("unknown item flagged", kofi("t7", "nope", "text") == 200 and svc.rows("txn='t7'")[0]["status"] == "flagged")
check("donation is not a request", kofi("t8", "x", "thanks", typ="Donation") == 200 and not svc.rows("txn='t8'"))

# several items and quantities
def kofi_multi(txn, items):
    d = {"verification_token": "TOK", "type": "Shop Order", "kofi_transaction_id": txn, "from_name": "Pat", "is_public": True,
         "message": "Hi", "amount": "3", "currency": "USD", "timestamp": k.iso(), "shop_items": items, "email": "x@y.z"}
    body = urllib.parse.urlencode({"data": json.dumps(d)}).encode()
    return urllib.request.urlopen(urllib.request.Request(base + "/hooks/kofi", body)).status
check("quantity 2 makes two requests", kofi_multi("m1", [{"direct_link_code": "animalcode", "quantity": 2}]) == 200 and len(svc.rows("txn LIKE 'm1#%'")) == 2)
check("repeat of a multi order is ignored", kofi_multi("m1", [{"direct_link_code": "animalcode", "quantity": 2}]) == 200 and len(svc.rows("txn LIKE 'm1#%'")) == 2)
check("email never stored", not any("x@y.z" in r["what"] for r in map(dict, svc.store.db.execute("SELECT what FROM events"))))
svc.store.db.execute("DELETE FROM requests WHERE txn LIKE 'm1#%'"); svc.store.db.commit()

# dashboard login
opener = urllib.request.build_opener(NoRedirect)
try:
    urllib.request.urlopen(base + "/api/state")
    check("state needs login", False)
except urllib.error.HTTPError as e:
    check("state needs login", e.code == 401)
try:
    opener.open(urllib.request.Request(base + "/login", b"password=pw"))
except urllib.error.HTTPError as e:
    cookie = e.headers["Set-Cookie"].split(";")[0]
    check("login gives cookie", cookie.startswith("b4bdash="))
req = urllib.request.Request(base + "/api/state", headers={"Cookie": cookie})
st = json.loads(urllib.request.urlopen(req).read())
check("queue shows 2 queued, 5 flagged", len(st["queued"]) == 2 and len(st["flagged"]) == 5)
check("dashboard page served", b"Block4Block requests" in urllib.request.urlopen(urllib.request.Request(base + "/dashboard", headers={"Cookie": cookie})).read())

# approve a flagged one, then run the queue
rid2 = svc.rows("txn='t2'")[0]["id"]
post("/api/action", {"action": "refund", "id": rid2}, cookie=cookie)
check("refund removes from flagged", svc.rows("txn='t2'")[0]["status"] == "refund")
post("/api/action", {"action": "next"}, cookie=cookie)
st = svc.state()
check("next sets the first queued active", st["active"] and st["active"]["txn"] == "t1")

# plugin detection
H = {"X-B4B-Secret": "SEC"}
check("plugin wrong secret rejected", post("/api/plugin/complete", {"kind": "sign", "text": "Hello\nWorld"}, {"X-B4B-Secret": "no"})[0] == 403)
check("wrong text does not match", post("/api/plugin/complete", {"kind": "sign", "text": "Other"}, H)[1].get("matched") is False)
check("wrong kind does not match", post("/api/plugin/complete", {"kind": "animal", "text": "Hello World"}, H)[1].get("matched") is False)
res = post("/api/plugin/complete", {"kind": "sign", "text": "hello\n world", "world": "world", "x": 1, "y": 64, "z": 2}, H)[1]
check("matching sign marks done (case and spacing ignored)", res.get("matched") is True)
d = svc.rows("txn='t1'")[0]
check("done with location and source", d["status"] == "done" and d["location"] == "world 1,64,2" and d["done_by"] == "plugin")
post("/api/action", {"action": "next"}, cookie=cookie)
check("next moves to the animal", svc.state()["active"]["txn"] == "t6")
post("/api/action", {"action": "skip", "id": svc.state()["active"]["id"]}, cookie=cookie)
check("skip clears active", svc.state()["active"] is None)
post("/api/action", {"action": "next"}, cookie=cookie)
post("/api/action", {"action": "done", "id": svc.state()["active"]["id"]}, cookie=cookie)
check("Done button finishes", svc.rows("txn='t6'")[0]["status"] == "done")
check("threshold not due with 0 open", svc.state()["stream_due"] is False)
for i in range(5):
    kofi("q%d" % i, "treecode", "cherry")
check("five open requests make a stream due", svc.state()["stream_due"] is True)

print("\n%d failed" % len(fails))
raise SystemExit(1 if fails else 0)
