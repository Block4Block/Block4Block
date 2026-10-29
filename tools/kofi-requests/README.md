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
