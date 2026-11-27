// Block for Block: breaking stone costs one stone from the hotbar, outside a claim and inside the player's own claim,
// and is refused without one. On 26.1 and later also the Golden Dandelion (Free in Claims). Each run starts on fresh
// land far out. B4B_CONSOLE = the server console input file, B4B_SERVER = the server folder (to read claimdata.yml);
// B4B_LOG = the server log (for console replies); B4B_PORT and B4B_VERSION are optional.
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

// Golden Dandelion (26.1, SQ53: Free in Claims like the redstone objects): breaks with an empty hand, on dirt.
const golden = !/^1\./.test(process.env.B4B_VERSION || "1.21.10");
// Whether a dropped Golden Dandelion lies at that spot, asked through the console (needs B4B_LOG); it's then removed.
async function droppedAt(at) {
  const n = fs.statSync(process.env.B4B_LOG).size;
  run(`execute positioned ${at.x + 0.5} ${at.y} ${at.z + 0.5} if entity @e[type=item,distance=..2,nbt={Item:{id:"minecraft:golden_dandelion"}}]`);
  await sleep(1500);
  const reply = fs.readFileSync(process.env.B4B_LOG).slice(n).toString("utf8");
  run(`execute positioned ${at.x + 0.5} ${at.y} ${at.z + 0.5} run kill @e[type=item,distance=..2]`);
  return /Test passed/.test(reply);
}
async function breakDandelion(at) {
  run(`setblock ${at.x} ${at.y - 1} ${at.z} dirt`); run(`setblock ${at.x} ${at.y} ${at.z} golden_dandelion`);
  for (let i = 0; i < 50 && bot.blockAt(at)?.name !== "golden_dandelion"; i++) await sleep(100);
  await bot.unequip("hand").catch(() => {});
  bar.length = 0;
  try { await bot.dig(bot.blockAt(at), true); } catch (e) { console.log("dig: " + e.message); }
  await sleep(2000);
  // Ask the server: the bot's own view of a flower can lag after a refused break.
  const n = fs.statSync(process.env.B4B_LOG).size;
  run(`execute if block ${at.x} ${at.y} ${at.z} minecraft:golden_dandelion`);
  await sleep(1200);
  return /Test passed/.test(fs.readFileSync(process.env.B4B_LOG).slice(n).toString("utf8")) ? "golden_dandelion" : "air";
}

// The 26.2 blocks (SQ58, SQ59, SQ61, SQ64): sulfur, potent sulfur and the spike cost one and drop nothing; natural
// cinnabar is Free in Claims. Placed with commands, broken with the pickaxe; the block and drops asked from the server.
const v262 = !/^1\./.test(process.env.B4B_VERSION || "1.21.10") && !/^26\.1/.test(process.env.B4B_VERSION || "");
const v263 = v262 && !/^26\.2/.test(process.env.B4B_VERSION || "");
async function serverHas(test) {
  const n = fs.statSync(process.env.B4B_LOG).size;
  run(test); await sleep(1200);
  return /Test passed/.test(fs.readFileSync(process.env.B4B_LOG).slice(n).toString("utf8"));
}
// The 26.3 blocks (SQ68-SQ71 and the ones that follow an existing rule): what each needs under or beside it to stay.
const SUPPORT = { red_shrub: "grass_block", poplar_sapling: "grass_block" };
const STATE = { sulfur_spike: "[vertical_direction=up,thickness=tip]", shelf_mushroom: "[facing=north]",
  straw_bed: "[part=foot,facing=north]" };
async function breakNew(name, at) {
  const state = STATE[name] || "";
  run(`setblock ${at.x} ${at.y - 1} ${at.z} ${SUPPORT[name] || "stone_bricks"}`);
  if (name === "shelf_mushroom") run(`setblock ${at.x} ${at.y} ${at.z + 1} stone_bricks`);   // it hangs on the block behind it
  run(`setblock ${at.x} ${at.y} ${at.z} ${name}${state}`);
  if (name === "straw_bed") run(`setblock ${at.x} ${at.y} ${at.z - 1} straw_bed[part=head,facing=north]`);
  for (let i = 0; i < 50 && bot.blockAt(at)?.name !== name; i++) await sleep(100);
  const pick = bot.inventory.items().find(i => i.name === "iron_pickaxe");
  if (pick) await bot.equip(pick, "hand");
  bar.length = 0;
  try { await bot.dig(bot.blockAt(at), true); } catch (e) { console.log("dig: " + e.message); }
  await sleep(1800);
  const still = await serverHas(`execute if block ${at.x} ${at.y} ${at.z} minecraft:${name}`);
  const dropped = await serverHas(`execute positioned ${at.x + 0.5} ${at.y} ${at.z + 0.5} if entity @e[type=item,distance=..2,nbt={Item:{id:"minecraft:${name}"}}]`);
  run(`execute positioned ${at.x + 0.5} ${at.y} ${at.z + 0.5} run kill @e[type=item,distance=..2]`);
  return { left: still ? name : "air", dropped };
}

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
  // Fresh land, away from claims made by earlier runs: a spread can fail over the ocean, so try a few places and
  // make sure the claim cell (3x3 chunks) the bot lands in is unclaimed.
  const claimed = () => fs.readFileSync(path.join(process.env.B4B_SERVER, "plugins/Block4Block/claimdata.yml"), "utf8");
  const cellOf = v => "NORMAL|" + Math.floor(Math.floor(v.x) / 48) + "," + Math.floor(Math.floor(v.z) / 48) + ":";
  for (let tries = 0; tries < 8; tries++) {
    const X = 3000 + Math.floor(Math.random() * 40) * 480, Z = -3000 - Math.floor(Math.random() * 40) * 480;
    const before = bot.entity.position.clone();
    run(`execute in minecraft:overworld run spreadplayers ${X} ${Z} 0 40 false VetBot`);
    for (let i = 0; i < 60 && bot.entity.position.distanceTo(before) < 100; i++) await sleep(100);
    if (bot.entity.position.distanceTo(before) >= 100 && !claimed().includes(cellOf(bot.entity.position))) break;
  }
  await sleep(2000);
  const p = bot.entity.position.floored();
  console.log("starting at " + p + " (" + cellOf(p) + " unclaimed: " + !claimed().includes(cellOf(p)) + ")");
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

  if (golden) {
    run("give VetBot golden_dandelion 2"); await sleep(1200);
    let g = await breakDandelion(p.offset(-2, 0, 2));
    const dropOut = await droppedAt(p.offset(-2, 0, 2));
    check("Golden Dandelion outside a claim: costs one and drops nothing", g === "air" && count("golden_dandelion") === 1 && !dropOut, g + "; in inventory: " + count("golden_dandelion") + "; dropped: " + dropOut);
    run("clear VetBot golden_dandelion"); await sleep(800);
    g = await breakDandelion(p.offset(-2, 0, 3));
    check("Golden Dandelion outside a claim, none in the hotbar: refused", g === "golden_dandelion", g + "; " + bar.join(" | "));
  }

  if (v262) {
    const NEW = ["sulfur", "cinnabar", "sulfur_spike", "potent_sulfur"];
    for (const [i, name] of NEW.entries()) {
      run(`give VetBot ${name} 2`); await sleep(1200);
      const r = await breakNew(name, p.offset(-3, 0, -3 + i));
      check(`${name} outside a claim: costs one and drops nothing`, r.left === "air" && count(name) === 1 && !r.dropped, r.left + "; in inventory: " + count(name) + "; dropped: " + r.dropped);
      run(`clear VetBot ${name}`); await sleep(800);
    }
    for (const [i, name] of ["sulfur", "cinnabar"].entries()) {
      const r = await breakNew(name, p.offset(-4, 0, -3 + i));
      check(`${name} outside a claim, none in the hotbar: refused`, r.left === name, r.left + "; " + bar.join(" | "));
    }
  }

  if (v263) {
    // Exempt, like pale oak's logs and leaves, the other saplings and beds: free to break with none in the hotbar.
    for (const [i, name] of ["poplar_log", "red_poplar_leaves", "poplar_sapling", "straw_bed"].entries()) {
      run(`clear VetBot ${name}`); await sleep(600);
      const r = await breakNew(name, p.offset(-5, 0, -3 + 2 * i));
      check(`${name} outside a claim: free to break`, r.left === "air", r.left + "; " + bar.join(" | "));
    }
    // Cost one, drop nothing: poplar's crafted forms, the wool and concrete slabs and stairs, the red shrub (SQ69),
    // and the shelf mushroom outside claims (SQ70).
    for (const [i, name] of ["poplar_planks", "white_wool_stairs", "white_concrete_slab", "red_shrub", "shelf_mushroom"].entries()) {
      run(`give VetBot ${name} 2`); await sleep(1200);
      const r = await breakNew(name, p.offset(-6, 0, -3 + 2 * i));
      check(`${name} outside a claim: costs one and drops nothing`, r.left === "air" && count(name) === 1 && !r.dropped, r.left + "; in inventory: " + count(name) + "; dropped: " + r.dropped);
      run(`clear VetBot ${name}`); await sleep(800);
    }
    const r = await breakNew("red_shrub", p.offset(-7, 0, -3));
    check("red_shrub outside a claim, none in the hotbar: refused", r.left === "red_shrub", r.left + "; " + bar.join(" | "));
  }

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

  if (golden) {
    run("clear VetBot golden_dandelion"); await sleep(800);
    const g = await breakDandelion(p.offset(2, 0, 3));
    const dropIn = await droppedAt(p.offset(2, 0, 3));
    check("Golden Dandelion inside your own claim: free, and it drops itself", g === "air" && dropIn, g + "; dropped: " + dropIn + "; " + bar.join(" | "));
  }

  if (v262) {
    run("give VetBot cinnabar 2"); await sleep(1200);
    let r = await breakNew("cinnabar", p.offset(3, 0, -3));
    check("cinnabar inside your own claim: free, and it drops itself", r.left === "air" && count("cinnabar") === 2 && r.dropped, r.left + "; in inventory: " + count("cinnabar") + "; dropped: " + r.dropped);
    run("give VetBot sulfur 2"); await sleep(1200);
    r = await breakNew("sulfur", p.offset(3, 0, -2));
    check("sulfur inside your own claim: still costs one and drops nothing", r.left === "air" && count("sulfur") === 1 && !r.dropped, r.left + "; in inventory: " + count("sulfur") + "; dropped: " + r.dropped);
  }

  if (v263) {
    // Free in Claims: the concrete slabs and stairs, like concrete, and the shelf mushroom (SQ70).
    for (const [i, name] of ["white_concrete_slab", "shelf_mushroom"].entries()) {
      run(`give VetBot ${name} 2`); await sleep(1200);
      const r = await breakNew(name, p.offset(4, 0, -3 + 2 * i));
      check(`${name} inside your own claim: free, and it drops itself`, r.left === "air" && count(name) === 2 && r.dropped, r.left + "; in inventory: " + count(name) + "; dropped: " + r.dropped);
      run(`clear VetBot ${name}`); await sleep(800);
    }
  }

  console.log(pass + " of " + total + " checks passed.");
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
