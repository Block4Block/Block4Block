"""For every nbt condition in the datapack files used at format 121, summon that mob with that data and test that the
selector still matches it on the running copy. A mismatch means the mob-head condition can never be true."""
import json, os, re, sys, time
DP = sys.argv[2] if len(sys.argv) > 2 else r"C:Block4Block-website_b4b-server-queue-workdatapack-26.3"
SRV = sys.argv[1] if len(sys.argv) > 1 else r"C:\Block4Block-website\_b4b-server-queue-work\tooltip-test\server-26.3"
CON = os.path.join(SRV, "console-in.txt"); LOG = os.path.join(SRV, "run.log")
ORDER = ["overlay_57", "overlay_61", "overlay_71", "overlay_80", "overlay_81", "overlay_88_0", "overlay_94", "overlay_107", "overlay_121"]
eff = {}
for top in ["."] + ORDER:
    for root, _, fs in os.walk(os.path.join(DP, top, "data")):
        for fn in fs:
            if fn.endswith(".json"):
                full = os.path.join(root, fn); eff[os.path.relpath(full, os.path.join(DP, top))] = full
pairs = set()
def walk(o, ent):
    if isinstance(o, dict):
        if o.get("condition", "").endswith("entity_properties") and o.get("entity") == "this":
            nbt = (o.get("predicate") or {}).get("nbt")
            if nbt: pairs.add((ent, nbt))
        for v in o.values(): walk(v, ent)
    elif isinstance(o, list):
        for v in o: walk(v, ent)
for rel, full in eff.items():
    m = re.search(r"loot_table[\\/]entities[\\/]([a-z_]+)", rel)
    if m: walk(json.load(open(full, encoding="utf8")), m.group(1))

def run(lines):
    start = os.path.getsize(LOG)
    with open(CON, "a", encoding="utf8") as f: f.write("\n".join(lines) + "\n")
    return start

X, Y, Z = 3000, 121, 3000
with open(CON, "a") as f: f.write(f"forceload add {X-16} {Z-16} {X+16} {Z+16}\nfill {X-3} {Y-1} {Z-3} {X+3} {Y-1} {Z+3} stone\n")
time.sleep(2)
bad = []
for ent, nbt in sorted(pairs):
    tag = f"t{time.time_ns()}"
    start = run([f"minecraft:kill @e[type=!player,x={X},y={Y},z={Z},distance=..8]",
                 f"summon {ent} {X} {Y} {Z} {nbt[:-1]},NoAI:1b,Tags:[\"{tag}\"]}}",
                 f"execute if entity @e[tag={tag},nbt={nbt}] run say {tag}OK",
                 f"execute unless entity @e[tag={tag},nbt={nbt}] run say {tag}NO"])
    res = None
    for _ in range(40):
        time.sleep(0.2)
        s = open(LOG, encoding="utf8", errors="replace").read()[start:] if False else None
        with open(LOG, encoding="utf8", errors="replace") as f: f.seek(start); s = f.read()
        if tag + "OK" in s: res = "ok"; break
        if tag + "NO" in s: res = "NO MATCH"; break
    if res != "ok":
        bad.append((ent, nbt, res)); print(f"{res}: {ent} {nbt}", flush=True)
print(f"{len(pairs)} nbt conditions checked, {len(bad)} never match")
