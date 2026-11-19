const mineflayer = require("mineflayer");
const bot = mineflayer.createBot({ host: "127.0.0.1", port: 25570, username: "TestBot", auth: "offline", version: "1.21.10" });
const seen = [];
bot._client.on("packet", (data, meta) => { if (/title|clear_titles/.test(meta.name)) seen.push(meta.name + " " + JSON.stringify(data).slice(0, 160)); });
bot.on("message", (m) => console.log("MSG " + m.toString()));
bot.once("spawn", async () => {
  await new Promise(r => setTimeout(r, 1500));
  for (let i = 0; i < 11; i++) { bot.chat("/b4bquest skip"); await new Promise(r => setTimeout(r, 700)); }
  await new Promise(r => setTimeout(r, 3000));
  console.log("TITLE PACKETS: " + seen.length + "\n" + seen.join("\n"));
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
setTimeout(() => process.exit(3), 40000);
