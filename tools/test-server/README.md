# Test-server pass (docs/ship-checklist.md)

A Paper 1.21.10 server in offline mode on port 25570, with the same plugins as the live server (list them
with `b4b-plugin-install list` and compare). Ops: HasJamOn and TestBot. Bots use mineflayer
(`NODE_PATH` set to a folder with `node_modules/mineflayer`), see `bots/`.

Run each new feature in these states, not just a clean one:
1. No `plugins/Block4Block/config.yml` on disk (how the live server runs).
2. An old `config.yml` that lacks the new keys.
3. Reset state: move `world/playerdata` away, delete `world/stats`, `plugins/Block4Block/questprogress.yml`
   and `spawn-state.yml`.
4. Server killed mid-step (stop the process by id, not by a name pattern), then started again.
5. A fresh account and a returning one.

Start: `java -jar paper.jar nogui` in the server folder. Stop: `Stop-Process -Id <pid>`.
`bots/signbot.js 'Sign text'` writes a sign as HasJamOn; `bots/titlebot.js` skips a questline and prints title packets.
