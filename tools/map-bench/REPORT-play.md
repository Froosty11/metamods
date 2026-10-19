# Tick time during play: no mapper vs squaremap vs BlueMap (no caves)

Machine: 4 vCPU cloud VM (Intel Xeon @ 2.10GHz, KVM), 15 GB RAM, Temurin 25.0.4.1, 6 GB heap.
World: the fixed S6 world from `setup.sh` (radius 1024 pregenerated). Mappers: squaremap 1.4.3
(default config), BlueMap 5.28 + Polymer patch 1.2.1 with `remove-caves-below-y: 10000`. Both with
2 render threads, both fully prerendered before the measured runs. 3 interleaved reps per variant
(`run_play.sh`), raw data in `results/play-*-r{1,2,3}.json`.

Each rep: 4 Carpet fake players; 5 min **explore** (2 bots fly outward at 8 blocks/s generating new
terrain, 2 drift and place a block per second), then 5 min **settled** (all 4 drift and place blocks
inside the pregenerated area). `/tick query` every 5 s (60 samples per phase per rep). Values are the
mean over the 3 reps with min–max in brackets. 99.6–99.8% of setblocks returned "Changed the block"
in every run (the rest is 2 "not loaded" per phase when a bot jumps to its new path), so the bots
really did keep their chunks loaded.

## Explore

| variant | mean MSPT | mean P95 | worst P99 | 5 s samples avg > 50 ms | cores used |
|---|---|---|---|---|---|
| none | 16.0 (13.8–18.8) | 26.5 (23.0–30.8) | 894 | 0 / 180 | 1.8 (1.8–1.9) |
| squaremap | 17.8 (16.3–18.8) | 30.8 (28.7–32.7) | 952 | 0 / 180 | 1.9 (1.9–2.0) |
| BlueMap no-caves | 27.2 (25.3–29.4) | 43.7 (40.5–47.4) | 1128 | 2 / 180 (2/0/0) | 3.7 (3.7–3.8) |

## Settled

| variant | mean MSPT | mean P95 | worst P99 | 5 s samples avg > 50 ms | cores used |
|---|---|---|---|---|---|
| none | 12.9 (11.7–14.4) | 18.6 (18.0–20.0) | 300 | 0 / 180 | 0.5 (0.5–0.5) |
| squaremap | 13.1 (13.0–13.2) | 19.0 (18.8–19.1) | 216 | 0 / 180 | 0.5 (0.5–0.5) |
| BlueMap no-caves | 20.9 (20.5–21.4) | 34.1 (33.2–34.8) | 162 | 0 / 180 | 3.2 (3.2–3.2) |

**BlueMap catch-up after the bots leave:** 810 s (796–818), i.e. ~13.5 min to finish rendering what 10
min of play produced. squaremap has no pending-task signal; its tile output stayed tiny (3.4 MB).
BlueMap's startup update on an already rendered map: 9–14 s.

## In plain words

- **squaremap does not measurably hurt tick time.** +1.8 ms mean MSPT while exploring (+4 ms P95), but
  the rep ranges overlap with no mapper (16.3–18.8 vs 13.8–18.8), so it is within noise. In settled play
  it is +0.2 ms. It uses ~0.1 extra cores.
- **BlueMap no-caves does.** Versus no mapper: +11.2 ms while exploring, +8.0 ms settled; P95 goes from
  ~27 to ~44 ms (explore) and ~19 to ~34 ms (settled). Versus squaremap: **+9.4 ms exploring (19% of the
  50 ms budget) and +7.8 ms settled (16% of the budget)**. That is well outside rep-to-rep noise: the
  worst BlueMap rep is above the best squaremap rep by 6.5 ms (explore) and 7.3 ms (settled). The
  server still held 20 TPS: only 2 of 180 BlueMap explore samples averaged above 50 ms, all in rep 1.
- **The "settled" BlueMap number is really "BlueMap working off the explore backlog".** It stayed at 3.2
  cores for the whole settled phase and needed ~13.5 min more after the bots left. On 4 vCPU, with the
  mapper still busy from exploration, settled play costs ~8 ms/tick. It would be lower once BlueMap is
  idle, but under continuous exploration it never is.
- **Spikes are not caused by the mappers.** Worst P99 is ~0.9–1.1 s in every variant, *including no
  mapper*. There were no multi-second ticks. The ~1 s ticks come from chunk generation and loading
  while the bots teleport: on the server thread, C2ME's chunk-holder futures
  (`NewChunkHolderVanillaInterface.getTickingChunk*`) are the largest non-idle frames. BlueMap makes the
  worst P99 a bit higher (1128 vs 894–952 ms) because it competes for the same cores.
- **Why BlueMap costs tick time: CPU contention, not main-thread work.** In an extra profiled BlueMap rep
  (spark, all threads, explore + settled: https://spark.lucko.me/89SOrIveOg), BlueMap code accounts for
  **0.03%** of the server thread's samples (only an end-of-tick hook). Per-thread CPU from `top -H` over
  the same 10 min: BlueMap render threads 1.37 cores, BlueMap-FJP (4 threads, mostly Caffeine cache
  cleanup contending on a lock) 0.66, C2ME workers 0.45, server thread 0.23, C2 JIT 0.13. So BlueMap
  takes ~2 of the 4 vCPUs even with `render-thread-count: 2`, and the server thread and worldgen run
  slower because of it. On a machine with more cores than BlueMap + C2ME + the server thread need, the
  gap should shrink. This was not measured here.

## Caveats

- 4 vCPU cloud VM. Results depend on core count, since the effect is CPU contention.
- The bots are Carpet fake players in spectator mode, teleported once a second over RCON. They are not
  real clients: there is no network or packet load, no entity interaction, and movement is in 1 s jumps.
- Worldgen (Tectonic + Terralith via C2ME) dominates the explore phase in every variant.
- The settled phase directly follows explore, so BlueMap's settled number includes its explore backlog
  (see above).
- squaremap's catch-up can't be timed: it has no task queue to poll.
