// Disguise test (B4B_CONSOLE = the server console input file; B4B_PORT, B4B_VERSION optional): HasJamOn joins (its skin gets saved), then VetBot puts on HasJamOn's head.
const mineflayer = require("mineflayer");
const fs = require("fs");
const CONSOLE = process.env.B4B_CONSOLE;
const run = c => fs.appendFileSync(CONSOLE, c + "\n");
const sleep = ms => new Promise(r => setTimeout(r, ms));
const opts = name => ({ host: "127.0.0.1", port: Number(process.env.B4B_PORT || 25570), username: name, auth: "offline", version: process.env.B4B_VERSION || "1.21.10" });
let pass = 0, total = 0;
const check = (name, ok, info = "") => { total++; if (ok) pass++; console.log((ok ? "PASS " : "FAIL ") + name + (info ? "  (" + info + ")" : "")); };

(async () => {
  const owner = mineflayer.createBot(opts("HasJamOn"));
  await new Promise(r => owner.once("spawn", r));
  await sleep(2000);
  const vet = mineflayer.createBot(opts("VetBot"));
  const vetChat = [];
  vet.on("messagestr", m => vetChat.push(m));
  await new Promise(r => vet.once("spawn", r));
  await sleep(2000);
  run("gamemode survival VetBot"); run("clear VetBot"); await sleep(800);
  run('give VetBot player_head[profile={name:"HasJamOn"}] 1'); await sleep(1500);
  const head = vet.inventory.items().find(i => i.name === "player_head");
  check("VetBot got HasJamOn's head", !!head);
  if (head) {
    try { await vet.equip(head, "head"); } catch (e) { console.log("equip: " + e.message); }
    await sleep(2000);
  }
  const msg = vetChat.find(l => /disguised as HasJamOn/i.test(l));
  check("putting on the head disguises VetBot as HasJamOn", !!msg, msg || vetChat.slice(-3).join(" | "));
  await sleep(6000);
  check("the disguise runs out after its 5 seconds", vetChat.some(l => /disguise has expired/i.test(l)), vetChat.slice(-2).join(" | "));
  console.log(pass + " of " + total + " checks passed.");
  owner.quit(); vet.quit(); setTimeout(() => process.exit(0), 500);
})();
