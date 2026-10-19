# Map renderer benchmark: squaremap vs BlueMap — cloud run, 2026-10-19

Machine: 4 vCPU Intel Xeon @ 2.80 GHz (KVM cloud VM, shared host), 15 GB RAM, no swap, Temurin 25.0.4.1,
server heap 6 GB (see `preflight.txt`). World: 16,641 pregenerated chunks (Chunky radius 1024, S6 seed,
Tectonic + Terralith), 256 chunks force-loaded. Mapper threads = 2 unless noted. All 17 runs in
`results/`; raw per-run JSON includes the 15 s `/tick query` samples.

## Results

Mean of reps; (min–max) where there are 2+. "MSPT render" = mean of the `/tick query` samples taken
every 15 s during the full render; "idle" = after the render. "Incr CPU" = CPU-s above the idle rate in
the 150 s after changing 64 chunks. Ratios are vs squaremap (2 threads).

| variant | thr | n | wall s | CPU s | CPU s / 1k chunks | MSPT render avg / P95 | MSPT idle avg / P95 | incr CPU s | output MB | files | peak RSS MB | wall× | CPU× | size× |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| squaremap | 2 | 3 | 13 (11–16) | 50 (39–59) | 3.0 (2.4–3.6) | (30 / 74)¹ | 6.5 / 10.9 | −18 (−24…−8)² | 2.3 | 46 | 3471 (3050–4261) | 1 | 1 | 1 |
| squaremap | default | 1 | 16 | 56 | 3.4 | (20 / 52)¹ | 5.5 / 9.7 | −5² | 2.3 | 46 | 3906 | 1.2 | 1.1 | 1.0 |
| bluemap (default + Polymer patch) | 2 | 2 | 1813 (1717–1908) | 5497 (5193–5800) | 330 (312–349) | 6.7 / 14.2 | 4.4 / 7.8 | 49 (12–85) | 366 | 4196 | 5871 (5827–5915) | 136 | 110 | 159 |
| bluemap-nopatch | 2 | 1 | 1770 | 5426 | 326 | 7.1 / 15.6 | 3.2 / 5.8 | 86 | 366 | 4196 | 5516 | 133 | 109 | 159 |
| bluemap-flat (no 3D/free-flight) | 2 | 2 | 462 (459–466) | 1345 (1329–1360) | 81 (80–82) | 10.0 / 20.5 | 9.6 / 19.7 | 5 (4–5) | 91 | 4196 | 5400 (4710–6089) | 35 | 27 | 40 |
| bluemap-nocaves | 2 | 1 | 1284 | 3980 | 239 | 7.9 / 16.9 | 5.8 / 11.6 | 55 | 279 | 4196 | 5694 | 97 | 80 | 121 |
| bluemap-miny (mask min-y 0) | 2 | 1 | 1417 | 4258 | 256 | 6.7 / 14.6 | 4.5 / 10.0 | 73 | 361 | 4196 | 5639 | 107 | 85 | 157 |
| bluemap-lowres (no hires) | 2 | 1 | 378 | 1129 | 68 | 10.7 / 21.3 | 5.6 / 10.3 | 9 | 4.5 | 100 | 6046 | 28 | 23 | 2.0 |
| bluemap-zstd | 2 | 1 | 1732 | 5386 | 324 | 8.8 / 18.6 | 3.8 / 6.9 | 80 | 425 | 4196 | 5911 | 130 | 108 | 185 |
| bluemap-lean (flat + nocaves + min-y) | 2 | 2 | 503 (492–514) | 1443 (1411–1474) | 87 (85–89) | 7.9 / 17.0 | 4.7 / 8.7 | −2 (−4…0) | 89 | 4196 | 5950 (5913–5988) | 38 | 29 | 39 |
| bluemap-lean | 1 | 1 | 903 | 1459 | 88 | 4.9 / 8.9 | 4.1 / 7.7 | 21 | 89 | 4196 | 5791 | 68 | 29 | 39 |
| bluemap-lowres-lean | 2 | 2 | 465 (459–472) | 1380 (1372–1389) | 83 (82–84) | 11.4 / 23.0 | 6.5 / 12.2 | 6 (2–10) | 4.5 | 100 | 5926 (5905–5948) | 35 | 28 | 2.0 |

¹ squaremap finishes in 11–16 s, so it gets at most one tick sample, taken right after boot while
the server is still settling (the before-render sample is similar: 12–23 ms avg). Treat as noise.
² Negative = below the noise floor. The idle baseline is taken 20 s after the render, while the
server is still settling, so this column is good to about ±25 CPU-s.

## Answers

**How much heavier is BlueMap?** At defaults: ~30 min wall and ~5,500 CPU-s for 16.6k chunks,
**~110× squaremap's CPU, ~136× its wall time, ~160× its disk** (366 MB / 4,196 files vs 2.3 MB / 46).
At its leanest (lowres-only), BlueMap is still **~23× squaremap's CPU** (378 s wall, 1,129 CPU-s), with
output about 2× squaremap's (4.5 MB). With 2 render threads BlueMap keeps about 2.9 of the 4 cores busy
for the whole render (render threads plus ~1 core of GC/IO/server overhead). Every variant burns CPU at
the same rate, so the knobs only change how long it runs.

**Which knobs help, alone (CPU vs default):**
- **Flat-only** (perspective + free-flight off): **−76% CPU, −75% size.** Cost: no 3D or free-flight
  views, only the top-down map. This is the knob that matters.
- **Lowres-only** (`enable-hires: false`): **−79% CPU, −99% size.** Cost: no hires tiles, so no
  close-up zoom at all. The far-zoom view (3D and flat) still works.
- **Remove caves** (`remove-caves-below-y: 10000`): −28% CPU, −24% size. Cost: underground
  air/caves are filled, so cave openings, ravines and overhangs look solid.
- **Min-y 0 render mask**: −22% CPU, −2% size. Cost: nothing below y=0 (deep ravines, deep-ocean
  floors and deepslate caves vanish). The CPU saving is real but the visible map hardly changes.
- **zstd**: no CPU change, and output is **16% bigger** than gzip. Not worth it.
- **Threads 2 → 1** (lean): same total CPU (1,459 vs 1,443 CPU-s) at about half the load: wall time
  rises 503 → 903 s, average load drops ~2.9 → ~1.6 cores, and render MSPT drops 7.9 → 4.9 ms.
- **Stacking does not help:** no-caves + min-y on top of flat costs about **+7% CPU** (lean 1,443 vs flat
  1,345). On top of lowres it costs about +22% (1,380 vs 1,129). Once hires geometry is gone, the extra
  per-block checks cost more than they save. The laptop pass showed the same effect (lean 398 vs
  flat 316 CPU-s).

**Polymer patch:** no measurable cost. Without the patch: 1,770 s / 5,426 CPU-s. With it: 1,717–1,908 s
/ 5,193–5,800 CPU-s. The no-patch run falls inside the patched range, and the output is identical.

**Tick time:** neither mapper visibly hurts it on this box. All `/tick query` averages during BlueMap
renders were 5–11 ms, P95 at or under 23 ms, so **TPS stayed at 20 throughout** (the worst single 15 s
sample was a 63 ms average, once). There are occasional P99 spikes of 300–1,800 ms, a handful per run,
mostly in the first sample after boot. They appear in some lean/flat runs too, so treat them as
GC/startup hitches, not something a knob fixes. Flat/lowres variants show somewhat higher render MSPT
(10–11 ms) than default (7 ms). The likely cause is that they hit the server's chunk IO harder per
second. One thread halves it.

**Ongoing cost (incremental, 64 changed chunks):** default BlueMap ≈ 50 CPU-s (12–86, noisy) to
re-render the touched tiles, about 0.3 core over 150 s. flat/lean/lowres ≈ 0–10 CPU-s, which is
indistinguishable from squaremap and from the ±25 CPU-s noise floor. Idle CPU after the render is
about 0.2–0.4 core for every variant, the same as squaremap. Once the first render is done, a flat
BlueMap costs about nothing extra.

## Recommendation (S6, ~4 cores, if BlueMap stays)

```hocon
# maps/world.conf
enable-perspective-view: false
enable-free-flight-view: false
# leave remove-caves-below-y and render-mask at defaults: on a flat map they add CPU, not save it
# core.conf
render-thread-count: 1
# storages/file.conf: keep compression: gzip
```

Expected: a one-off full render of a 16.6k-chunk world in about 15 min using about 1.6 cores, with
tick time unaffected (with 4 exploring bots: +4 ms average tick, TPS ≥ 19.9 on average), ~90 MB on disk, and near-zero ongoing cost. If close-up zoom isn't needed,
`enable-hires: false` (keeping 3D at far zoom) is ~15% cheaper still and ~20× smaller. If the 3D
views are wanted, plan on ~4× the CPU of flat. Either way, squaremap does the same top-down job for
about 1/25 of the CPU.

## With simulated players (players.py)

4 Carpet fake players (spectator) start at spawn and travel diagonally outward at 6 blocks/s,
about sprint speed, for 10 min. For the first ~4 min they cross the pregenerated area; for the last
~6 min they push into new terrain that has to be generated. The mapper works at the same time:
BlueMap does its initial full render under load (the worst case), and squaremap does a full render
plus live updates. `/tick query` runs every 5 s, so the samples cover the whole window. There are 2
interleaved reps per variant; `results/players/`.

| mapper | thr | n | MSPT avg | MSPT P95 | MSPT avg, old terrain (0–4 min) | MSPT avg, new terrain (4–10 min) | mean TPS | worst 5 s TPS | 5 s windows < 20 TPS (of 119) | lag spikes logged | cores used |
|---|---|---|---|---|---|---|---|---|---|---|---|
| none | – | 2 | 25.6 (25.0–26.2) | 41.1 (39.5–42.8) | 31.2 (30.4–32.0) | 21.9 (21.4–22.4) | 19.96 (19.96–19.97) | 15.4 | 1 (1–1) | 2 (2–3) | 3.0 (2.9–3.0) |
| squaremap | 2 | 2 | 26.4 (26.2–26.5) | 42.6 (42.3–42.9) | 31.7 (30.7–32.7) | 22.9 (22.5–23.2) | 20.00 (20.00–20.00) | 19.8 | 0 (0–1) | 2 (2–2) | 3.0 (3.0–3.0) |
| bluemap | 2 | 2 | 30.9 (30.3–31.5) | 52.1 (50.7–53.5) | 42.7 (42.4–43.0) | 23.2 (22.0–24.4) | 19.88 (19.88–19.89) | 15.1 | 8 (5–10) | 6 (6–6) | 3.9 (3.9–3.9) |
| bluemap-flat | 1 | 2 | 29.5 (28.1–30.9) | 47.1 (44.4–49.9) | 34.7 (33.5–35.9) | 26.1 (24.6–27.6) | 19.94 (19.93–19.94) | 16.7 | 4 (3–4) | 3 (3–3) | 3.6 (3.6–3.6) |
| bluemap-flat | 2 | 2 | 28.6 (27.8–29.5) | 46.5 (45.5–47.4) | 39.0 (38.4–39.7) | 21.9 (20.9–22.9) | 19.92 (19.86–19.98) | 16.6 | 6 (4–7) | 4 (2–5) | 3.9 (3.8–3.9) |

"Lag spikes logged" = "Can't keep up" warnings after the bots spawned.

- **TPS barely moves.** Mean TPS is 19.88–20.00 for every variant. The worst 5 s window drops to
  15–17 TPS, and that happens even with no mapper at all (15.4).
- **squaremap is indistinguishable from no mapper:** +0.8 ms average tick, 0 extra windows below
  20 TPS.
- **BlueMap default costs about +5 ms average and +11 ms P95.** It costs about +11 ms while the bots
  are in loaded terrain (31 → 43 ms). It produces ~8 windows below 20 TPS vs ~1 without a mapper,
  and pins the box at 3.9 of 4 cores.
- **BlueMap flat on 1 thread is the gentlest BlueMap:** +4 ms average, only +3.5 ms in loaded terrain,
  3–4 windows below 20 TPS, and it leaves about 0.4 core free. Flat on 2 threads sits in between.
- **New terrain shows lower tick times than old terrain for every variant.** The bots outrun chunk
  generation, so fewer chunks reach the fully ticking state. The worldgen cost lands on C2ME's
  threads, not the tick.
- **Caveat:** 4 spectator bots don't build, fight or receive packets. Exploring alone already
  uses ~3 of the 4 cores, though, so this is a fair picture of how little headroom an S6 box has. With
  more real players, BlueMap competes for the same cores, and a 1-thread flat render (or doing the
  initial render before opening) matters more. On a faster or wider CPU the same load leaves more
  headroom, and all of these gaps shrink.

## Caveats

- **Cloud VM** with neighbours; the 2-rep spreads are ≤ 10% (BlueMap default 1,717 vs 1,908 s).
  Single-rep rows (the tail of `run_all.sh`) carry the same uncertainty.
- **squaremap's render is so short** (11–16 s) that its numbers are dominated by chunk loading and
  post-boot settling, including its tick samples and incremental figure. The ratios are lower bounds on
  how much cheaper it is.
- **Peak RSS mostly reflects the 6 GB `-Xms` heap getting touched,** not mapper footprint. Read it as
  "BlueMap fills the heap, squaremap mostly doesn't".
- **Harness fixes made for this run:**
  - Added ResourcefulConfig, a missing hard dependency of Structure Layout Optimizer.
  - Created the `runs/` directory, which was failing on Linux.
  - Set `pause-when-empty-seconds=0`. Vanilla stops ticking an empty server after 60 s, which froze
    every `/tick query` sample and the idle baseline. The first attempt was discarded and every
    result here was run with the fix.

## vs results-laptop/ (M5, busy, earlier harness)

Absolute numbers differ a lot: the cloud BlueMap default used 4× the laptop's CPU (5,497 vs 1,372
CPU-s) and 2.6× its wall time. The laptop pass was also taken without the pause fix. The **ratios agree**:

- **CPU vs BlueMap default (laptop / cloud):**

  | variant | laptop | cloud |
  |---|---|---|
  | flat | 0.23 | 0.24 |
  | lean | 0.29 | 0.26 |
  | lowres | 0.19 | 0.21 |
  | min-y | 0.76 | 0.77 |

- **Output sizes** match to within 1%.
- **Lean was slower than flat** on both machines.
- **Exception:** remove-caves helped little on the laptop (0.92) but 0.72 here, so trust the cloud
  figure.
- **BlueMap vs squaremap:** wall ~135× on both; CPU ~75× on the laptop vs ~110× here.
