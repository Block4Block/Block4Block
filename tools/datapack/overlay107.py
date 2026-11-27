"""Builds overlay_107 for the Block4Block essential datapack: data format 107 (Minecraft 26.2) renamed two
entity-predicate fields. For every file the pack uses at format 107 (base plus the highest overlay holding it),
convert entity predicates and write the converted copy into overlay_107 if anything changed.
  "type": X                                   -> "minecraft:entity_type": X
  "type_specific": {"type": "minecraft:K", ...} -> "minecraft:type_specific/K'": {...}   (slime -> cube_mob)
  "fishing_hook": {...}                         -> "minecraft:type_specific/fishing_hook": {...}
Run from the datapack folder."""
import json, os, sys

ORDER = ["overlay_57", "overlay_61", "overlay_71", "overlay_80", "overlay_81", "overlay_88_0"]
KIND = {"slime": "cube_mob", "magma_cube": "cube_mob"}
ENTITY_NESTED = ("vehicle", "passenger", "targeted_entity", "minecraft:vehicle", "minecraft:passenger", "minecraft:targeted_entity")
changes = []

def conv_entity(p, where):
    if not isinstance(p, dict):
        return p
    out = {}
    for k, v in p.items():
        if k == "type":
            out["minecraft:entity_type"] = v; changes.append((where, "type"))
        elif k == "type_specific":
            v = dict(v); kind = v.pop("type").split(":")[-1]
            out["minecraft:type_specific/" + KIND.get(kind, kind)] = v; changes.append((where, "type_specific/" + kind))
        elif k == "fishing_hook":
            out["minecraft:type_specific/fishing_hook"] = v; changes.append((where, "fishing_hook"))
        elif k in ENTITY_NESTED:
            out[k] = conv_entity(v, where)
        else:
            out[k] = v
    return out

def conv_damage(p, where):
    if not isinstance(p, dict):
        return p
    return {k: (conv_entity(v, where) if k in ("direct_entity", "source_entity") else
                conv_damage(v, where) if k == "predicate" else v) for k, v in p.items()}

def walk(o, where):
    if isinstance(o, list):
        return [walk(v, where) for v in o]
    if not isinstance(o, dict):
        return o
    cond = o.get("condition", "").split(":")[-1]
    o = {k: walk(v, where) for k, v in o.items()}
    if cond == "entity_properties" and "predicate" in o:
        o["predicate"] = conv_entity(o["predicate"], where)
    if cond == "damage_source_properties" and "predicate" in o:
        o["predicate"] = conv_damage(o["predicate"], where)
    return o

effective = {}
for top in ["."] + ORDER:
    base = os.path.join(top, "data")
    for root, _, files in os.walk(base):
        for fn in files:
            if fn.endswith(".json"):
                full = os.path.join(root, fn)
                effective[os.path.relpath(full, top).replace("\\", "/")] = full

written = 0
for rel, src in sorted(effective.items()):
    before = len(changes)
    data = json.load(open(src, encoding="utf8"))
    new = walk(data, rel)
    if len(changes) > before:
        dst = os.path.join("overlay_107", rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        json.dump(new, open(dst, "w", encoding="utf8", newline="\n"), indent=2, ensure_ascii=False)
        open(dst, "a", encoding="utf8", newline="\n").write("\n")
        written += 1
        print(f"{rel}  (from {os.path.dirname(os.path.dirname(src)) if src.startswith('.' + os.sep + 'overlay') else 'base'}): "
              + ", ".join(sorted({c for w, c in changes[before:]})))
print(f"{written} files written to overlay_107, {len(changes)} fields renamed")
