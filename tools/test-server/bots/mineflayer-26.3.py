"""Teaches a local mineflayer install to speak Minecraft 26.3, for the test-copy bots only (no ViaVersion: SQ63, SQ65).

PrismarineJS's minecraft-data branch `pc_26_3` has 26.3's protocol, but its blocks, items and entities still point at
26.1's lists. 26.3 adds about 90 blocks, so every block state id after the first new one moves, and a bot reading the
world with old lists would see the wrong blocks. This script builds 26.3's lists from the game's own reports, keeping
everything else from 26.2:
  1. downloads protocol.json and version.json for 26.3 from minecraft-data at the pinned commit;
  2. writes blocks, items, entities and blockCollisionShapes for 26.3: ids and block states from the reports; every
     other field from 26.2's entry of the same name, or for a new block from a like block (poplar from pale oak; wool
     and concrete slabs and stairs from the wool or concrete block with the oak slab's or stairs' shape; the rest
     listed in LIKE), with drops renumbered to 26.3's item ids;
  3. registers 26.3 (dataPaths.json, data.js, versions.json, protocolVersions.json), adds '26.3' to the version lists
     of minecraft-protocol and mineflayer, maps 26.3 to the current chunk format, and adds it to every feature list
     that ends at '26.2'.
First make the reports, from a 26.3 server folder (Paper's jar holds the game's data generator):
  java -cp "versions/26.3/paper-26.3.jar;<every jar under libraries>" net.minecraft.data.Main --reports --output OUT
Then, on a folder already patched by mineflayer-26.2.py:
  py -3 mineflayer-26.3.py <folder with node_modules> OUT/reports"""
import json, os, re, sys, urllib.request

COMMIT = "e4cbec4b17"   # PrismarineJS/minecraft-data branch pc_26_3, 15 September 2026
COLORS = "white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split()
LIKE = {"red_shrub": "dead_bush", "shelf_mushroom": "brown_mushroom", "straw_bed": "white_bed",
        "red_poplar_leaves": "pale_oak_leaves", "orange_poplar_leaves": "pale_oak_leaves",
        "yellow_poplar_leaves": "pale_oak_leaves", "cushion": "item_frame"}
for c in COLORS:
    LIKE[f"{c}_cushion"] = "white_carpet"

nm = os.path.join(sys.argv[1], "node_modules")
reports = sys.argv[2]
md = os.path.join(nm, "minecraft-data")
data = os.path.join(md, "minecraft-data", "data")
old_dir, new_dir = os.path.join(data, "pc", "26.2"), os.path.join(data, "pc", "26.3")
raw = f"https://raw.githubusercontent.com/PrismarineJS/minecraft-data/{COMMIT}/data/"

def fetch(rel):
    return urllib.request.urlopen(urllib.request.Request(raw + rel, headers={"User-Agent": "b4b-test-bots"})).read()
def load(d, f):
    return json.load(open(os.path.join(d, f + ".json"), encoding="utf8"))
def save(f, obj):
    json.dump(obj, open(os.path.join(new_dir, f + ".json"), "w", encoding="utf8"), indent=2)

def like(name, have):
    """The 26.2 name whose entry a new 26.3 name copies."""
    if name in LIKE: return LIKE[name]
    if name.endswith("_map") and "map" in have: return "map"           # the new explorer maps
    if "poplar" in name and name.replace("poplar", "pale_oak") in have: return name.replace("poplar", "pale_oak")
    m = re.fullmatch(r"(\w+?)_(wool|concrete)_(slab|stairs)", name)
    if m: return f"{m.group(1)}_{m.group(2)}"
    raise SystemExit(f"no like block for {name}: add it to LIKE")

os.makedirs(new_dir, exist_ok=True)
for f in ("protocol", "version"):
    open(os.path.join(new_dir, f + ".json"), "wb").write(fetch(f"pc/26.3/{f}.json"))

# The branch's protocol is unfinished: it doesn't number four packets 26.3 added, so every packet after them in a
# state is read as the wrong one, and login success carries 16 more bytes than it reads. Number them as the game's
# packet report does; packets whose layout isn't known here are read whole as raw bytes, which the bots ignore.
NEW_PACKETS = {"configuration": [("add_resource_pack", "post_effects")],
               "play": [("explosion", "add_transient_block"), ("add_resource_pack", "post_effects"), ("store_cookie", "swing_animation")]}
reg_pre = json.load(open(os.path.join(reports, "registries.json"), encoding="utf8"))
proto_path = os.path.join(new_dir, "protocol.json")
proto = json.load(open(proto_path, encoding="utf8"))
rpackets = json.load(open(os.path.join(reports, "packets.json"), encoding="utf8"))
for state, inserts in NEW_PACKETS.items():
    types = proto[state]["toClient"]["types"]
    container = types["packet"][1]
    mapper = container[0]["type"][1]["mappings"]
    names = [mapper[k] for k in sorted(mapper, key=lambda k: int(k, 16))]
    for after, new in inserts:
        if new not in names:
            names.insert(names.index(after) + 1, new)
            types["packet_" + new] = ["container", [{"name": "data", "type": "restBuffer"}]]
            container[1]["type"][1]["fields"][new] = "packet_" + new
    want = len(rpackets[state]["clientbound"])
    if len(names) != want:
        raise SystemExit(f"{state}: {len(names)} packets after the fix, the game's report has {want}")
    container[0]["type"][1]["mappings"] = {f"0x{i:02x}": n for i, n in enumerate(names)}
# Serverbound play, as the 26.3 server jar's codecs read them: the teleport confirmation now also carries the position
# and rotation the client took (ServerboundAcceptTeleportationPacket: varint, 3 doubles, 2 floats); swinging an arm is
# the new `punch`, an empty packet at 0x2e (ServerboundPunchPacket), and the old swing packet is gone; the spectator
# action keeps 26.2's optional varint (the branch's `spectate_entity` is wrong). Everything else keeps 26.2's numbers.
old_proto = json.load(open(os.path.join(old_dir, "protocol.json"), encoding="utf8"))
sb, sb_old = proto["play"]["toServer"]["types"], old_proto["play"]["toServer"]["types"]
smap = sb_old["packet"][1][0]["type"][1]["mappings"]
snames = [smap[k] for k in sorted(smap, key=lambda k: int(k, 16))]
if "arm_animation" in snames:
    snames.remove("arm_animation"); snames.insert(0x2e, "arm_animation")
want = len(rpackets["play"]["serverbound"])
if len(snames) != want:
    raise SystemExit(f"play serverbound: {len(snames)} packets after the fix, the game's report has {want}")
sb["packet"] = json.loads(json.dumps(sb_old["packet"]))
sb["packet"][1][0]["type"][1]["mappings"] = {f"0x{i:02x}": n for i, n in enumerate(snames)}
sb["packet_arm_animation"] = ["container", []]
sb["packet_spectator_action"] = sb_old["packet_spectator_action"]
sb.pop("packet_spectate_entity", None)
sb["packet_teleport_confirm"] = ["container", [{"name": "teleportId", "type": "varint"}, {"name": "x", "type": "f64"},
                                               {"name": "y", "type": "f64"}, {"name": "z", "type": "f64"},
                                               {"name": "yaw", "type": "f32"}, {"name": "pitch", "type": "f32"}]]
# 26.3 sends the four light masks of the chunk and light packets as BitSet bytes (ByteBufCodecs.BIT_SET: a varint
# length, then the bytes, lowest bit first), no longer as an array of longs. Read them as bytes here; prismarine-chunk
# turns them back into longs below.
MASKS = ("skyLightMask", "blockLightMask", "emptySkyLightMask", "emptyBlockLightMask")
for pk in ("packet_map_chunk", "packet_update_light"):
    for f in proto["play"]["toClient"]["types"][pk][1]:
        if f.get("name") in MASKS:
            f["type"] = ["buffer", {"countType": "varint"}]
# Entity position sync (ClientboundEntityPositionSyncPacket): 26.3 sends a PositionPath, not position and velocity:
# a varint type, then for LINEAR (0) one Vec3, for STEPPED (1) a list of {Vec3, varint tick offset}; then yaw, pitch
# and on-ground as before. mineflayer's handler is taught to read it below.
vec3 = [{"name": "x", "type": "f64"}, {"name": "y", "type": "f64"}, {"name": "z", "type": "f64"}]
proto["play"]["toClient"]["types"]["packet_sync_entity_position"] = ["container", [
    {"name": "entityId", "type": "varint"}, {"name": "pathType", "type": "varint"},
    {"name": "path", "type": ["switch", {"compareTo": "pathType", "fields": {
        "0": ["container", vec3],
        "1": ["container", [{"name": "steps", "type": ["array", {"countType": "varint", "type":
              ["container", vec3 + [{"name": "tickOffset", "type": "varint"}]]}]}]]}}]},
    {"name": "yaw", "type": "f32"}, {"name": "pitch", "type": "f32"}, {"name": "onGround", "type": "bool"}]]
# Item components: the branch numbers them as 26.2 did (110 types), 26.3 has 122, so every item component after the
# 40th was read as the wrong one. Number them from the game's registry. Layouts, from the jar's DataComponents: the
# attack and interact animations are SwingAnimation, 26.2's swing_animation layout; waxed carries nothing; the cushion
# color is one dye color. The rest of the new ones (block transformer, villager food, fuels, compostable, mob
# visibility, pottery pattern, sign texts) aren't laid out here; they only travel on items changed to carry them.
ctypes = proto["types"]
comp_reg = {v["protocol_id"]: k.split(":", 1)[1] for k, v in reg_pre["minecraft:data_component_type"]["entries"].items()}
ctypes["SlotComponentType"][1]["mappings"] = {str(i): n for i, n in sorted(comp_reg.items())}
cfields = ctypes["SlotComponent"][1][1]["type"][1]["fields"]
if "swing_animation" in cfields:
    cfields["attack_animation"] = cfields["interact_animation"] = cfields["swing_animation"]
cfields["waxed"] = "void"
cfields["cushion/color"] = "varint"
# A recipe's tag slot (SlotDisplay$TagSlotDisplay) now holds a set of items (ByteBufCodecs.holderSet, the IDSet
# layout), no longer a tag name; read as a name, it threw every recipe after the first out of step.
# A pot's decorations (PotDecorations) are now four optional item stacks (back, left, right, front), no longer a list
# of item ids.
cfields["pot_decorations"] = ["container", [{"name": side, "type": ["option", "ItemStackTemplate"]}
                                             for side in ("back", "left", "right", "front")]]
sd = (ctypes.get("SlotDisplay") or proto["play"]["toClient"]["types"]["SlotDisplay"])[1][1]["type"][1]["fields"]
if "tag" in sd: sd["tag"] = "IDSet"
if "sulfur_cube_content" not in cfields:
    old_types = old_proto["types"]["SlotComponent"][1][1]["type"][1]["fields"]
    if "sulfur_cube_content" in old_types: cfields["sulfur_cube_content"] = old_types["sulfur_cube_content"]
# Advancements (ClientboundUpdateAdvancementsPacket): each one's x and y moved out of its display (DisplayInfo) to just
# after it (PositionedAdvancement: the advancement, then two floats); the packet ends with a show-advancements flag.
adv = proto["play"]["toClient"]["types"]["packet_advancements"][1]
entry = next(f for f in adv if f["name"] == "advancementMapping")["type"][1]["type"][1]
display = next(f for f in next(f for f in entry if f["name"] == "value")["type"][1] if f["name"] == "displayData")["type"][1][1]
xy = [f for f in display if f["name"] in ("xCord", "yCord")]
if xy:
    display[:] = [f for f in display if f["name"] not in ("xCord", "yCord")]
    entry.extend(xy)
if not any(f["name"] == "showAdvancements" for f in adv):
    adv.append({"name": "showAdvancements", "type": "bool"})
# Packets 26.3 changed in ways not worked out here, read as raw bytes (none so far: the advancements and recipe book
# failed only through the item components above).
RAW_PACKETS = ()
for pk in RAW_PACKETS:
    proto["play"]["toClient"]["types"][pk] = ["container", [{"name": "data", "type": "restBuffer"}]]
success = proto["login"]["toClient"]["types"]["packet_success"][1]
if not any(f.get("name") == "rest" for f in success):
    success.append({"name": "rest", "type": "restBuffer"})
json.dump(proto, open(proto_path, "w", encoding="utf8"), indent=2)

reg = json.load(open(os.path.join(reports, "registries.json"), encoding="utf8"))
rblocks = json.load(open(os.path.join(reports, "blocks.json"), encoding="utf8"))
def ids(kind):
    return {k.split(":", 1)[1]: v["protocol_id"] for k, v in reg["minecraft:" + kind]["entries"].items()}

# Items first: blocks' drops are item ids.
old_items = {x["name"]: x for x in load(old_dir, "items")}
old_item_name = {x["id"]: x["name"] for x in old_items.values()}
item_ids = ids("item"); items = []
for name, i in sorted(item_ids.items(), key=lambda kv: kv[1]):
    base = old_items.get(name) or old_items[like(name, old_items)]
    items.append({**base, "id": i, "name": name,
                  "displayName": base["displayName"] if name in old_items else name.replace("_", " ").title()})
save("items", items)

old_blocks = {x["name"]: x for x in load(old_dir, "blocks")}
shapes = load(old_dir, "blockCollisionShapes")
blocks, new_shapes = [], {}
for name, i in sorted(ids("block").items(), key=lambda kv: kv[1]):
    rep = rblocks["minecraft:" + name]
    sts = [s["id"] for s in rep["states"]]
    default = next(s["id"] for s in rep["states"] if s.get("default"))
    # The state ids count through the properties with the first one slowest. The report's property list isn't always
    # in that order (chests), so order them by how many states in a row share a value, and the values as they appear.
    ordered = sorted(rep["states"], key=lambda s: s["id"])
    def run(k):
        return next((n for n, s in enumerate(ordered) if s["properties"][k] != ordered[0]["properties"][k]), len(ordered))
    order = sorted(rep.get("properties", {}), key=run, reverse=True)
    props = []
    for k in order:
        v = list(dict.fromkeys(s["properties"][k] for s in ordered))
        props.append({"name": k, "type": "bool" if sorted(v) == ["false", "true"] else "int" if all(x.isdigit() for x in v) else "enum",
                      "num_values": len(v), **({"values": v} if sorted(v) != ["false", "true"] else {})})
    src = name if name in old_blocks else like(name, old_blocks)
    shape_src = src
    m = re.fullmatch(r"\w+_(wool|concrete)_(slab|stairs)", name)
    if m: shape_src = "oak_" + m.group(2)
    base = old_blocks[src]
    drops = [item_ids[old_item_name[d]] for d in base.get("drops", []) if old_item_name.get(d) in item_ids]
    if name not in old_blocks and name in item_ids: drops = [item_ids[name]]
    blocks.append({**base, "id": i, "name": name,
                   "displayName": base["displayName"] if name in old_blocks else name.replace("_", " ").title(),
                   "defaultState": default, "minStateId": min(sts), "maxStateId": max(sts), "states": props,
                   "drops": drops, **({"boundingBox": old_blocks[shape_src]["boundingBox"]} if shape_src in old_blocks else {})})
    s = shapes["blocks"].get(shape_src, 0)
    if isinstance(s, list) and len(s) != len(sts): s = s[0]
    new_shapes[name] = s
save("blocks", blocks)
save("blockCollisionShapes", {"blocks": new_shapes, "shapes": shapes["shapes"]})

old_ents = {x["name"]: x for x in load(old_dir, "entities")}
ents = []
for name, i in sorted(ids("entity_type").items(), key=lambda kv: kv[1]):
    base = old_ents.get(name) or old_ents[like(name, old_ents)]
    ents.append({**base, "id": i, "internalId": i, "name": name,
                 "displayName": base["displayName"] if name in old_ents else name.replace("_", " ").title()})
save("entities", ents)
sounds = [{"id": v["protocol_id"] + 1, "name": k.split(":", 1)[1]} for k, v in reg["minecraft:sound_event"]["entries"].items()]
save("sounds", sorted(sounds, key=lambda x: x["id"]))
print(f"26.3: {len(blocks)} blocks, {len(items)} items, {len(ents)} entities; highest state {max(b['maxStateId'] for b in blocks)}")

# Register. Lists not rebuilt above stay 26.2's.
paths = json.load(open(os.path.join(data, "dataPaths.json")))
paths["pc"]["26.3"] = {**paths["pc"]["26.2"], **{k: "pc/26.3" for k in ("blocks", "items", "entities", "blockCollisionShapes", "sounds", "protocol", "version")}}
json.dump(paths, open(os.path.join(data, "dataPaths.json"), "w"), indent=2)
for name in ("versions", "protocolVersions"):
    p = os.path.join(data, "pc", "common", name + ".json"); cur = json.load(open(p))
    keys = [x if isinstance(x, str) else x["minecraftVersion"] for x in cur]
    if "26.3" not in keys:
        new = [x for x in json.loads(fetch(f"pc/common/{name}.json")) if (x if isinstance(x, str) else x["minecraftVersion"]) == "26.3"]
        cur = (cur + new) if name == "versions" else (new + cur)
        json.dump(cur, open(p, "w"), indent=2)
js = open(os.path.join(md, "data.js"), encoding="utf8").read()
js = re.sub(r"    '26\.3': \{\n.*?\n    \},\n(?=    '26\.2': \{)", "", js, count=1, flags=re.S)   # rerunnable
if "    '26.3': {" not in js:
    body = re.search(r"    '26\.2': \{\n(.*?)\n    \}", js, re.S).group(1)
    for k in ("blocks", "items", "entities", "blockCollisionShapes", "sounds", "protocol", "version"):
        body = re.sub(rf'(get {k} \(\) \{{ return require\("\./minecraft-data/data/)pc/[^/]+(/{k}\.json"\) \}})', rf"\1pc/26.3\2", body)
    js = js.replace("    '26.2': {", "    '26.3': {\n" + body + "\n    },\n    '26.2': {", 1)
    open(os.path.join(md, "data.js"), "w", encoding="utf8").write(js)

# mineflayer confirms a teleport with the id only; 26.3 wants the position and rotation it took, too (extra fields are
# ignored by the older versions' packet layouts).
p = os.path.join(nm, "mineflayer", "lib", "plugins", "physics.js"); s = open(p, encoding="utf8").read()
old_line = "bot._client.write('teleport_confirm', { teleportId: packet.teleportId })"
if old_line in s:
    s = s.replace(old_line, "bot._client.write('teleport_confirm', { teleportId: packet.teleportId, x: pos.x, y: pos.y, z: pos.z, "
                            "yaw: newYaw, pitch: newPitch })")
    open(p, "w", encoding="utf8").write(s)

# 26.3 counts the client's ticks by its tick-end packet: a second position packet before one gets the client kicked
# ("Invalid move player packet": receivedPositionThisTick in ServerGamePacketListenerImpl), and mining time is measured
# in them, so a bot that never ends a tick can't finish breaking a block. mineflayer never sends one; end every physics
# tick, after its movement packet.
p = os.path.join(nm, "mineflayer", "lib", "plugins", "physics.js"); s = open(p, encoding="utf8").read()
END_TICK = "    if (bot.version === '26.3') bot._client.write('tick_end', {})  // 26.3: end the tick\n"
s = s.replace("    bot._client.write('position', lastSent)\n" + END_TICK, "    bot._client.write('position', lastSent)\n")
s = s.replace("    bot._client.write('look', lastSent)\n" + END_TICK, "    bot._client.write('look', lastSent)\n")
s = s.replace("    bot._client.write('position_look', lastSent)\n" + END_TICK, "    bot._client.write('position_look', lastSent)\n")
tick = "    if (shouldUsePhysics) {\n      updatePosition(now)\n    }\n  }\n"
if END_TICK not in s and tick in s:
    s = s.replace(tick, "    if (shouldUsePhysics) {\n      updatePosition(now)\n    }\n" + END_TICK + "  }\n", 1)
    # The answers to a teleport are sent outside the physics tick, so they end a tick of their own.
    for reply in ("        sendPacketPositionAndLook(delayedPos, delayedYaw, delayedPitch, delayedOnGround)\n",
                  "    sendPacketPositionAndLook(pos, newYaw, newPitch, bot.entity.onGround)\n"):
        indent = reply[:len(reply) - len(reply.lstrip())]
        s = s.replace(reply, reply + indent + END_TICK.lstrip(), 1)
open(p, "w", encoding="utf8").write(s)

# 26.3 added CHANGE_DESTROY_DIRECTION as the second player action (ServerboundPlayerActionPacket$Action), so every
# action after the first moved up one: abort is 2, finishing a dig 3, releasing a used item 6. mineflayer's "finish"
# (2) read as an abort, and no block that takes time to break was ever broken.
p = os.path.join(nm, "mineflayer", "lib", "plugins", "digging.js"); s = open(p, encoding="utf8").read()
for old, new in (("status: 2, // finish digging", "status: bot.version === '26.3' ? 3 : 2, // finish digging (26.3: 3)"),
                 ("status: 1, // cancel digging", "status: bot.version === '26.3' ? 2 : 1, // cancel digging (26.3: 2)")):
    s = s.replace(old, new)
open(p, "w", encoding="utf8").write(s)
p = os.path.join(nm, "mineflayer", "lib", "plugins", "inventory.js"); s = open(p, encoding="utf8").read()
s = s.replace("    const body = {\n      status: 5,\n", "    const body = {\n      status: bot.version === '26.3' ? 6 : 5,  // release use item (26.3: 6)\n", 1)
open(p, "w", encoding="utf8").write(s)

# mineflayer's position-sync handler reads x, y, z and a velocity; 26.3's packet holds a path (see above).
p = os.path.join(nm, "mineflayer", "lib", "plugins", "entities.js"); s = open(p, encoding="utf8").read()
old_sync = "    entity.position.set(packet.x, packet.y, packet.z)\n    entity.velocity.set(packet.dx, packet.dy, packet.dz)\n"
if old_sync in s:
    s = s.replace(old_sync, "    const at = packet.path ? (packet.path.steps ? packet.path.steps[packet.path.steps.length - 1] : packet.path) : packet  // 26.3: a path\n"
                            "    if (at) entity.position.set(at.x, at.y, at.z)\n"
                            "    if (packet.dx !== undefined) entity.velocity.set(packet.dx, packet.dy, packet.dz)\n", 1)
    open(p, "w", encoding="utf8").write(s)

for rel in (("minecraft-protocol", "src", "version.js"), ("mineflayer", "lib", "version.js")):
    p = os.path.join(nm, *rel); s = open(p, encoding="utf8").read()
    if "'26.3'" not in s:
        s = s.replace("'26.1', '26.2']", "'26.1', '26.2', '26.3']"); open(p, "w", encoding="utf8").write(s)
# prismarine-chunk reads light masks as longs ([high, low] pairs); turn 26.3's BitSet bytes into that shape first.
p = os.path.join(nm, "prismarine-chunk", "src", "pc", "1.18", "ChunkColumn.js"); s = open(p, encoding="utf8").read()
head = "    loadParsedLight (skyLight, blockLight, skyLightMask, blockLightMask, emptySkyLightMask, emptyBlockLightMask) {\n"
fix = ("      const toLongs = m => {   // 26.3: BitSet bytes, lowest bit first\n"
       "        if (!m || !(Buffer.isBuffer(m) || typeof m[0] === 'number')) return m\n"
       "        const b = Buffer.from(m); const out = []\n"
       "        for (let i = 0; i < b.length; i += 8) {\n"
       "          const w = Buffer.alloc(8); b.copy(w, 0, i, Math.min(i + 8, b.length))\n"
       "          out.push([w.readInt32LE(4), w.readInt32LE(0)])\n"
       "        }\n"
       "        return out\n"
       "      }\n"
       "      skyLightMask = toLongs(skyLightMask); blockLightMask = toLongs(blockLightMask)\n"
       "      emptySkyLightMask = toLongs(emptySkyLightMask); emptyBlockLightMask = toLongs(emptyBlockLightMask)\n")
if "toLongs" not in s:
    s = s.replace(head, head + fix, 1); open(p, "w", encoding="utf8").write(s)

p = os.path.join(nm, "prismarine-chunk", "src", "index.js"); s = open(p, encoding="utf8").read()
if "26.3:" not in s:
    s = s.replace("    26.2: require('./pc/1.18/chunk')\n", "    26.2: require('./pc/1.18/chunk'),\n    26.3: require('./pc/1.18/chunk')\n")
    open(p, "w", encoding="utf8").write(s)
for root, _, fs in os.walk(nm):
    if "features.json" in fs and os.path.join("minecraft-data", "minecraft-data") not in root:
        p = os.path.join(root, "features.json"); d = json.load(open(p, encoding="utf8"))
        if isinstance(d, list) and any(isinstance(f, dict) and (f.get("versions") or [None])[-1] == "26.2" for f in d):
            for f in d:
                if isinstance(f, dict) and (f.get("versions") or [None])[-1] == "26.2": f["versions"].append("26.3")
            json.dump(d, open(p, "w", encoding="utf8"), indent=2)

ver = os.popen(f'cd "{sys.argv[1]}" && node -e "const m=require(\'minecraft-data\')(\'26.3\');console.log(m.version.version, Object.keys(m.blocksByName).length, m.blocksByName.poplar_log.minStateId)"').read().strip()
print("26.3 ready:", ver)
