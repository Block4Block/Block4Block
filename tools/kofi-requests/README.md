# Ko-fi request service

Receives Ko-fi payments, checks the requested text, keeps the queue and serves the owner's
second-monitor dashboard. The plugin (`kofi-requests:` in config.yml) only reports finished
signs and named animals; nothing appears in game.

Standard library only (Python 3.9+). Test with fake payments: `python test_service.py`.

Files next to `config.json` (all secret, never committed):
- `kofi_token.txt` Ko-fi verification token
- `dashboard_password.txt` dashboard login
- `plugin_secret.txt` shared with the plugin (`kofi_plugin_secret.txt` in the plugin folder)
- `bad_words.txt` one word per line

`config.json` maps Ko-fi shop item direct link codes to a type:
`{"item_types": {"abc123": "sign", "def456": "tree", "ghi789": "animal"}}`.
Run: `python3 kofi_requests.py config.json`. Listens on 127.0.0.1:8765; put a TLS reverse proxy in
front for /hooks/kofi and /dashboard.

## Discord, stream slots, markers, screenshots
- `discord_requests_webhook.txt` and `discord_announce_webhook.txt`: one channel webhook URL each (secret). A missing
  file switches that output off. #requests gets one status message the service keeps editing, plus a thank-you per
  finished request (the buyer's name only if their Ko-fi name is public). #announcements gets the stream time.
- `config.json` `stream_windows` (default Sat and Sun 20:00) and `tz_hours` (8): when 5 are queued or the oldest is
  within 2 days of 14, the dashboard offers the next two slots; one tap posts the announcement.
- `markers.jsonl`: one line per finished request for the highlight pipeline.
- OBS screenshot: the dashboard page talks to OBS (WebSocket v5, ws://127.0.0.1:port) from the owner's own browser, so
  the OBS password never reaches the server. Settings are stored in that browser. After a request finishes it waits
  1.5 s, crops to the centre 60%, shows a 10 s preview with Retake, then sends the picture to #requests. Cherry trees:
  Done starts a 3 s countdown first. OBS offline: the thank-you goes without a picture.
- Tests: `python test_service.py`, `python test_outputs.py` (fake Discord on this PC).
