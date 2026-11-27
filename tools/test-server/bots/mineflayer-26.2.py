"""Teaches a local mineflayer install to speak Minecraft 26.2, for the test-copy bots only (no ViaVersion: SQ63, SQ65).

The bot libraries (mineflayer 4.39, minecraft-data 3.119, minecraft-protocol 1.68) stop at 26.1. Their 26.2 work sits
on PrismarineJS's own unreleased branches: minecraft-data `pc_26_2` (the data, pinned below) and node-minecraft-protocol
`pc26_2`, whose only change is adding '26.2' to its version list. This script, run on a folder where
`npm install mineflayer@4.39.0 minecraft-data@3.119.0` was done:
  1. downloads data/pc/26.2 from minecraft-data at the pinned commit and registers it (dataPaths.json, data.js,
     versions.json; the windows file falls back to 26.1's, which the npm package ships);
  2. adds '26.2' to the version lists of minecraft-protocol and mineflayer;
  3. maps 26.2 to the current chunk format in prismarine-chunk, as 26.1 is;
  4. adds '26.2' to every prismarine feature list that ends at '26.1' (physics: liquid gravity).
Usage: py -3 mineflayer-26.2.py <folder with node_modules>"""
import json, os, re, sys, urllib.request

COMMIT = "282f19b06c"   # PrismarineJS/minecraft-data branch pc_26_2, 29 October 2026
FILES = ["attributes", "biomes", "blockCollisionShapes", "blockLoot", "blocks", "commands", "entities", "entityLoot",
         "foods", "items", "language", "loginPacket", "materials", "particles", "protocol", "recipes", "sounds", "tints",
         "version"]
nm = os.path.join(sys.argv[1], "node_modules")
md = os.path.join(nm, "minecraft-data")
data = os.path.join(md, "minecraft-data", "data")
raw = f"https://raw.githubusercontent.com/PrismarineJS/minecraft-data/{COMMIT}/data/"

def fetch(rel):
    return urllib.request.urlopen(urllib.request.Request(raw + rel, headers={"User-Agent": "b4b-test-bots"})).read()

# 1. Data.
os.makedirs(os.path.join(data, "pc", "26.2"), exist_ok=True)
for f in FILES:
    open(os.path.join(data, "pc", "26.2", f + ".json"), "wb").write(fetch(f"pc/26.2/{f}.json"))
paths = json.load(open(os.path.join(data, "dataPaths.json")))
paths["pc"]["26.2"] = json.loads(fetch("dataPaths.json"))["pc"]["26.2"]
json.dump(paths, open(os.path.join(data, "dataPaths.json"), "w"), indent=2)
for name in ("versions", "protocolVersions"):
    p = os.path.join(data, "pc", "common", name + ".json"); cur = json.load(open(p))
    keys = [x if isinstance(x, str) else x["minecraftVersion"] for x in cur]
    if "26.2" not in keys:
        new = [x for x in json.loads(fetch(f"pc/common/{name}.json"))
               if (x if isinstance(x, str) else x["minecraftVersion"]) == "26.2"]
        cur = (cur + new) if name == "versions" else (new + cur)
        json.dump(cur, open(p, "w"), indent=2)
js = open(os.path.join(md, "data.js"), encoding="utf8").read()
if "    '26.2': {" not in js:
    old = {}
    for ln in re.search(r"    '26\.1': \{\n(.*?)\n    \}", js, re.S).group(1).split("\n"):
        k = re.search(r"get (\w+) \(\)|^\s+(proto):", ln); old[k.group(1) or k.group(2)] = ln.rstrip(",")
    lines = []
    for k in sorted(set(paths["pc"]["26.2"]) | set(old)):
        p = paths["pc"]["26.2"].get(k); f = f"minecraft-data/data/{p}/{k}.json" if p else None
        if k == "proto": lines.append(old.get(k, f"      proto: __dirname + '/minecraft-data/data/{p}/proto.yml'"))
        elif f and os.path.exists(os.path.join(md, f)): lines.append(f'      get {k} () {{ return require("./{f}") }}')
        elif k in old: lines.append(old[k])
    js = js.replace("    '26.1': {", "    '26.2': {\n" + ",\n".join(lines) + "\n    },\n    '26.1': {", 1)
    open(os.path.join(md, "data.js"), "w", encoding="utf8").write(js)

# 2. Version lists.
for rel in (("minecraft-protocol", "src", "version.js"), ("mineflayer", "lib", "version.js")):
    p = os.path.join(nm, *rel); s = open(p, encoding="utf8").read()
    if "'26.2'" not in s.split("supportedVersions")[-1].split("testedVersions")[-1]:
        s = s.replace("'1.21.11', '26.1']", "'1.21.11', '26.1', '26.2']"); open(p, "w", encoding="utf8").write(s)

# 3. Chunk format.
p = os.path.join(nm, "prismarine-chunk", "src", "index.js"); s = open(p, encoding="utf8").read()
if "26.2:" not in s:
    s = s.replace("    26.1: require('./pc/1.18/chunk')\n", "    26.1: require('./pc/1.18/chunk'),\n    26.2: require('./pc/1.18/chunk')\n")
    open(p, "w", encoding="utf8").write(s)

# 4. Library feature lists.
for root, _, fs in os.walk(nm):
    if "features.json" in fs and os.path.join("minecraft-data", "minecraft-data") not in root:
        p = os.path.join(root, "features.json"); d = json.load(open(p, encoding="utf8"))
        if isinstance(d, list) and any(isinstance(f, dict) and (f.get("versions") or [None])[-1] == "26.1" for f in d):
            for f in d:
                if isinstance(f, dict) and (f.get("versions") or [None])[-1] == "26.1": f["versions"].append("26.2")
            json.dump(d, open(p, "w", encoding="utf8"), indent=2)

ver = os.popen(f'cd "{sys.argv[1]}" && node -e "const m=require(\'minecraft-data\')(\'26.2\');console.log(m.version.version, Object.keys(m.blocksByName).length)"').read().strip()
print("26.2 ready:", ver)
