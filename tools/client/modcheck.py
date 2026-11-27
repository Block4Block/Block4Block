"""Checks a Fabric mods folder both ways: every 'depends' is present, and no 'breaks' (or 'conflicts') matches a present
mod's version. Version ranges are checked with Fabric's semantics for the common forms (>=, <=, >, <, =, ~, ^, *, x,
space-separated AND, list = OR); prerelease tags (-beta.1) sort before the release.
Usage: modcheck.py MODS_DIR [extra.jar ...] [--without file.jar] MCVER"""
import glob, io, json, os, re, sys, zipfile

args = sys.argv[1:]
mc = args.pop()
without = set()
if "--without" in args:
    i = args.index("--without"); without.add(args[i + 1]); del args[i:i + 2]
mods_dir, extra = args[0], args[1:]
jars = [j for j in glob.glob(os.path.join(mods_dir, "*.jar")) if os.path.basename(j) not in without] + extra

def parse(v):
    v = v.split("+")[0]
    main, _, pre = v.partition("-")
    nums = [int(x) if x.isdigit() else x for x in re.split(r"[._]", main) if x != ""]
    return nums, pre

def cmp(a, b):
    (na, pa), (nb, pb) = parse(a), parse(b)
    for i in range(max(len(na), len(nb))):
        x = na[i] if i < len(na) else 0; y = nb[i] if i < len(nb) else 0
        if x != y:
            if isinstance(x, int) and isinstance(y, int): return -1 if x < y else 1
            return -1 if str(x) < str(y) else 1
    if pa == pb: return 0
    if not pa: return 1
    if not pb: return -1
    return -1 if pa < pb else 1

def one(have, pred):
    pred = pred.strip()
    if pred in ("*", ""): return True
    m = re.match(r"(>=|<=|>|<|=|~|\^)?(.*)", pred); op, ver = m.group(1) or "=", m.group(2)
    if ver.endswith(".x") or ver.endswith(".*"):
        base = ver[:-2]; return have == base or have.startswith(base + ".") or have.startswith(base + "-")
    c = cmp(have, ver)
    if op == ">=": return c >= 0
    if op == "<=": return c <= 0
    if op == ">": return c > 0
    if op == "<": return c < 0
    if op == "~":
        n = parse(ver)[0]; up = n[:2] if len(n) > 1 else n[:1]; up = up[:-1] + [up[-1] + 1]
        return c >= 0 and cmp(have, ".".join(map(str, up))) < 0
    if op == "^":
        n = parse(ver)[0]; up = [n[0] + 1]
        return c >= 0 and cmp(have, ".".join(map(str, up))) < 0
    return c == 0 or have.split("+")[0] == ver.split("+")[0]

def matches(have, spec):
    specs = spec if isinstance(spec, list) else [spec]
    return any(all(one(have, p) for p in s.split()) for s in specs)

have = {"minecraft": mc, "java": "25", "fabricloader": "0.20.1"}
meta = {}
def load(z, top, name):
    m = json.loads(z.read("fabric.mod.json").decode("utf8", "replace"), strict=False)
    for key in [m["id"]] + list(m.get("provides", [])):
        if key not in have or cmp(m["version"], have[key]) > 0: have[key] = m["version"]   # Fabric loads the newest copy
    if top: meta[m["id"]] = (name, m)
    for jj in m.get("jars", []): load(zipfile.ZipFile(io.BytesIO(z.read(jj["file"]))), False, name)
for j in jars: load(zipfile.ZipFile(j), True, os.path.basename(j))

bad = 0
for mid, (name, m) in meta.items():
    for k, spec in m.get("depends", {}).items():
        if k not in have: print(f"MISSING  {mid} needs {k} {spec}"); bad += 1
        elif not matches(have[k], spec): print(f"WRONG    {mid} needs {k} {spec}, have {have[k]}"); bad += 1
    for field in ("breaks", "conflicts"):
        for k, spec in m.get(field, {}).items():
            if k in have and matches(have[k], spec):
                print(f"{field.upper():8} {mid} {field} {k} {spec}, and {have[k]} is present"); bad += 1
print(f"{len(meta)} mods checked, {bad} problems")
