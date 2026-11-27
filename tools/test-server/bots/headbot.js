// Mob heads on the 26.3 copy: killed by a player (VetBot, through /damage ... by VetBot) they may drop a named,
// textured head; killed by anything else they drop none. Counts the heads and reads their names from the server.
const mineflayer = require("mineflayer"); const fs = require("fs");
const CON = process.env.B4B_CONSOLE, LOG = process.env.B4B_LOG;
const run = c => fs.appendFileSync(CON, c + "\n"); const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: "127.0.0.1", port: Number(process.env.B4B_PORT), username: "VetBot", auth: "offline", version: "26.3" });
async function ask(cmd, re) {
  const n = fs.statSync(LOG).size; run(cmd); await sleep(1500);
  const m = fs.readFileSync(LOG).slice(n).toString("utf8").match(re); return m;
}
async function trial(mob, nbt, byPlayer, times) {
  const X = 3000, Y = 120, Z = 3000;
  run(`minecraft:kill @e[type=item,x=${X},y=${Y},z=${Z},distance=..8]`); await sleep(500);
  for (let i = 0; i < times; i++) {
    run(`summon ${mob} ${X} ${Y} ${Z} ${nbt}`);
    run(byPlayer ? `damage @e[type=${mob},x=${X},y=${Y},z=${Z},distance=..3,limit=1] 1000 minecraft:player_attack by VetBot`
                 : `minecraft:kill @e[type=${mob},x=${X},y=${Y},z=${Z},distance=..3,limit=1]`);
  }
  await sleep(2500);
  const c = await ask(`execute positioned ${X} ${Y} ${Z} store result score #h b4btest if entity @e[type=item,distance=..6,nbt={Item:{id:"minecraft:player_head"}}]\nscoreboard players get #h b4btest`, /#h has (\d+)/);
  const name = await ask(`data get entity @e[type=item,x=${X},y=${Y},z=${Z},distance=..6,limit=1,nbt={Item:{id:"minecraft:player_head"}}] Item.components`, /has the following entity data: (.*)/);
  return { heads: c ? Number(c[1]) : null, sample: name ? name[1].replace(/\x1b\[[0-9;]*m/g, "").slice(0, 160) : "" };
}
bot.once("spawn", async () => {
  await sleep(3000);
  run("forceload add 2990 2990 3010 3010"); run("fill 2994 119 2994 3006 119 3006 stone"); run("scoreboard objectives add b4btest dummy");
  run("tp VetBot 3000 121 3010"); await sleep(2000);
  for (const [mob, nbt] of [["axolotl", "{Variant:0}"], ["sheep", "{Color:14}"], ["cow", "{}"], ["fox", "{Type:\"snow\"}"]]) {
    const a = await trial(mob, nbt, false, 20);
    const b = await trial(mob, nbt, true, 20);
    console.log(`${mob}: killed without a player, 20 times: ${a.heads} heads; by a player, 20 times: ${b.heads} heads  ${b.sample}`);
  }
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
