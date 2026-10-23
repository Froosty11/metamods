# seedscan

Finds world seeds and border centres for METAcraft Season 6 (Minecraft 26.3, Fabric, Tectonic + the Metacraft datapack).
Results are in [`results/README.md`](results/README.md).

Seedscan is a small Fabric mod that runs inside a headless 26.3 dedicated server that has the exact production worldgen loaded
(Tectonic 3.0.31, Lithostitched 2.0.4, default Tectonic config, the Metacraft datapack in [`datapack/Metacraft`](datapack/Metacraft)).
For any seed it builds a `RandomState` from the loaded overworld `NoiseGeneratorSettings`, the same way the server does, and samples
the real density functions and the overworld `MultiNoiseBiomeSource`. No chunks are generated, so it can score thousands of seeds
in one server. Chunkbase and cubiomes are not used because they do not know Tectonic.

## Passes

1. **pass1** (biomes only): surface biome on a 64-block grid over ±7040 blocks, plus underground biomes (y = -20 and -50) on a
   128-block grid. The surface y is Tectonic's `preliminary_surface_level`, raised to sea level (63). Against the real terrain
   height this gives the same biome 99–100% of the time at about 1/150 of the cost. Candidate centres are placed every 256 blocks
   within ±5888. Each is scored on biome categories per stage, ocean share and the approximate nearest stronghold (the ring
   formula without the ≤112-block biome nudge). The top 3 distinct centres per seed are kept.
2. **pass2** (detail, best seeds): 16-block grid over ±1800 blocks with heights, 32-block underground grid, and **exact structure
   starts**. Placement, the weighted structure pick and `Structure.generate` (biome and terrain checks, jigsaw assembly) are done
   the way `ChunkGenerator.createStructures` does them, so villages, trial chambers, ancient cities and the rest are what the
   server will place, not guesses. Stronghold positions come from the real ring computation. Valleys and terrain types are
   measured on the height grid. The centre is refined within ±448 blocks in 64-block steps.
3. **final** (`pass2` with `-Dseedscan.final=true`): the same with real terrain heights (`getBaseHeight(WORLD_SURFACE_WG)`), plus
   map data dumps (biomes at 4 blocks per pixel over 3000 blocks, heights at 8 blocks per pixel, and an 8000-block context map).
4. **verify**: a fresh real server on the seed (datapack in place before creation) runs `/datapack list`, then `/locate structure`
   and `/locate biome` from the centre.
5. **pregen** + **Shots** (client game test): the verified world is force-loaded around the camera positions, copied into a
   Fabric client's `saves/`, and screenshotted under Xvfb with Mesa llvmpipe.

## Scoring

Stage factor: start 1.0, stage 2 0.6, stage 3 0.25. Border half widths are 500 / 750 / 1000.

- **Biome categories** (weight × stage factor of the first stage that has it): oak 0.5, birch 0.8, spruce 0.8, dark oak 1,
  jungle 1, acacia 1, mangrove 1, cherry 1, pale oak 1, desert 1, badlands 1.2, snow/ice 1, swamp 0.8, ocean 0.6, peaks 1.2,
  mushroom 0.3, lush caves 1, deep dark 1, dripstone 0.2. A category counts as present with ≥ 8192 m² on the surface, or
  ≥ 8192 m² underground (pass 2).
- **Ocean**: −8 × (share in start − 25%) and −4 × (share in stage 2 − 40%), when positive.
- **Structures**: villages +1.5 (≥1 in start), +1 (≥2 within stage 2), +0.5 (≥3 within stage 3). Weight × stage factor for
  trial chambers 1.5, ancient city 1.2, desert/jungle temple 0.4, pillager outpost 0.4, monument 0.4, mansion 0.6,
  trail ruins 0.4, swamp hut 0.3, igloo 0.2, abandoned camp 0.1. Nearest stronghold (Chebyshev): ≤500 +1.5, ≤750 +0.9,
  ≤1000 +0.4, ≤1500 +0.15.
- **Valleys**: a land cell is a valley if, in at least one pair of opposite directions (N/S, E/W or the two diagonals), there is
  ground at least 40 blocks higher within 256 blocks. Score +2 × min(1, valley share of land in stage 2 / 15%).
- **Terrain variety** in stage 2, +0.3 each: lowland (≥10% of land at y ≤ 85), hills (≥10% at 85–140), plateau (≥3% flat
  ground above y 100), peaks (≥1% at y ≥ 200), coast (beaches or stony shore).

## Running it

Needs Java 25 (`JAVA_HOME`). For screenshots it also needs `xvfb` and Mesa.

```sh
scripts/setup.sh                         # server base in work/server-base, mods hash-checked
./gradlew build
scripts/run-job.sh work/pass1 0 pass1 -Dseedscan.count=12000 -Dseedscan.minutes=65
python3 -I select.py pass1 work/pass1/out/pass1.jsonl work/pass2/out/pass2-input.txt 200 60
scripts/run-job.sh work/pass2 0 pass2
python3 -I select.py pass2 work/pass2/out/pass2.jsonl work/final/out/pass2-input.txt 10
scripts/run-job.sh work/final 0 pass2 -Dseedscan.final=true
scripts/run-job.sh work/v-<seed> <seed> verify -Dseedscan.centre=<x>,<z>
python3 -I render.py work/final/out/dump/<seed> results
python3 -I plan_shots.py work/final/out/dump/<seed> work/v-<seed>/world >> shots.txt
scripts/run-job.sh work/v-<seed> <seed> pregen -Dseedscan.shots=$PWD/shots.txt
Xvfb :77 -screen 0 1920x1080x24 +extension GLX &
DISPLAY=:77 ./gradlew runClient -Dseedscan.shots=$PWD/shots.txt -Dseedscan.shotsOut=$PWD/results
```

The server instances share the base install. `run-job.sh` copies the datapack into `world/datapacks` before the first start and
writes `server.properties` with `max-tick-time=-1`, so long jobs do not trip the watchdog.

Headless rendering: 26.3 asks SDL for an sRGB-capable framebuffer, and Xvfb's llvmpipe visuals do not offer one. With
`-Dseedscan.noSrgb=true` (set for `runClient`) a client mixin drops that request. The game never enables `GL_FRAMEBUFFER_SRGB`,
so the image is unchanged.
