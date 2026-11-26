// Lectern test: a Master Book copy whose pages are out of date gets rewritten when read, and the lectern opens with the
// new pages (Block4Block's BookReadLectern).
// Needs masterbooks.yml to hold book 9999 with the page "New page text from the master book" when the server starts.
// B4B_CONSOLE = the server console input file; B4B_PORT and B4B_VERSION are optional.
const mineflayer = require("mineflayer");
const { Vec3 } = require("vec3");
const fs = require("fs");
const run = c => fs.appendFileSync(process.env.B4B_CONSOLE, c + "\n");
const sleep = ms => new Promise(r => setTimeout(r, ms));
let pass = 0, total = 0;
const check = (name, ok, info = "") => { total++; if (ok) pass++; console.log((ok ? "PASS " : "FAIL ") + name + (info ? "  (" + info + ")" : "")); };

const bot = mineflayer.createBot({ host: "127.0.0.1", port: Number(process.env.B4B_PORT || 25570), username: "VetBot",
  auth: "offline", version: process.env.B4B_VERSION || "1.21.10" });
let opened = null;
bot.on("windowOpen", w => { opened = w; });

bot.once("spawn", async () => {
  await sleep(2000);
  run("gamemode creative VetBot"); await sleep(500);
  const p = bot.entity.position.floored();
  const at = p.offset(2, 0, 0);
  const book = '{id:"minecraft:written_book",count:1,components:{"minecraft:written_book_content":{title:"Test",author:"VetBot",pages:["Old page text"]},"minecraft:lore":[{text:"Master Book ",color:"gold",extra:[{text:"#9999",color:"gray"}]}]}}';
  run(`setblock ${at.x} ${at.y} ${at.z} lectern[has_book=true]{Book:${book}}`); await sleep(1500);
  const block = bot.blockAt(at);
  check("the lectern with the old copy is placed", block && block.name === "lectern", block && block.name);
  await bot.lookAt(at.offset(0.5, 0.5, 0.5)); await sleep(300);
  await bot.activateBlock(block); await sleep(2500);
  check("the lectern opens for the player", !!opened, opened && opened.type);
  const shown = opened ? JSON.stringify(opened.slots[0] || null) : "";
  check("it shows the master book's new page", shown.includes("New page text"), shown.slice(0, 160));
  check("the old page is gone", !shown.includes("Old page text"));
  run(`data get block ${at.x} ${at.y} ${at.z} Book`); await sleep(1500);
  console.log(pass + " of " + total + " checks passed.");
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
