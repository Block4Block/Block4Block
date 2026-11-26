// Block for Block: breaking stone costs one stone from the hotbar, outside a claim and inside the player's own claim,
// and is refused without one. B4B_CONSOLE = the server console input file, B4B_SERVER = the server folder (to read
// claimdata.yml); B4B_PORT and B4B_VERSION are optional.
const mineflayer = require("mineflayer");
const fs = require("fs");
const path = require("path");
const run = c => fs.appendFileSync(process.env.B4B_CONSOLE, c + "\n");
const sleep = ms => new Promise(r => setTimeout(r, ms));
let pass = 0, total = 0;
const check = (name, ok, info = "") => { total++; if (ok) pass++; console.log((ok ? "PASS " : "FAIL ") + name + (info !== "" ? "  (" + info + ")" : "")); };

const bot = mineflayer.createBot({ host: "127.0.0.1", port: Number(process.env.B4B_PORT || 25570), username: "VetBot",
  auth: "offline", version: process.env.B4B_VERSION || "1.21.10" });
const bar = [];
bot.on("actionBar", m => bar.push(m.toString()));
bot._client.on("system_chat", d => { if (d.isActionBar) bar.push(JSON.stringify(d.content).slice(0, 200)); });
bot.on("message", m => { const s = m.toString(); if (s.trim()) console.log("chat: " + s); });

const count = name => bot.inventory.items().filter(i => i.name === name).reduce((a, i) => a + i.count, 0);

async function breakStone(at) {
  run(`setblock ${at.x} ${at.y} ${at.z} stone`);
  for (let i = 0; i < 50 && bot.blockAt(at)?.name !== "stone"; i++) await sleep(100);
  const pick = bot.inventory.items().find(i => i.name === "iron_pickaxe");
  if (pick) await bot.equip(pick, "hand");
  bar.length = 0;
  try { await bot.dig(bot.blockAt(at), true); } catch (e) { console.log("dig: " + e.message); }
  await sleep(1500);
  return bot.blockAt(at)?.name;
}

bot.once("spawn", async () => {
  await sleep(2000);
  run("gamemode survival VetBot"); run("clear VetBot"); run("effect give VetBot haste 600 5 true"); await sleep(800);
  const p = bot.entity.position.floored();
  run(`fill ${p.x - 4} ${p.y - 1} ${p.z - 4} ${p.x + 4} ${p.y - 1} ${p.z + 4} stone_bricks`);
  run(`fill ${p.x - 4} ${p.y} ${p.z - 4} ${p.x + 4} ${p.y + 3} ${p.z + 4} air`);
  run("give VetBot iron_pickaxe"); await sleep(600);
  run("give VetBot stone 5"); await sleep(1200);

  // Outside a claim.
  let left = await breakStone(p.offset(2, 0, 0));
  check("outside a claim: the stone breaks", left === "air", left);
  check("outside a claim: it costs one stone", count("stone") === 4, "stone in inventory: " + count("stone"));

  run("clear VetBot stone"); await sleep(800);
  left = await breakStone(p.offset(2, 0, 1));
  check("outside a claim, no stone: the break is refused", left === "stone", left + "; " + bar.join(" | "));

  // Claim the area: a lectern with a claim book.
  const lec = p.offset(-2, 0, 0);
  run(`setblock ${lec.x} ${lec.y} ${lec.z} lectern`);
  run('give VetBot written_book[written_book_content={title:"Claim",author:"VetBot",pages:["claim\\nVetBot"]}]');
  run("give VetBot stone 5"); await sleep(1500);
  const book = bot.inventory.items().find(i => i.name === "written_book");
  if (book) await bot.equip(book, "hand");
  await bot.lookAt(lec.offset(0.5, 0.9, 0.5)); await sleep(300);
  try { await bot.activateBlock(bot.blockAt(lec)); } catch (e) { console.log("lectern: " + e.message); }
  await sleep(2000);
  const claims = fs.readFileSync(path.join(process.env.B4B_SERVER, "plugins/Block4Block/claimdata.yml"), "utf8");
  check("the lectern with the claim book makes a claim", /members: VetBot/.test(claims), claims.split("\n").filter(l => /VetBot|\|/.test(l)).join(" ").slice(0, 120));

  // Inside the bot's own claim: stone isn't Free in Claims, so it still costs one.
  left = await breakStone(p.offset(2, 0, 2));
  check("inside your own claim: the stone breaks", left === "air", left);
  check("inside your own claim: it costs one stone", count("stone") === 4, "stone in inventory: " + count("stone"));

  console.log(pass + " of " + total + " checks passed.");
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
