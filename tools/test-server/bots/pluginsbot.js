// Checks the main server's other plugins on a test server: RefillAnyWater (bottles, mud), TradeRevamp (trades),
// PandasSneezePlus (sneezes), and the status commands of AdaptiveRangeMobSpawner, B4BClaimAssetCounter, FarmControl
// and Chunky. B4B_CONSOLE = the server console input file, B4B_LOG = the server log (for console replies);
// B4B_PORT and B4B_VERSION are optional.
const mineflayer = require("mineflayer");
const fs = require("fs");
const run = c => fs.appendFileSync(process.env.B4B_CONSOLE, c + "\n");
const logSize = () => fs.statSync(process.env.B4B_LOG).size;
const logSince = n => { const b = fs.readFileSync(process.env.B4B_LOG); return b.slice(n).toString("utf8").replace(/\x1b\[[0-9;]*m/g, ""); };
const sleep = ms => new Promise(r => setTimeout(r, ms));
let pass = 0, total = 0;
const check = (name, ok, info = "") => { total++; if (ok) pass++; console.log((ok ? "PASS " : "FAIL ") + name + (info ? "  (" + String(info).slice(0, 140) + ")" : "")); };
const consoleReply = async (cmd, wait = 1500) => { const n = logSize(); run(cmd); await sleep(wait); return logSince(n); };

const bot = mineflayer.createBot({ host: "127.0.0.1", port: Number(process.env.B4B_PORT || 25570), username: "VetBot",
  auth: "offline", version: process.env.B4B_VERSION || "1.21.10" });
const sounds = [];
bot._client.on("sound_effect", p => sounds.push(JSON.stringify(p)));
bot._client.on("entity_sound_effect", p => sounds.push(JSON.stringify(p)));

bot.once("spawn", async () => {
  await sleep(2000);
  run("gamemode survival VetBot"); run("clear VetBot"); await sleep(800);
  const p = bot.entity.position.floored();
  const count = name => bot.inventory.items().filter(i => i.name === name).reduce((a, i) => a + i.count, 0);

  // RefillAnyWater: a glass bottle fills from water in reach.
  const w = p.offset(0, -1, 2);
  run(`setblock ${w.x} ${w.y} ${w.z} water`); run("give VetBot glass_bottle 2"); await sleep(1200);
  bot.setQuickBarSlot(0); await sleep(300);
  const bottle = bot.inventory.items().find(i => i.name === "glass_bottle");
  if (bottle) await bot.equip(bottle, "hand");
  await bot.lookAt(w.offset(0.5, 0.9, 0.5)); await sleep(300);
  bot.activateItem(); await sleep(300); bot.deactivateItem(); await sleep(1200);
  check("RefillAnyWater: a glass bottle fills from water in reach", count("potion") >= 1, "potions: " + count("potion"));

  // RefillAnyWater: a shovel in the main hand and a water bottle in the off hand turn grass into mud (live 1.4;
  // the GitHub source has a newer, unreleased version where the bottle alone does it).
  const g = p.offset(2, -1, 0);
  run(`setblock ${g.x} ${g.y} ${g.z} grass_block`); run(`setblock ${g.x} ${g.y + 1} ${g.z} air`); run("give VetBot iron_shovel"); await sleep(1200);
  const potion = bot.inventory.items().find(i => i.name === "potion");
  if (potion) await bot.equip(potion, "off-hand");
  const shovel = bot.inventory.items().find(i => i.name === "iron_shovel");
  if (shovel) await bot.equip(shovel, "hand");
  await bot.lookAt(g.offset(0.5, 1, 0.5)); await sleep(300);
  try { await bot.activateBlock(bot.blockAt(g)); } catch (e) {}
  await sleep(1200);
  check("RefillAnyWater: shovel plus water bottle turns grass into mud", bot.blockAt(g)?.name === "mud", bot.blockAt(g)?.name);

  // TradeRevamp: new villagers get its trade lists, including the potions it builds with PotionData.
  const v = p.offset(-3, 0, 0);
  run(`summon villager ${v.x} ${v.y} ${v.z} {NoAI:1b,Tags:["b4btest"],VillagerData:{profession:"minecraft:cleric",level:1,type:"minecraft:plains"}}`);
  await sleep(1500);
  const offers = await consoleReply(`data get entity @e[type=villager,tag=b4btest,limit=1] Offers`, 2000);
  check("TradeRevamp: a cleric sells its potions", /minecraft:potion/.test(offers) && /minecraft:emerald/.test(offers), offers.split("\n").find(l => /Offers|potion/.test(l)));
  run("kill @e[type=villager,tag=b4btest]");

  // PandasSneezePlus: baby pandas sneeze every 10 seconds.
  sounds.length = 0;
  run(`summon panda ${p.x - 3} ${p.y} ${p.z + 2} {Age:-24000,NoAI:1b,Tags:["b4btest"]}`);
  await sleep(14000);
  const sneezeId = require("minecraft-data")(bot.version).soundsByName["entity.panda.sneeze"].id;
  const sneezes = sounds.filter(s => { const p = JSON.parse(s); return p.sound && (p.sound.soundId === sneezeId + 1 || p.sound.soundId === sneezeId || p.soundId === sneezeId); });
  check("PandasSneezePlus: a baby panda sneezes", sneezes.length > 0, sneezes.length + " sneezes of " + sounds.length + " sounds; " + (sounds[0] || ""));
  run("kill @e[type=panda,tag=b4btest]");

  // Status commands answer without errors.
  for (const [name, cmd, re] of [
    ["AdaptiveRangeMobSpawner status", "adaptivespawner", /Current TPS: \d/],
    ["B4BClaimAssetCounter count", "countclaimassets", /claim|asset|count/i],
    ["FarmControl status", "farmcontrol status world", /world|cycle|status|group|entities/i],
    ["Chunky status", "chunky progress", /chunky|task|progress/i]]) {
    const out = await consoleReply(cmd, 2500);
    check(name + " answers", re.test(out) && !/Exception|An internal error|Unknown or incomplete command/.test(out), out.trim().split("\n").slice(-1)[0]);
  }
  console.log(pass + " of " + total + " checks passed.");
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
