"""Data format 121 (Minecraft 26.3) loot-table form, learned by diffing vanilla 26.2 and 26.3:
  "conditions": [c, ...]  ->  "condition": c          (several: {"type": "minecraft:all_of", "terms": [...]})
  in a condition, "condition": X  ->  "type": X
  "functions": [f, ...]   ->  "modifier": [f, ...];  in a function, "function": X  ->  "type": X
  a tag entry's "name": X ->  "items": "#X"
convert(obj) returns the 26.3 form. Run as a script to check it against the game's own tables:
  conv263.py <26.2 loot_table dir> <26.3 loot_table dir>"""
import json, os, sys

# Number-provider fields (not predicate ranges, which keep {min, max}).
NUMBER_FIELDS = {"count", "rolls", "bonus_rolls", "damage", "duration", "amplifier", "value", "add"}

def cond(c):
    c = walk(c)
    if isinstance(c, dict) and "condition" in c and "type" not in c:
        c = {"type": c.pop("condition"), **c}
    # block_state_property {block, properties} is now match_block {blocks, state}
    if isinstance(c, dict) and c.get("type") in ("minecraft:block_state_property", "block_state_property"):
        c = {"type": "minecraft:match_block", **{({"block": "blocks", "properties": "state"}).get(k, k): v
                                                 for k, v in c.items() if k != "type"}}
    return c

def conds(lst):
    lst = [cond(c) for c in lst]
    return lst[0] if len(lst) == 1 else {"type": "minecraft:all_of", "terms": lst}

def walk(o):
    if isinstance(o, list):
        return [walk(v) for v in o]
    if not isinstance(o, dict):
        return o
    out = {}
    for k, v in o.items():
        if k == "conditions" and isinstance(v, list):
            out["condition"] = conds(v)
        elif k == "functions" and isinstance(v, list):
            out["modifier"] = [fn(f) for f in v]
        elif k in ("terms", "term"):
            out[k] = [cond(t) for t in v] if isinstance(v, list) else cond(v)
        elif k in NUMBER_FIELDS and isinstance(v, dict) and "type" not in v and {"min", "max"} <= set(v):
            out[k] = {"type": "minecraft:uniform", **walk(v)}   # 26.3 no longer reads a typeless {min, max} as uniform
        elif k in NUMBER_FIELDS and isinstance(v, dict) and "type" not in v and set(v) == {"n", "p"}:
            out[k] = {"type": "minecraft:binomial", **v}
        else:
            out[k] = walk(v)
    # a damage source's tag tests ({"id": X, "expected": b}) now name the tag as "#X"
    if isinstance(out.get("tags"), list):
        out["tags"] = [({**t, "id": t["id"] if t["id"].startswith("#") else "#" + t["id"]}
                        if isinstance(t, dict) and isinstance(t.get("id"), str) and "expected" in t else t) for t in out["tags"]]
    if out.get("type") in ("minecraft:tag", "tag") and "name" in out and "items" not in out:
        n = out.pop("name"); out["items"] = n if n.startswith("#") else "#" + n
    return out

def fn(f):
    if isinstance(f, list):
        return [fn(x) for x in f]
    f = walk(f)
    if isinstance(f, dict) and "function" in f and "type" not in f:
        f = {"type": f.pop("function"), **f}
    # filtered: the function it applied on a match was "modifier"; 26.3 names it on_pass (beside on_fail)
    if isinstance(f, dict) and f.get("type") in ("minecraft:filtered", "filtered"):
        if "modifier" in f and "on_pass" not in f:
            f["on_pass"] = f.pop("modifier")
        for k in ("on_pass", "on_fail"):
            if k in f: f[k] = fn(f[k])
    return f

def convert(o):
    return walk(o)

def norm(o):
    if isinstance(o, dict): return {k: norm(v) for k, v in o.items()}
    if isinstance(o, list): return [norm(v) for v in o]
    if isinstance(o, float) and o.is_integer(): return int(o)
    return o

if __name__ == "__main__":
    a, b = sys.argv[1], sys.argv[2]; same = diff = 0; bad = []
    for root, _, fs in os.walk(a):
        for f in fs:
            pa = os.path.join(root, f); pb = os.path.join(b, os.path.relpath(pa, a))
            if not os.path.exists(pb): continue
            x = norm(convert(json.load(open(pa, encoding="utf8")))); y = norm(json.load(open(pb, encoding="utf8")))
            if x == y: same += 1
            else: diff += 1; bad.append(os.path.relpath(pa, a))
    print(same, "match", diff, "differ"); print(bad[:15])
