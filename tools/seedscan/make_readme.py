#!/usr/bin/env python3
"""Builds results/README.md from final.jsonl, verify-*.jsonl and the rendered images.

python3 -I make_readme.py <results-dir> <final.jsonl> <verify-dir> <stats.json>
"""
import json
import math
import os
import sys

CATS = ["oak", "birch", "spruce", "dark_oak", "jungle", "acacia", "mangrove", "cherry", "pale_oak", "desert", "badlands",
        "snowy", "swamp", "ocean", "peaks", "mushroom", "lush", "deep_dark", "dripstone"]
CAT_LABEL = {"oak": "oak", "birch": "birch", "spruce": "spruce", "dark_oak": "dark oak", "jungle": "jungle", "acacia": "acacia",
             "mangrove": "mangrove", "cherry": "cherry", "pale_oak": "pale oak", "desert": "desert", "badlands": "badlands",
             "snowy": "snow/ice", "swamp": "swamp", "ocean": "ocean", "peaks": "peaks", "mushroom": "mushroom",
             "lush": "lush caves", "deep_dark": "deep dark", "dripstone": "dripstone"}
STRUCT_LABEL = [("village", "villages"), ("trial_chambers", "trial ch."), ("ancient_city", "anc. city"),
                ("desert_pyramid", "desert temple"), ("jungle_pyramid", "jungle temple"), ("pillager_outpost", "outpost"),
                ("monument", "monument"), ("mansion", "mansion"), ("trail_ruins", "trail ruins"), ("swamp_hut", "swamp hut"),
                ("igloo", "igloo")]
STAGE = ["start", "stage 2", "stage 3"]


def stage_cats(r, st):
    return [CAT_LABEL[c] for c in CATS if r["cats"][c] == st]


def struct_counts(r, st):
    out = []
    for key, label in STRUCT_LABEL:
        n = sum(1 for s in r["structures"] if s[0] == key and s[3] == st)
        if n:
            out.append(f"{n} {label}" if key in ("village", "trial_chambers", "ancient_city") else label)
    return out


def nearest(r, key):
    best = None
    for s in r["structures"]:
        if s[0] == key:
            d = math.hypot(s[1] - r["x"], s[2] - r["z"])
            if best is None or d < best[0]:
                best = (d, s)
    return best


def main():
    res, final, vdir, statsf = sys.argv[1:5]
    rows = [json.loads(l) for l in open(final) if l.strip()]
    stats = json.load(open(statsf))
    verify = {}
    for f in os.listdir(vdir):
        if f.startswith("verify-") and f.endswith(".jsonl"):
            lines = [json.loads(l) for l in open(os.path.join(vdir, f)) if l.strip()]
            verify[lines[0]["seed"]] = lines
    # Final ranking: score, minus a penalty where the real server disagrees (set in stats["penalties"]).
    pen = {int(k): v for k, v in stats.get("penalties", {}).items()}
    for r in rows:
        r["final"] = r["score"] - pen.get(r["seed"], 0)
    rows.sort(key=lambda r: -r["final"])
    out = []
    w = out.append
    w("# METAcraft Season 6 seed search")
    w("")
    w("World generation: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Tectonic 3.0.31, Lithostitched 2.0.4, "
      "default `config/tectonic.json`, plus the **Metacraft** datapack (in [`../datapack/Metacraft`](../datapack/Metacraft)) loaded above Tectonic. "
      "All biomes, heights and structures below come from that exact worldgen, computed inside a real 26.3 dedicated server "
      "(see [`../README.md`](../README.md) for how).")
    w("")
    w(f"Searched **{stats['pass1_seeds']} seeds** (pass 1: biomes on a 64-block grid, {stats['pass1_centres']} candidate centres each, "
      f"{stats['pass1_minutes']:.0f} min on 4 cores), then **{stats['pass2_candidates']} candidates** in detail "
      "(pass 2: 16-block grid, heights, valleys, exact structure starts, centre refined to 64 blocks), "
      f"then the top {len(rows)} with real terrain heights, and the top {len(verify)} verified with `/locate` in a real server.")
    w("")
    w("Border stages: **start** = 1000 wide, **stage 2** = 1500 wide (+250 gold blocks), **stage 3** = 2000 wide (+500 gold blocks). "
      "\"In stage 2\" below means newly reachable in stage 2 (not already in the start).")
    w("")
    w("## Top 10")
    w("")
    w("| # | seed | border centre (spawn) | score | ocean in start | biomes/wood: start | + stage 2 | + stage 3 | structures: start | + stage 2 | + stage 3 | nearest stronghold | valleys (stage 2) |")
    w("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for i, r in enumerate(rows[:10]):
        sh = nearest(r, "stronghold")
        shs = f"{sh[0]:.0f} blocks ({STAGE[sh[1][3]] if sh[1][3] < 3 else 'outside'})" if sh else "> 4500"
        w(f"| {i + 1} | `{r['seed']}` | {r['x']}, {r['z']} | {r['final']:.1f} | {r['oceanStart'] * 100:.0f}% | "
          f"{', '.join(stage_cats(r, 0))} | {', '.join(stage_cats(r, 1)) or '–'} | {', '.join(stage_cats(r, 2)) or '–'} | "
          f"{', '.join(struct_counts(r, 0)) or '–'} | {', '.join(struct_counts(r, 1)) or '–'} | {', '.join(struct_counts(r, 2)) or '–'} | "
          f"{shs} | {r['valleyStage2'] * 100:.0f}% |")
    w("")
    w("Missing from all three stages is what is not listed. Wood types: oak, birch, spruce, dark oak, jungle, acacia, mangrove, cherry, "
      "pale oak (pale garden). Score = biome categories (weighted, start 1.0 / stage 2 0.6 / stage 3 0.25) + structures + valleys + "
      "terrain variety − ocean penalty; see [`../README.md`](../README.md).")
    w("")
    for i, r in enumerate(rows[:5]):
        seed = r["seed"]
        w(f"## {i + 1}. Seed `{seed}`, centre {r['x']}, {r['z']}")
        w("")
        if str(seed) in stats.get("why", {}):
            w(stats["why"][str(seed)])
            w("")
        w(f"![map 3000]({seed}_map_3000.png)")
        w("")
        w(f"![height 3000]({seed}_height_3000.png)")
        w("")
        w(f"<details><summary>Context map, 8000 blocks wide</summary>\n\n![map 8000]({seed}_map_8000.png)\n\n</details>")
        w("")
        valleys = r["valleyRegions"][:4]
        if valleys:
            w("Biggest valleys: " + "; ".join(f"~{int(v[0] ** 0.5)}×{int(v[0] ** 0.5)} blocks at {v[1]}, {v[2]} ({STAGE[v[3]]})" for v in valleys)
              + f". Valley share of land: start {r['valleyStart'] * 100:.0f}%, stage 2 {r['valleyStage2'] * 100:.0f}%, stage 3 {r['valleyStage3'] * 100:.0f}%.")
            w("")
        terr = [k for k, v in r["terrain"].items() if v]
        w(f"Terrain types in stage 2: {', '.join(terr)}.")
        w("")
        if seed in verify:
            v = verify[seed]
            w(f"**Verified in a real server** (`/locate` from {r['x']}, ~, {r['z']}; datapacks: {v[0]['datapacks'].split(': ', 1)[-1]}):")
            w("")
            w("| locate | result | Chebyshev distance | stage |")
            w("|---|---|---|---|")
            for row in v[1:]:
                c = row["chebyshev"]
                st = "–" if c is None else (STAGE[0] if c <= 500 else STAGE[1] if c <= 750 else STAGE[2] if c <= 1000 else "outside")
                pos = "not found" if row["pos"] is None else f"{row['pos'][0]}, {row['pos'][1]}"
                w(f"| {row['kind']} `{row['what'].replace('minecraft:', '')}` | {pos} | {'–' if c is None else c} | {st} |")
            w("")
        shots = sorted(f for f in os.listdir(res) if f.startswith(f"{seed}_") and not ("_map_" in f or "_height_" in f))
        if shots:
            w("Screenshots (render distance 12, FOV 70, noon, no HUD):")
            w("")
            for f in shots:
                w(f"![{f}]({f})")
                w("")
    w("## Recreating a world")
    w("")
    w("1. Fabric server for Minecraft 26.3 with Fabric Loader 0.19.5 and these mods (check the hashes):")
    w("   - Fabric API 0.161.0+26.3")
    w("   - Tectonic 3.0.31: https://cdn.modrinth.com/data/lWDHr9jE/versions/j2jmkU85/tectonic-3.0.31-fabric-26.3.jar "
      "(sha256 `33a534a71c5d81ad4b4327570481355c1356e55febbe8c21cc92ffa15a303046`)")
    w("   - Lithostitched 2.0.4: https://cdn.modrinth.com/data/XaDC71GB/versions/FhKHD2LJ/lithostitched-2.0.4-fabric-26.3.jar "
      "(sha256 `b1cd7ca605a9e5cdfb43b60970885002c88b2cab132259e65d7294d36bdf3466`)")
    w("   - Leave `config/tectonic.json` at its defaults (let Tectonic write it).")
    w("2. Before the first start, copy [`datapack/Metacraft`](../datapack/Metacraft) to `world/datapacks/Metacraft` and set "
      "`level-seed=<seed>` in `server.properties`.")
    w("3. Start the server and check `/datapack list` shows `[file/Metacraft (world)]` after `[tectonic:tectonic (built-in: Tectonic)]`.")
    w("4. Border and spawn: `/worldborder center <x> <z>`, `/worldborder set 1000`, `/setworldspawn <x> <y> <z>`.")
    w("")
    w("**Datapack note:** the `flat_terrain_skew.json` from the brief (`{\"type\": \"minecraft:constant\", \"argument\": 0.0}`) does not load in 26.3: "
      "the constant density function's field is now called `value`. The pack here uses `{ \"type\": \"minecraft:constant\", \"value\": 0.0 }`, "
      "which is what was meant. Everything else is exactly as given.")
    w("")
    w("Sanity check of the pack (seed 1): " + "; ".join(stats.get("sanity", [])))
    w("")
    open(os.path.join(res, "README.md"), "w").write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
