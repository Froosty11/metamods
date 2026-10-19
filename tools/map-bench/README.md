# Map renderer benchmark: squaremap vs BlueMap (+ Polymer patch)

Same pregenerated world for every run: Minecraft 26.3, Fabric 0.19.5, the S6 performance + worldgen
mods in `mods.txt` (Tectonic + Terralith), S6 seed `127721965112300492`, Chunky square radius 1024
around 0,0 (16,641 chunks), 256 chunks force-loaded around spawn so the server has something to tick.

```sh
JAVA=/path/to/java25 ./setup.sh ~/map-bench      # server, mods, mapper jars, pregenerated world
MAP_BENCH_WORK=~/map-bench ./run_all.sh          # every variant, results/ + results/summary.jsonl
```

`bench.py` measures, per run: the full render (wall, process CPU seconds, peak RSS, `/tick query`
every 15 s), 60 s of idle afterwards, and an incremental update (64 blocks set in 64 chunks,
`save-all flush`, CPU above idle over 150 s). Mapper threads default to 2 (`--threads`, 0 = the
mod's own default). BlueMap variants are the tuning knobs in its map config; see `VARIANTS`.

`jars/BlueMapPolymerPatch-1.2.1+26.3.jar` is the local mc26.3 build of
Metacraft/BlueMap-Polymer-Patch, which has no release yet. `results-laptop/` is a first pass on an
M5 laptop that was busy with other work, so wall times there are noisy.
