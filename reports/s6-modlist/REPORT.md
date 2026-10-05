# METAcraft Season 6 — server-side worldgen pack benchmark

Server-side-only worldgen candidates for METAcraft S6 (Minecraft 26.3, Fabric 0.19.5, Fabric API 0.161.0+26.3), benchmarked by Chunky pregen on a 4-core cloud VM. **Screenshots were not taken**: the run was stopped at the owner's request after all benchmarks finished. The screenshot pipeline was proven to work (see below), but no comparison images were produced.

## Machine and method

```
# Preflight 2026-10-05T11:27:14Z
## Host check (HTTP status, any response = reachable)
piston-meta.mojang.com/mc/game/version_manifest_v2.json 200
maven.fabricmc.net/ 200
api.modrinth.com/v2/project/fabric-api 200
cdn.modrinth.com/ 404
github.com/ 400
resources.download.minecraft.net/ 404
api.adoptium.net/ 200
## nproc
4
## free -g
               total        used        free      shared  buff/cache   available
Mem:              15           0          14           0           0          15
Swap:              0           0           0
## uname -a
Linux vm 6.18.44-fc-v70 #1 SMP PREEMPT_DYNAMIC @0 x86_64 x86_64 x86_64 GNU/Linux
## df -h
Filesystem               Size  Used Avail Use% Mounted on
/dev/vda                 252G  8.8G   30G  23% /
## lscpu
CPU(s):                                  4
Model name:                              Intel(R) Xeon(R) Processor @ 2.10GHz
Thread(s) per core:                      1
## Java
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
```

One fixed seed (20251006). Every run uses a fresh world, the same JVM flags (`-Xms6G -Xmx6G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+AlwaysPreTouch`) and no players. Chunky pregens a square centred at 0,0: Overworld R=1000 (16,129 chunks), Nether R=1200, End R=2200. Radii were picked so that B0 takes about 3–4 min per dimension. B0 = Fabric API + C2ME + Distant Horizons (server, default config) + spark + Chunky. Timing stops at Chunky's 'Task finished'; the server then idles for 20 s with CPU measured ('post-CPU'). This is the same in every config. DH did not generate LODs without players; it only builds LODs from generated chunks, and in the Nether that backlog keeps about 40% CPU busy after Chunky finishes. spark profiler (`--thread *`) ran during each pregen; health and profile links are in bench.csv. Runs: B0 and packs 3× Overworld; single-mod configs 1× (per the mid-run change of plan; terralith has 2). Pack Nether/End runs are 1× each. Single runs carry about ±3% noise (B0 spread 65.6–68.6 chunks/s). Every run's world was checked against B0 (region-file heights on a 16-block grid, biome histogram, `locate`). The WORLD_SURFACE height at a sampled point differs from B0 at about 15% of points even for V0/V0+C2ME (tree/feature placement order). Terrain mods differ at 35–80%, and Terralith, Tectonic, Hybrid Beta and Continents add biomes that B0 does not have.

## Exact versions

Minecraft 26.3 (release), Fabric loader 0.19.5, Fabric API 0.161.0+26.3, Java: Temurin 25.0.4.1+1. Every mod jar was downloaded from Modrinth and checked against its sha1.

| slug | version | file | sha1 |
|---|---|---|---|
| amethyst-nether | v4.4+mod | `amethyst-nether-v4.4.jar` | `e73a0967fd77` |
| amplified-nether | 1.3.0+26.3 | `Amplified_Nether_26.3_v1.3.0+26.3.jar` | `0c61d13da099` |
| ati-structures-vanilla-edition | 1.4.7 | `ATi Structures Vanilla V1.4.7 (26.3).jar` | `9a8daefdc66e` |
| c2me-fabric | 0.4.2-alpha.0.89+26.3 | `c2me-fabric-mc26.3-0.4.2-alpha.0.89.jar` | `1aa0ea789373` |
| chunky | 1.5.3 | `Chunky-Fabric-1.5.3.jar` | `fc1f2f374c5c` |
| cloth-config | 26.3.159+fabric | `cloth-config-fabric-26.3.159.jar` | `9a5cce069380` |
| continents | 1.2.1+26.3 | `Continents_26.3_v1.2.1+26.3.jar` | `7c3fa107b602` |
| cristel-lib | fabric-26.3-3.1.13 | `cristellib-fabric-26.3-3.1.13.jar` | `9cc655e54a8e` |
| distanthorizons | 3.3.4-26.3 | `DistantHorizons-3.3.4-26.3-fabric-neoforge.jar` | `310c0363068d` |
| dungeons-and-taverns | 6.0.2+mod | `dungeons-and-taverns-6.0.2.jar` | `172ba3915c8d` |
| endercon | 4.0+mod | `endercon-4.0.jar` | `cdd766243e13` |
| epic-structures-villages | 2.0.0+mod | `epic-structures-villages-2.0.0.jar` | `fc4f4998c5cf` |
| fabric-api | 0.161.0+26.3 | `fabric-api-0.161.0+26.3.jar` | `53f9ee02c370` |
| formations | 1.0.4-fabric-mc26.3 | `formations-1.0.4-fabric-mc26.3.jar` | `fc21ab8becd3` |
| formations-nether | 1.0.5b-mc1.21+ | `formationsnether-1.0.5b-mc1.21+.jar` | `6b0e0f58399f` |
| formations-overworld | 1.0.5c-mc1.21+ | `formationsoverworld-1.0.5c-mc1.21+.jar` | `717033f82390` |
| geophilic | 3.7 | `Geophilic v3.7.mod.jar` | `4d668978e18b` |
| hopo-better-mineshaft | 1.3.8 | `HopoBetterMineshaft-[26.3]-1.3.8.jar` | `542983da3c93` |
| hopo-better-ruined-portals | 1.5.2 | `HopoBetterRuinedPortals-[26.3]-1.5.2.jar` | `544e6e82055c` |
| hopo-better-underwater-ruins | 1.2.9 | `HopoBetterUnderwaterRuins-[26.3]-1.2.9.jar` | `4ba0a00574d5` |
| hybrid-beta | 1.0.7+mod | `hybrid-beta-1.0.7.jar` | `02adbca043ff` |
| incendium | 5.5.3+26.3 | `Incendium_26.3_v5.5.3+26.3.jar` | `7c2019995f5b` |
| katters-structures | 2.7-mod | `Katters Structures v2.7.jar` | `41472750864a` |
| lithostitched | 2.0.4-fabric-26.3 | `lithostitched-2.0.4-fabric-26.3.jar` | `f93e848eaa72` |
| mes-moogs-end-structures | 2.1.1 | `MoogsEndStructures-universal-1.21-2.1.1.jar` | `ef5f57f0e5d0` |
| midnightlib | 1.9.3+26.3-fabric | `midnightlib-fabric-1.9.3+26.3-rc-2.jar` | `2c0e2ba8a9a2` |
| mns-moogs-nether-structures | 3.1.1 | `MoogsNetherStructures-universal-1.21-3.1.1.jar` | `78845cb7bcca` |
| moogs-structure-lib | 3.4.2-fabric-26.3 | `MoogsStructureLib-fabric-26.3-3.4.2.jar` | `c261adabf25f` |
| moogs-voyager-structures | 5.1.3 | `MoogsVoyagerStructures-universal-1.21-5.1.3.jar` | `6759fa57891f` |
| mru | 1.0.43+26.3-fabric | `mru-1.0.43+26.3-fabric.jar` | `7e58e709188c` |
| mss-moogs-soaring-structures | 2.2.0 | `MoogsSoaringStructures-universal-1.21-2.2.0.jar` | `1284c673b46a` |
| nullscape | 2.0.1+26.3 | `Nullscape_26.3_v2.0.1+26.3.jar` | `51d81f5d986d` |
| repurposed-structures-fabric | 7.8.2+26.3-fabric | `repurposed_structures-7.8.2+26.3-fabric.jar` | `ef9ecd0984f1` |
| resourceful-config | 6.0.1 | `ResourcefulConfig-6.0.1.jar` | `55c3c414ee21` |
| scalablelux | 0.3.0-alpha.0.6+26.3 | `ScalableLux-fabric-mc26.3-0.3.0-alpha.0.6-all.jar` | `d51dcbe90145` |
| snow-under-trees-remastered | 2.7.6+26.3 | `SnowUnderTrees-2.7.7+26.3.jar` | `71fd762f9897` |
| spark | 1.10.187-fabric | `spark-1.10.187-fabric.jar` | `0940b4089aff` |
| sparsestructures | 3.1.5 | `sparsestructures-fabric-26.3-3.1.5.jar` | `ef56de783f2c` |
| stellarity | 6.0.0+mod | `Stellarity-6.0.0.jar` | `1e50711590bc` |
| structory | 1.3.18+26.3 | `Structory_26.3_v1.3.18+26.3.jar` | `da82d733b6f4` |
| structory-towers | 1.0.19+26.3 | `Structory_Towers_26.3_v1.0.19+26.3.jar` | `a753d7cb8f06` |
| structure-layout-optimizer | 1.1.4+26.3-fabric | `structure_layout_optimizer-1.1.4+26.3-fabric.jar` | `ce6a03a92729` |
| tectonic | 3.0.31-fabric-26.3 | `tectonic-3.0.31-fabric-26.3.jar` | `1e0725c00c97` |
| terralith | 2.6.5+26.3 | `Terralith_26.3_v2.6.5+26.3.jar` | `725bd469bc72` |
| tidal-towns | 2.0+mod | `tidal-towns-2.0.jar` | `f48816fb1b07` |
| towns-and-towers | 1.13.12 | `t_and_t-fabric-neoforge-1.13.12.jar` | `91b1a4883406` |
| vanilla-structure-update | V2.11+mod | `vanilla-structure-update-V2.11.jar` | `30e900950b95` |
| villages-and-pillages | fabric-2.0.0+mc26.3 | `villagesandpillages-fabric-2.0.0+mc26.3.jar` | `edc1cce78cde` |
| wwoo | 3.0.1 | `wwoo-fabric-26.3-3.0.1.jar` | `b1bddb632d48` |
| zconfig | 1.0.0+26.x | `zconfig-1.0.0+26.x.jar` | `91dab4f25559` |
| zfastnoise | 1.1.1+26.3 | `zfastnoise-1.1.1+26.3.jar` | `f440a6836308` |

## Candidates

Full table: [candidates.md](candidates.md). Short version:

Built from Modrinth search (worldgen, 26.3, mod+datapack, server_side required/optional, top 50 by downloads and by follows) plus the brief's names; each jar was scanned for client assets. **Not on 26.3:** the whole YUNG's family (no YUNG's API build), Geologica, Explorify, Cave Overhaul, Noisium, Leukocyte, Streams, CliffTree (latest is 26.2). **No server-only cave pack exists on 26.3**, so the caves row is empty. **Rejected (client mod needed):** Biomes O' Plenty (+TerraBlender), Wilder Wild, Oh The Biomes We've Gone, Ecologics. **Caveat:** Dungeons and Taverns, Katters Structures and Stellarity ship custom item models; on vanilla clients those loot items show a missing model unless a server resource pack is sent. Terrain and structures from all three are vanilla blocks.

## Benchmark results

Chunks/s = Chunky chunks ÷ wall time from `chunky start` to Chunky's 'Task finished' line. Δ is relative to B0 in the same dimension. Spread = min–max over runs. mspt = mean of vanilla `/tick query` 100-tick averages sampled every 5 s; TPS = mean of spark `tps` 5 s values; CPU = process CPU time ÷ wall ÷ 4 cores. Post-CPU = process CPU in the 20 s after Chunky finished (DH LOD backlog).

| config | type | dim | runs | chunks/s (mean, min–max) | wall s | Δ vs B0 | mspt | TPS | CPU % | peak heap MB | post-CPU % | verify | spark (health / profile, run 1) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| B0 | baseline | Overworld | 3 | 67.6 (65.6–68.6) | 239 | — | 0.51 | 20.00 | 96 | 3985 | 2 |  | [h](https://spark.lucko.me/qRcRhmITIT) / [p](https://spark.lucko.me/9hAjma7VdM) |
| V0 | baseline | Overworld | 3 | 82.5 (81.0–83.3) | 196 | +22.0% | 1.20 | 20.00 | 70 | 4520 | 1 |  | [h](https://spark.lucko.me/RPHXrjBunc) / [p](https://spark.lucko.me/0llzrSxpaT) |
| V0+C2ME | baseline | Overworld | 2 | 98.4 (98.2–98.5) | 164 | +45.5% | 0.55 | 20.00 | 85 | 3912 | 1 |  | [h](https://spark.lucko.me/j564I9pQ3q) / [p](https://spark.lucko.me/i343kqj6BL) |
| continents | terrain | Overworld | 1 | 72.8 (72.8–72.8) | 222 | +7.7% | 0.54 | 19.99 | 96 | 3870 | 2 |  | [h](https://spark.lucko.me/Sede9rKThY) / [p](https://spark.lucko.me/UWSvdGBSIM) |
| geophilic | terrain | Overworld | 1 | 68.8 (68.8–68.8) | 234 | +1.8% | 0.35 | 20.00 | 95 | 3791 | 2 |  | [h](https://spark.lucko.me/t1BORE0xIE) / [p](https://spark.lucko.me/AutXBxBbQw) |
| geophilic+tectonic | terrain | Overworld | 1 | 51.3 (51.3–51.3) | 315 | -24.2% | 0.46 | 20.00 | 96 | 4153 | 3 |  | [h](https://spark.lucko.me/l4Cgol34Bq) / [p](https://spark.lucko.me/w9bCY4xZhf) |
| geophilic+terralith | terrain | Overworld | 1 | 49.4 (49.4–49.4) | 327 | -26.9% | 0.62 | 20.00 | 96 | 4139 | 3 |  | [h](https://spark.lucko.me/Xa9prqyQGT) / [p](https://spark.lucko.me/5iIEfi7mAU) |
| hybrid-beta | terrain | Overworld | 1 | 57.1 (57.1–57.1) | 282 | -15.5% | 0.43 | 20.00 | 96 | 3786 | 3 |  | [h](https://spark.lucko.me/CxNuBxL8yC) / [p](https://spark.lucko.me/Hz6lxfC9Qc) |
| tectonic | terrain | Overworld | 1 | 55.0 (55.0–55.0) | 293 | -18.7% | 0.52 | 19.99 | 96 | 4138 | 5 |  | [h](https://spark.lucko.me/Zg3b5XxTWI) / [p](https://spark.lucko.me/dsIsmkjVMw) |
| tectonic+terralith | terrain | Overworld | 1 | 44.4 (44.4–44.4) | 363 | -34.3% | 0.61 | 20.00 | 96 | 4327 | 4 |  | [h](https://spark.lucko.me/G9x3HRl9yI) / [p](https://spark.lucko.me/HCvhrbTHH7) |
| terralith | terrain | Overworld | 2 | 54.5 (54.0–55.0) | 296 | -19.4% | 0.53 | 20.00 | 96 | 4140 | 4 |  | [h](https://spark.lucko.me/SeJu6LdfJN) / [p](https://spark.lucko.me/OOlgszmUz7) |
| wwoo | terrain | Overworld | 1 | 36.7 (36.7–36.7) | 439 | -45.7% | 0.43 | 20.00 | 96 | 4156 | 3 |  | [h](https://spark.lucko.me/eMR6J8vtaA) / [p](https://spark.lucko.me/H4yZVowWHw) |
| scalablelux | perf | Overworld | 1 | 68.0 (68.0–68.0) | 237 | +0.6% | 0.61 | 19.89 | 96 | 3849 | 5 |  | [h](https://spark.lucko.me/EZhlxIh8w4) / [p](https://spark.lucko.me/fHzUI3075D) |
| structure-layout-optimizer | perf | Overworld | 1 | 64.6 (64.6–64.6) | 250 | -4.4% | 0.54 | 20.00 | 96 | 3861 | 2 |  | [h](https://spark.lucko.me/cRqNpkZ67s) / [p](https://spark.lucko.me/Rx27GA3PKu) |
| zfastnoise | perf | Overworld | 1 | 69.4 (69.4–69.4) | 232 | +2.6% | 0.59 | 20.00 | 96 | 3879 | 3 |  | [h](https://spark.lucko.me/vGjz4dJMO4) / [p](https://spark.lucko.me/F2Vq48AG8e) |
| ati-structures | structures | Overworld | 1 | 57.4 (57.4–57.4) | 281 | -15.0% | 0.64 | 20.00 | 94 | 4401 | 4 |  | [h](https://spark.lucko.me/VevJlgPZIZ) / [p](https://spark.lucko.me/G0y9SLMm4r) |
| dungeons-and-taverns | structures | Overworld | 1 | 61.7 (61.7–61.7) | 261 | -8.7% | 0.64 | 20.00 | 96 | 4439 | 3 |  | [h](https://spark.lucko.me/TSIocf3vS3) / [p](https://spark.lucko.me/DlPtWsNMT1) |
| epic-structures-villages | structures | Overworld | 1 | 65.2 (65.2–65.2) | 247 | -3.5% | 0.45 | 20.00 | 95 | 4435 | 2 |  | [h](https://spark.lucko.me/3tDgfx9Bd2) / [p](https://spark.lucko.me/olk99bap3B) |
| formations-overworld | structures | Overworld | 1 | 66.0 (66.0–66.0) | 244 | -2.4% | 0.46 | 20.00 | 97 | 4169 | 3 |  | [h](https://spark.lucko.me/hrFAQ1jtjS) / [p](https://spark.lucko.me/aVGoQjpPlU) |
| hopo-better-mineshaft | structures | Overworld | 1 | 65.0 (65.0–65.0) | 248 | -3.8% | 0.56 | 20.00 | 96 | 4234 | 2 |  | [h](https://spark.lucko.me/ywIeT5hBJu) / [p](https://spark.lucko.me/4qY4AWY2ij) |
| hopo-better-ruined-portals | structures | Overworld | 1 | 66.2 (66.2–66.2) | 244 | -2.1% | 0.55 | 20.00 | 96 | 4092 | 2 |  | [h](https://spark.lucko.me/Aa2Y6fRuC9) / [p](https://spark.lucko.me/HWZ0LFA2JY) |
| hopo-better-underwater-ruins | structures | Overworld | 1 | 66.2 (66.2–66.2) | 244 | -2.1% | 0.37 | 20.00 | 96 | 4146 | 2 |  | [h](https://spark.lucko.me/CsJB1qQHWe) / [p](https://spark.lucko.me/5U2NymAVmf) |
| katters-structures | structures | Overworld | 1 | 64.8 (64.8–64.8) | 249 | -4.1% | 0.88 | 20.00 | 97 | 4174 | 4 |  | [h](https://spark.lucko.me/r2PREG6voD) / [p](https://spark.lucko.me/FevQKzSH9r) |
| mss | structures | Overworld | 1 | 66.6 (66.6–66.6) | 242 | -1.5% | 0.46 | 20.00 | 97 | 4242 | 2 |  | [h](https://spark.lucko.me/AH9QHu5YsN) / [p](https://spark.lucko.me/YO9vjdmLWt) |
| mvs | structures | Overworld | 1 | 66.3 (66.3–66.3) | 243 | -1.9% | 0.47 | 20.00 | 96 | 4033 | 2 |  | [h](https://spark.lucko.me/gIl1qCTkei) / [p](https://spark.lucko.me/gfwUjwx9kx) |
| repurposed-structures | structures | Overworld | 1 | 66.3 (66.3–66.3) | 243 | -1.9% | 0.47 | 20.00 | 97 | 3932 | 3 |  | [h](https://spark.lucko.me/lofvalGrku) / [p](https://spark.lucko.me/lLjw7VALkF) |
| sparsestructures | structures | Overworld | 1 | 68.1 (68.1–68.1) | 237 | +0.8% | 0.46 | 20.00 | 96 | 3913 | 2 |  | [h](https://spark.lucko.me/xDpuL2ZIJd) / [p](https://spark.lucko.me/qUpbWoC45G) |
| structory | structures | Overworld | 1 | 67.7 (67.7–67.7) | 238 | +0.2% | 0.46 | 19.99 | 97 | 4221 | 2 |  | [h](https://spark.lucko.me/SlNfBUgi7h) / [p](https://spark.lucko.me/ZixUCryJwz) |
| structory-towers | structures | Overworld | 1 | 64.8 (64.8–64.8) | 249 | -4.1% | 0.55 | 20.00 | 96 | 4170 | 3 |  | [h](https://spark.lucko.me/RqvEK6hl49) / [p](https://spark.lucko.me/BXGLennB3n) |
| tidal-towns | structures | Overworld | 1 | 67.8 (67.8–67.8) | 238 | +0.3% | 0.61 | 20.00 | 96 | 4121 | 2 |  | [h](https://spark.lucko.me/yhW7U0IyBC) / [p](https://spark.lucko.me/s6YlAQUTvm) |
| towns-and-towers | structures | Overworld | 1 | 63.2 (63.2–63.2) | 255 | -6.4% | 0.45 | 20.00 | 97 | 4295 | 4 |  | [h](https://spark.lucko.me/m7hW173hx6) / [p](https://spark.lucko.me/OCMeN4cVpd) |
| vanilla-structure-update | structures | Overworld | 1 | 66.2 (66.2–66.2) | 244 | -2.1% | 0.47 | 19.99 | 96 | 4004 | 2 |  | [h](https://spark.lucko.me/F4frzzA7mI) / [p](https://spark.lucko.me/bZZkNvDfVA) |
| villages-and-pillages | structures | Overworld | 1 | 66.3 (66.3–66.3) | 243 | -2.0% | 0.48 | 20.00 | 96 | 4304 | 3 |  | [h](https://spark.lucko.me/n6pUnKgKmq) / [p](https://spark.lucko.me/oWjAmoIqvZ) |
| snow-under-trees | features | Overworld | 1 | 69.4 (69.4–69.4) | 232 | +2.7% | 0.47 | 20.00 | 97 | 3988 | 3 |  | [h](https://spark.lucko.me/4DrTxon0Ml) / [p](https://spark.lucko.me/cK34Mp9ts5) |
| BALANCED | pack | Overworld | 3 | 52.2 (51.6–52.5) | 309 | -22.8% | 0.66 | 20.00 | 97 | 4272 | 5 |  | [h](https://spark.lucko.me/i3tgJqpsIW) / [p](https://spark.lucko.me/eR9xtVyjbH) |
| BALANCED-G | pack | Overworld | 3 | 67.2 (65.1–68.2) | 240 | -0.6% | 0.54 | 20.00 | 96 | 4367 | 3 |  | [h](https://spark.lucko.me/mYWGHVhwaH) / [p](https://spark.lucko.me/9vVLrAcBYw) |
| LEAN | pack | Overworld | 3 | 65.4 (64.9–66.4) | 247 | -3.2% | 0.49 | 20.00 | 96 | 4283 | 2 |  | [h](https://spark.lucko.me/5y7nBXvUPL) / [p](https://spark.lucko.me/mu1hADMFib) |
| PRETTY | pack | Overworld | 3 | 44.4 (43.9–45.3) | 363 | -34.3% | 1.42 | 20.00 | 97 | 4524 | 4 |  | [h](https://spark.lucko.me/jXTPIUQnee) / [p](https://spark.lucko.me/tcCKrZ5O4E) |
| B0 | baseline | Nether | 3 | 93.4 (88.9–98.0) | 244 | — | 0.65 | 20.00 | 96 | 4072 | 40 |  | [h](https://spark.lucko.me/q8lhvtkfv4) / [p](https://spark.lucko.me/ZAfewTjyCq) |
| amethyst-nether | nether | Nether | 1 | 82.7 (82.7–82.7) | 276 | -11.5% | 0.53 | 19.99 | 96 | 4038 | 4 |  | [h](https://spark.lucko.me/Qx7Xsor13V) / [p](https://spark.lucko.me/7GNKAqB2Mh) |
| amplified-nether | nether | Nether | 1 | 51.4 (51.4–51.4) | 444 | -45.0% | 0.41 | 20.00 | 96 | 3976 | 10 |  | [h](https://spark.lucko.me/MOEWaFUg22) / [p](https://spark.lucko.me/GUMLs0jA3L) |
| formations-nether | nether | Nether | 1 | 85.4 (85.4–85.4) | 267 | -8.6% | 0.48 | 20.00 | 96 | 4323 | 39 |  | [h](https://spark.lucko.me/0t9f3YUnfs) / [p](https://spark.lucko.me/unTpKg2kJu) |
| incendium | nether | Nether | 1 | 37.7 (37.7–37.7) | 604 | -59.6% | 0.77 | 20.00 | 96 | 5551 | 2 |  | [h](https://spark.lucko.me/alv645aM4I) / [p](https://spark.lucko.me/vuAghGQun4) |
| mns | nether | Nether | 1 | 86.0 (86.0–86.0) | 265 | -8.0% | 0.46 | 20.00 | 96 | 4488 | 42 |  | [h](https://spark.lucko.me/yqgy1sfYKK) / [p](https://spark.lucko.me/XCHBohI32T) |
| BALANCED | pack | Nether | 1 | 88.4 (88.4–88.4) | 258 | -5.4% | 0.55 | 19.99 | 97 | 4155 | 2 |  | [h](https://spark.lucko.me/fdzfp6avfD) / [p](https://spark.lucko.me/Lh0Chl9yyg) |
| BALANCED-G | pack | Nether | 1 | 84.5 (84.5–84.5) | 270 | -9.5% | 0.55 | 20.00 | 95 | 4189 | 3 |  | [h](https://spark.lucko.me/bj4Gq0t0Mv) / [p](https://spark.lucko.me/ys48LnkdWL) |
| LEAN | pack | Nether | 1 | 91.8 (91.8–91.8) | 248 | -1.8% | 0.62 | 20.00 | 96 | 4436 | 41 |  | [h](https://spark.lucko.me/W2I6ACXHSV) / [p](https://spark.lucko.me/pxxHTbS7BW) |
| PRETTY | pack | Nether | 1 | 38.1 (38.1–38.1) | 598 | -59.2% | 1.23 | 20.00 | 96 | 4518 | 5 |  | [h](https://spark.lucko.me/JVtFXV9uec) / [p](https://spark.lucko.me/9bfuFBbtNX) |
| B0 | baseline | End | 3 | 324.4 (317.1–332.7) | 237 | — | 0.49 | 20.00 | 95 | 3922 | 3 |  | [h](https://spark.lucko.me/WKn6cFutaE) / [p](https://spark.lucko.me/nq6a3cHmlB) |
| endercon | end | End | 1 | 131.6 (131.6–131.6) | 583 | -59.4% | 0.33 | 20.00 | 95 | 4088 | 4 |  | [h](https://spark.lucko.me/MRpo8GQ3nE) / [p](https://spark.lucko.me/6hY08UfvMh) |
| mes | end | End | 1 | 316.1 (316.1–316.1) | 243 | -2.5% | 0.54 | 20.00 | 95 | 4131 | 2 |  | [h](https://spark.lucko.me/88zgTXrUyh) / [p](https://spark.lucko.me/2lvhA6esWG) |
| nullscape | end | End | 1 | 100.6 (100.6–100.6) | 763 | -69.0% | 0.71 | 20.00 | 96 | 4263 | 4 |  | [h](https://spark.lucko.me/0hBC7NMerr) / [p](https://spark.lucko.me/tvk9OrOKpx) |
| stellarity | end | End | 1 | 135.2 (135.2–135.2) | 567 | -58.3% | 1.47 | 20.00 | 96 | 4674 | 4 |  | [h](https://spark.lucko.me/McpNZXTdfT) / [p](https://spark.lucko.me/bTWk11DLBb) |
| BALANCED | pack | End | 1 | 103.7 (103.7–103.7) | 740 | -68.0% | 0.53 | 20.00 | 96 | 4303 | 4 |  | [h](https://spark.lucko.me/vUSMP2ZmYF) / [p](https://spark.lucko.me/d3gOHU0Vh9) |
| BALANCED-G | pack | End | 1 | 101.5 (101.5–101.5) | 756 | -68.7% | 0.53 | 19.99 | 97 | 4553 | 4 |  | [h](https://spark.lucko.me/dsm9bXzGVw) / [p](https://spark.lucko.me/GCbCKohNLd) |
| LEAN | pack | End | 1 | 332.2 (332.2–332.2) | 231 | +2.4% | 0.54 | 20.00 | 96 | 4195 | 2 |  | [h](https://spark.lucko.me/yhPC3rWiwe) / [p](https://spark.lucko.me/n45B5CnpJ4) |
| PRETTY | pack | End | 1 | 100.7 (100.7–100.7) | 762 | -69.0% | 0.85 | 20.00 | 97 | 4423 | 19 |  | [h](https://spark.lucko.me/5zKnRsjlJa) / [p](https://spark.lucko.me/LQ8x5ixGBS) |

Failed runs (not in the table):

- V0+DH Overworld run 1: start_failed — server failed to start
- V0+DH Overworld run 2: start_failed — server failed to start
- clifftree-nolitho Overworld run 1: start_failed — server failed to start
- clifftree Overworld run 1: start_failed — server failed to start

Radius: Overworld Overworld R=1000 blocks (16129 chunks), Nether R=1200 blocks (22801 chunks), End R=2200 blocks (76729 chunks). Seed: 20251006.



## Screenshots

Not done. Method (a) was set up and worked: a real vanilla 26.3 client (downloaded from Mojang launcher meta) under Xvfb. 26.3's OpenGL backend fails on Xvfb ('Couldn't find matching GLX visual'), but its new Vulkan backend runs on Mesa lavapipe after installing `mesa-vulkan-drivers`. One test frame at spawn rendered correctly in about 2.5 min (not committed: it was night, because 26.3 removed the doDaylightCycle gamerule; `shot.py` now resets `time set 6000` before each capture). Views are fixed in `shots/views.json` (5 Overworld, 2 Nether, 2 End). `scripts/shot.py CONFIG` produces the grid from the kept worlds.



## The three packs

### LEAN — 65.4 chunks/s, -3.2% vs B0

Geophilic (looks, ~0 cost) + Structory + Moog's Nether/End structures + ScalableLux. Measured −3.2% (inside B0 noise).

Mods on top of B0 (Fabric API, C2ME, Distant Horizons, spark, Chunky): geophilic 3.7, structory 1.3.18+26.3, scalablelux 0.3.0-alpha.0.6+26.3, mns-moogs-nether-structures 3.1.1, moogs-structure-lib 3.4.2-fabric-26.3, mes-moogs-end-structures 2.1.1

### BALANCED-G — 67.2 chunks/s, -0.6% vs B0

Recommended. Geophilic + four structure packs + ScalableLux/SLO + Amethyst Nether + Nullscape. Measured −0.6% Overworld, −9.5% Nether, −68.7% End.

Mods on top of B0 (Fabric API, C2ME, Distant Horizons, spark, Chunky): geophilic 3.7, structory 1.3.18+26.3, structory-towers 1.0.19+26.3, towns-and-towers 1.13.12, cristel-lib fabric-26.3-3.1.13, cloth-config 26.3.159+fabric, dungeons-and-taverns 6.0.2+mod, scalablelux 0.3.0-alpha.0.6+26.3, structure-layout-optimizer 1.1.4+26.3-fabric, resourceful-config 6.0.1, amethyst-nether v4.4+mod, nullscape 2.0.1+26.3

### BALANCED — 52.2 chunks/s, -22.8% vs B0

Terralith-based attempt at the 15% budget; came in at −22.8%, so it misses the target.

Mods on top of B0 (Fabric API, C2ME, Distant Horizons, spark, Chunky): terralith 2.6.5+26.3, lithostitched 2.0.4-fabric-26.3, structory 1.3.18+26.3, structory-towers 1.0.19+26.3, scalablelux 0.3.0-alpha.0.6+26.3, structure-layout-optimizer 1.1.4+26.3-fabric, resourceful-config 6.0.1, amethyst-nether v4.4+mod, nullscape 2.0.1+26.3

### PRETTY — 44.4 chunks/s, -34.3% vs B0

Tectonic + Terralith + Geophilic + D&T + Towns and Towers + Structory (+Towers) + Incendium + Nullscape. Costs −34.3% Overworld, −59.2% Nether, −69.0% End.

Mods on top of B0 (Fabric API, C2ME, Distant Horizons, spark, Chunky): tectonic 3.0.31-fabric-26.3, terralith 2.6.5+26.3, geophilic 3.7, lithostitched 2.0.4-fabric-26.3, dungeons-and-taverns 6.0.2+mod, towns-and-towers 1.13.12, cristel-lib fabric-26.3-3.1.13, cloth-config 26.3.159+fabric, structory 1.3.18+26.3, structory-towers 1.0.19+26.3, scalablelux 0.3.0-alpha.0.6+26.3, structure-layout-optimizer 1.1.4+26.3-fabric, resourceful-config 6.0.1, incendium 5.5.3+26.3, nullscape 2.0.1+26.3

## Recommendation

**Recommended S6 base: BALANCED-G** = Geophilic + Dungeons and Taverns + Towns and Towers + Structory + Structory Towers + ScalableLux + structure-layout-optimizer, with Amethyst Nether and Nullscape. In the Overworld it measured −0.6% vs B0 (65.1–68.2 vs 65.6–68.6), i.e. no measurable cost, while it adds Geophilic's biome rework and four structure packs. All are server-only (D&T has the item-model caveat). The Nether costs about −10% (Amethyst Nether). The End costs about −69% (Nullscape), but the End baseline is about 5× faster than the Overworld, so Nullscape End still pregens faster (≈100 chunks/s) than the B0 Overworld. If the team wants big mountains, the next step up is Tectonic (−19%) or Terralith (−19%). Their stack is −34%, which is the PRETTY cost. **Do not use WWOO (−46%) or Incendium (−60% in the Nether) unless the team accepts that.** One finding about the baseline itself: plain vanilla (V0) is 22% faster than B0, and V0+C2ME is 46% faster. Distant Horizons' server-side LOD building is what costs about 31% vs C2ME alone. If DH LODs are not needed for S6, dropping DH is the single largest speed-up measured here.

## Geophilic

Geophilic 3.7 (the mod the team called 'Geolithic'): alone **+1.8%** vs B0 (68.8 chunks/s, one run, within noise). It changes features and vegetation inside vanilla biome IDs, so it adds no new biome names. Surface heights at the grid differ from B0 at 35% of points, against about 15% between V0 and B0. Layered: **Geophilic+Tectonic −24.2%** (Tectonic alone −18.7%); **Geophilic+Terralith −26.9%** (Terralith alone −19.4%). So Geophilic adds about 5–8 points on top of either. Looks were not compared, because no screenshots were taken.

## What was not tested, and open problems

- **Screenshots not taken** (run stopped by the owner). The pipeline works (vanilla client, Vulkan on lavapipe); rerun `scripts/shot.py` for each config with kept worlds on a box that still has them.
- Kept worlds were not committed (by design) and die with this sandbox.
- Single-mod configs have 1 run, so differences under about ±4% are noise.
- **V0+DH** (DH 3.3.4 without C2ME) hangs forever at 'Selecting global world spawn'; the thread dump is in `raw/notes/`. B0 is not affected (DH+C2ME starts fine).
- **CliffTree** (last season): no 26.3 build. The 26.2 datapack (3.3) fails registry loading on 26.3, with and without Lithostitched: it references vanilla density functions such as `minecraft:continentalness` and `minecraft:overworld_amplified/depth` that do not exist on 26.3. No comparison possible until CliffTree ships 26.3.
- The BALANCED (Terralith) pack missed the ≤15% target (−23%); BALANCED-G was added and meets it.
- Structure-start counts inside the pregen square were not completed (stopped), so structure packs are verified by `locate` (they register and are found) but not counted in-area.
- Spark health links show TPS 20 / mspt < 1 throughout: with no players the tick loop is idle, and generation cost shows in chunks/s and CPU, not mspt.
- The DH Nether post-pregen CPU backlog (~40%) and the effect of LOD settings were not explored.

