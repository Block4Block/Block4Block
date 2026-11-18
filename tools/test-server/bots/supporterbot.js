// Supporter cosmetics test: HasJamOn (op) claims a chunk, makes a claim map and chats; VetBot (not op) listens.
// Checks the chat prefix and the claim-map colour before a grant, after it, and after removal.
// Usage: node supporterbot.js [x z]   (an unclaimed spot; default 6000 6000)
const mineflayer = require("mineflayer");
const Vec3 = require("vec3");
const sleep = ms => new Promise(r => setTimeout(r, ms));
const X = Number(process.argv[2] || 6000), Z = Number(process.argv[3] || 6000), Y = 150;
const opts = name => ({ host: "127.0.0.1", port: 25570, username: name, auth: "offline", version: "1.21.10" });

const results = [];
const check = (name, ok, detail) => { results.push([name, ok]); console.log((ok ? "PASS " : "FAIL ") + name + (detail ? "  (" + detail + ")" : "")); };

const owner = mineflayer.createBot(opts("HasJamOn"));
const vet = mineflayer.createBot(opts("VetBot"));
const vetChat = [], ownerMsgs = [];
vet.on("message", m => vetChat.push(m.toString()));
// What a real client shows for a player message with a changed format is its unsigned content, which
// mineflayer's "message" text leaves out: keep the raw packets too.
const vetRaw = [];
vet._client.on("player_chat", p => vetRaw.push(JSON.stringify(p.unsignedChatContent ?? null) + " " + JSON.stringify(p.plainMessage ?? "")));
owner.on("message", m => { ownerMsgs.push(m.toString()); console.log("  [HasJamOn sees] " + m.toString()); });

// The claim map's pixels, merged from map packets.
let mapId = null; const pixels = new Map();   // id -> Uint8Array(128*128)
owner._client.on("map", p => {
  if (!p.data || !p.columns) return;
  const id = p.itemDamage; if (!pixels.has(id)) pixels.set(id, new Uint8Array(128 * 128));
  const buf = pixels.get(id);
  for (let r = 0; r < p.rows; r++) for (let c = 0; c < p.columns; c++) buf[(p.y + r) * 128 + p.x + c] = p.data[r * p.columns + c];
});
const snap = () => Uint8Array.from(pixels.get(mapId) || []);
function diff(a, b) { const out = []; for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) out.push([i, a[i], b[i]]); return out; }
const said = (marker) => [...vetChat, ...vetRaw].filter(l => l.includes(marker));

Promise.all([new Promise(r => owner.once("spawn", r)), new Promise(r => vet.once("spawn", r))]).then(async () => {
  await sleep(2500);
  const cmd = async (c, wait = 700) => { owner.chat(c); await sleep(wait); };
  await cmd("/b4bsupporter remove HasJamOn all");
  await cmd("/gamemode creative"); await cmd("/clear HasJamOn");
  await cmd(`/tp HasJamOn ${X + 0.5} ${Y} ${Z + 0.5}`, 2000);
  await cmd(`/fill ${X - 2} ${Y - 1} ${Z - 2} ${X + 2} ${Y - 1} ${Z + 2} stone`);
  await cmd(`/fill ${X - 2} ${Y} ${Z - 2} ${X + 2} ${Y + 2} ${Z + 2} air`);
  await cmd(`/setblock ${X + 2} ${Y} ${Z} lectern`);
  await cmd(`/tp HasJamOn ${X + 0.5} ${Y} ${Z + 0.5}`, 1500);
  await cmd('/give HasJamOn written_book[written_book_content={title:"Claim",author:"HasJamOn",pages:["claim\\nHasJamOn"]}]', 1200);
  const lectern = owner.blockAt(new Vec3(X + 2, Y, Z));
  console.log("lectern block: " + (lectern && lectern.name));
  await owner.equip(owner.inventory.items().find(i => i.name === "written_book"), "hand");
  await owner.lookAt(lectern.position.offset(0.5, 0.9, 0.5));
  await owner.activateBlock(lectern); await sleep(1500);

  await cmd("/give HasJamOn map 1", 1200);
  await owner.equip(owner.inventory.items().find(i => i.name === "map"), "hand");
  owner.activateItem(); await sleep(1500);
  const filled = owner.inventory.items().find(i => i.name === "filled_map");
  check("an empty map became a filled map", Boolean(filled));
  await owner.equip(filled, "hand");
  await owner.lookAt(lectern.position.offset(0.5, 0.9, 0.5));
  await owner.activateBlock(lectern); await sleep(3000);
  const held = owner.heldItem;
  const lore = JSON.stringify(held && held.components || held && held.nbt || "");
  check("the map became a claim map", lore.includes("Claim Map"), held && held.name);
  // The claim map is a new map id: the newest one the server sent.
  mapId = [...pixels.keys()].pop();
  await sleep(2000);
  const before = snap();

  // Permission: VetBot is not op.
  vet.chat("/b4bsupporter list"); await sleep(1000);
  check("a non-op can't use /b4bsupporter", vetChat.some(l => /permission|unknown|I'm sorry/i.test(l)), vetChat.slice(-1)[0]);

  owner.chat("hello-before"); await sleep(1000);
  check("no prefix before a grant", said("hello-before").length > 0 && !said("hello-before").some(l => l.includes("✦")), said("hello-before")[0]);

  await cmd("/b4bsupporter grant HasJamOn prefix");
  owner.chat("hello-plain"); await sleep(1000);
  const plain = vetRaw.filter(l => l.includes("hello-plain")).join(" ");
  check("prefix shows to other players after a grant", plain.includes("✦"), plain.slice(0, 160));
  check("without a claim colour the tag is gold", /"gold"|§6/.test(plain));
  await cmd("/b4bsupporter grant HasJamOn claimcolor pink", 4000);
  owner.chat("hello-after"); await sleep(1000);
  // Pink as the map shows it (palette 242,127,165): the tag must be that exact colour.
  const colored = vetRaw.filter(l => l.includes("hello-after")).join(" ");
  // The tag stays gold; the name comes after it in the claim colour (#F27FA5, pink as the map shows it).
  const at = s => colored.toLowerCase().indexOf(s.toLowerCase());
  check("with a claim colour the tag stays gold", at('"gold"') >= 0 && at('"gold"') < at("✦"), colored.slice(0, 200));
  check("with a claim colour the name takes it (#F27FA5)", at("✦") < at("#F27FA5") && at("#F27FA5") < at("HasJamOn"));
  await cmd("/b4bsupporter grant HasJamOn claimcolor gold");
  check("gold is no longer a claim colour", ownerMsgs.some(l => l.startsWith("Pick a colour") && !l.includes("gold") && !l.includes("orange")));
  const granted = snap();
  const changed = diff(before, granted);
  const newColors = [...new Set(changed.map(c => c[2]))], oldColors = [...new Set(changed.map(c => c[1]))];
  check("the claim's outline changed colour on the map", changed.length > 0 && newColors.length === 1,
    changed.length + " pixels, " + oldColors.join("/") + " -> " + newColors.join("/"));
  console.log("  changed pixels (map x,z): " + changed.slice(0, 6).map(c => (c[0] % 128) + "," + Math.floor(c[0] / 128)).join(" ") + " ...");

  await cmd("/b4bsupporter grant HasJamOn claimcolor rainbow");
  check("an unknown colour is refused", ownerMsgs.some(l => l.startsWith("Pick a colour")));
  await cmd("/b4bsupporter info HasJamOn");
  check("info lists both perks", ownerMsgs.some(l => l.startsWith("HasJamOn: prefix, permanent; claim-color pink, permanent")), ownerMsgs.slice(-1)[0]);

  await cmd("/b4bsupporter remove HasJamOn claimcolor", 4000);
  const removed = snap();
  check("removing the colour restores the usual outline", diff(before, removed).length === 0, diff(before, removed).length + " pixels differ");
  await cmd("/b4bsupporter remove HasJamOn prefix");
  owner.chat("hello-removed"); await sleep(1000);
  check("no prefix after removal", said("hello-removed").length > 0 && !said("hello-removed").some(l => l.includes("✦")), said("hello-removed")[0]);

  // Leave a timed grant for the restart and expiry checks.
  await cmd("/b4bsupporter grant HasJamOn prefix 1");
  await cmd("/b4bsupporter grant HasJamOn claimcolor purple 1");
  await cmd("/b4bsupporter list");

  const failed = results.filter(r => !r[1]).length;
  console.log(`\n${results.length - failed} of ${results.length} checks passed.`);
  owner.quit(); vet.quit(); setTimeout(() => process.exit(failed ? 1 : 0), 500);
});
setTimeout(() => { console.log("timed out"); process.exit(3); }, 90000);
