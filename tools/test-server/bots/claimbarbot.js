// Claim bar for a new player whose claim questline isn't done: on claimed land the claim bar shows on top with the quest
// bar under it; on unclaimed land only the quest bar shows. Needs a claim by another player at the spawn point (run
// breakbot.js first) and a player name that has never joined (B4B_NAME, default NewBot plus a number).
// B4B_CONSOLE = the server console input file, B4B_SERVER = the server folder (to find VetBot's claim in
// claimdata.yml); B4B_PORT and B4B_VERSION are optional.
const mineflayer = require("mineflayer");
const fs = require("fs");
const path = require("path");
const run = c => fs.appendFileSync(process.env.B4B_CONSOLE, c + "\n");
const sleep = ms => new Promise(r => setTimeout(r, ms));
let pass = 0, total = 0;
const check = (name, ok, info = "") => { total++; if (ok) pass++; console.log((ok ? "PASS " : "FAIL ") + name + (info !== "" ? "  (" + info + ")" : "")); };
const name = process.env.B4B_NAME || ("NewBot" + Math.floor(Math.random() * 1e4));

const bot = mineflayer.createBot({ host: "127.0.0.1", port: Number(process.env.B4B_PORT || 25570), username: name,
  auth: "offline", version: process.env.B4B_VERSION || "1.21.10" });

// The bars on screen, top to bottom: the client stacks them in the order they were added.
const bars = new Map();
// Titles arrive as NBT text components: keep only their text values.
const text = t => {
  let out = "";
  const walk = v => {
    if (Array.isArray(v)) return v.forEach(x => typeof x === "string" ? (out += x) : walk(x));
    if (!v || typeof v !== "object") return;
    for (const [k, x] of Object.entries(v)) {
      if (k === "text") out += typeof x === "string" ? x : (x && typeof x.value === "string" ? x.value : "");
      else walk(x);
    }
  };
  walk(typeof t === "string" ? (() => { try { return JSON.parse(t); } catch (e) { return { text: t }; } })() : t);
  return out.replace(/§./g, "");
};
bot._client.on("boss_bar", p => { if (process.env.B4B_DEBUG) console.log("bar " + p.action + " " + String(p.entityUUID).slice(0, 8) + " " + JSON.stringify(p.title || "").slice(0, 300) + " pos " + (bot.entity ? bot.entity.position.x.toFixed(1) : ""));
  if (p.action === 0) bars.set(p.entityUUID, text(p.title));
  else if (p.action === 1) bars.delete(p.entityUUID);
  else if (p.action === 3 && bars.has(p.entityUUID)) bars.set(p.entityUUID, text(p.title));
});
const shown = () => [...bars.values()];
const isClaimBar = t => /Claimed at|Unclaimed/.test(t);

// Teleporting doesn't update the claim bar; walking across a chunk border does, as for players. The walks are in the
// air with slow falling so terrain never blocks them.
async function walkTo(x, z, toX) {
  run(`tp ${name} ${x} 160 ${z} 90 0`); run(`effect give ${name} slow_falling 10 0 true`); await sleep(2500);
  await bot.look(toX < x ? Math.PI / 2 : -Math.PI / 2, 0, true);
  bot.setControlState("forward", true); await sleep(1500);
  bot.setControlState("forward", false); await sleep(1500);
  if (process.env.B4B_DEBUG) console.log("at " + bot.entity.position.toString());
}

bot.once("spawn", async () => {
  await sleep(3000);
  run(`gamemode survival ${name}`); run(`effect give ${name} resistance 120 5 true`); await sleep(500);
  // A claim of VetBot's (breakbot.js) whose eastern neighbour is unclaimed. Claims are 48 blocks wide and chunk
  // borders every 16, so E (its east edge) and E + 16 are both chunk borders, the first also the claim border.
  const data = fs.readFileSync(path.join(process.env.B4B_SERVER, "plugins/Block4Block/claimdata.yml"), "utf8");
  const claims = [...data.matchAll(/NORMAL\|(-?\d+),(-?\d+):\s*location:\s*X: (-?[\d.]+)\s*Y: (-?[\d.]+)\s*Z: (-?[\d.]+)\s*members: ([^\r\n]*)/g)]
    .map(m => ({ cx: +m[1], cz: +m[2], z: +m[5], members: m[6] }));
  const taken = new Set(claims.map(c => c.cx + "," + c.cz));
  const c = claims.find(c => /VetBot/.test(c.members) && !taken.has((c.cx + 1) + "," + c.cz));
  if (!c) { console.log("FAIL no claim of VetBot's with an unclaimed east side; run breakbot.js first"); process.exit(1); }
  const E = (c.cx + 1) * 48, Z = c.cz * 48 + ((Math.floor(c.z) - c.cz * 48 + 5) % 48);
  await walkTo(E + 13, Z, E + 30);
  let s = shown();
  check("on unclaimed land: only the quest bar", s.length === 1 && !isClaimBar(s[0]), s.join(" / "));

  await walkTo(E + 2, Z, E - 10);
  s = shown();
  check("on claimed land: two bars", s.length === 2, s.join(" / "));
  check("on claimed land: the claim bar is on top", s.length > 0 && isClaimBar(s[0]), s[0]);
  check("on claimed land: the quest bar is underneath", s.length > 1 && !isClaimBar(s[1]), s[1]);

  await walkTo(E - 4, Z, E + 10);
  s = shown();
  check("walking out again: only the quest bar", s.length === 1 && !isClaimBar(s[0]), s.join(" / "));

  await walkTo(E + 2, Z, E - 10);
  s = shown();
  check("back on claimed land: the claim bar is on top again", s.length === 2 && isClaimBar(s[0]) && !isClaimBar(s[1]), s.join(" / "));

  console.log(pass + " of " + total + " checks passed.");
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
