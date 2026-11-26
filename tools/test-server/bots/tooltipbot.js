// Server-side tooltip test: redstone dust (an item that places another block) and the recipe book previews.
// Usage: node tooltipbot.js   (HasJamOn, op, on the test server)
const mineflayer = require("mineflayer");
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: "127.0.0.1", port: Number(process.env.B4B_PORT || 25570), username: "HasJamOn", auth: "offline", version: process.env.B4B_VERSION || "1.21.10" });
// Setup commands go through the server console when B4B_CONSOLE names the file the server reads its input from
// (so the bot needs no op); otherwise through chat as an op.
const fs = require("fs");
const run = c => process.env.B4B_CONSOLE ? fs.appendFileSync(process.env.B4B_CONSOLE, c + "\n") : bot.chat("/" + c);
const results = [];
const check = (name, ok, detail) => { results.push(ok); console.log((ok ? "PASS " : "FAIL ") + name + (detail ? "  (" + detail + ")" : "")); };

const slotPackets = [], bookPackets = [];
bot._client.on("set_slot", p => slotPackets.push(JSON.stringify(p)));
bot._client.on("window_items", p => slotPackets.push(JSON.stringify(p)));
bot._client.on("set_player_inventory", p => slotPackets.push(JSON.stringify(p)));
bot._client.on("recipe_book_add", p => bookPackets.push(JSON.stringify(p)));
bot.on("error", e => console.log("bot error " + e.message));
bot.on("message", m => console.log("  [chat] " + m.toString()));

bot.once("spawn", async () => {
  await sleep(3000);
  // Take every recipe first: a player who already knows them gets no new recipe_book_add on "give".
  run("recipe take HasJamOn *"); await sleep(1500);
  bookPackets.length = 0;
  run("recipe give HasJamOn *"); await sleep(3000);
  // Unlocked recipes reach the recipe book in recipe_book_add packets. Vanilla shows block results such as a crafting table there.
  const book = bookPackets.join("\n");
  check("the recipe book arrived", bookPackets.length > 0, bookPackets.length + " packets, " + book.length + " chars");
  check("recipe book previews carry the Block for Block line", book.includes("Block for Block"));
  check("recipe book previews carry a Free line too", book.includes("Free in Claims") || book.includes("Free to Break"));

  run("gamemode survival HasJamOn"); await sleep(500);
  run("clear HasJamOn"); await sleep(800);
  slotPackets.length = 0;
  run("give HasJamOn redstone 5"); await sleep(1500);
  const redstone = slotPackets.filter(s => s.includes("redstone") || /"itemId":\s*\d+/.test(s)).join("\n");
  check("redstone dust shows a tooltip line (Free in Claims: redstone wire is free in claims)", redstone.includes("Free in Claims"), redstone.slice(0, 200));
  slotPackets.length = 0;
  run("give HasJamOn stick 1"); await sleep(1500);
  check("a stick (places no block) still gets no line", !slotPackets.join("").match(/Block for Block|Free in Claims|Free to Break/));

  // A click on the decorated stack must not be refused (the server would re-send the slot and log a hash mismatch).
  const item = bot.inventory.items().find(i => i.name === "redstone");
  if (item) {
    slotPackets.length = 0;
    await bot.clickWindow(item.slot, 0, 0); await sleep(500);
    await bot.clickWindow(9, 0, 0); await sleep(1000);
    const moved = bot.inventory.slots[9];
    check("redstone dust can be moved in the inventory", moved && moved.name === "redstone", moved && moved.name);
  } else check("redstone dust can be moved in the inventory", false, "no redstone in inventory");

  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed} of ${results.length} checks passed.`);
  bot.quit(); setTimeout(() => process.exit(failed ? 1 : 0), 500);
});
setTimeout(() => { console.log("timed out"); process.exit(3); }, 60000);
