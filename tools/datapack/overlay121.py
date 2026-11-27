"""Builds overlay_121 for the Block4Block essential datapack: data format 121 (Minecraft 26.3) rewrote the loot-table
form, and 26.3 silently ignores the old keys, so the old tables load but lose every condition and function (heads
drop on any death, as blank heads). The new form, learned by diffing vanilla 26.2 and 26.3 (conv263.py, which checks
itself against all of vanilla's tables):
  "conditions": [c, ...] -> "condition": c  (several: all_of);  in a condition "condition": X -> "type": X
  "functions": [f, ...]  -> "modifier": [f, ...];                in a function "function": X -> "type": X
  a tag entry's "name": X -> "items": "#X";  block_state_property {block, properties} -> match_block {blocks, state}
Every loot table the pack uses at format 121 (base plus the highest overlay holding it) is converted into
overlay_121. Run from the datapack folder."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from conv263 import convert

ORDER = ["overlay_57", "overlay_61", "overlay_71", "overlay_80", "overlay_81", "overlay_88_0", "overlay_94", "overlay_107"]
effective = {}
for top in ["."] + ORDER:
    for root, _, files in os.walk(os.path.join(top, "data")):
        for fn in files:
            if fn.endswith(".json"):
                full = os.path.join(root, fn)
                effective[os.path.relpath(full, top).replace("\\", "/")] = full
written = 0
for rel, src in sorted(effective.items()):
    if "/loot_table/" not in rel:
        continue
    old = json.load(open(src, encoding="utf8")); new = convert(old)
    if new != old:
        dst = os.path.join("overlay_121", rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, "w", encoding="utf8", newline="\n") as f:
            json.dump(new, f, indent=2, ensure_ascii=False); f.write("\n")
        written += 1
print(f"{written} loot tables written to overlay_121")
