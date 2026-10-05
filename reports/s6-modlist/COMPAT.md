# METAcraft Season 6 modlist compatibility check

Run 2026-10-05, 11:27–14:05 UTC, in a cloud sandbox. Scripts are in `scripts/s6-compat/`. Raw logs and outputs are in `reports/s6-modlist/raw/`. Everything below was measured in this run; anything not tested says so.

## Summary

* **Blocker (METAmods): clicking any entry on the Booklet `/guide` index crashes the whole server** with `IllegalStateException: Registry is already frozen (trying to add key metacraft:message_handlers)`. metacraft-lib's `CustomMessageRegistry` builds its registry in a static initializer, and its `init()` is never called. Only metacraft-revival touched that class during mod init. With revival removed (commit 82c6d809), the class first loads on a player's first custom click action, after registries are frozen. Reproduced with a vanilla 26.3 client on the merged build. A one-line fix (`raw/../proposed-fix-custom-message-registry.patch`: call `CustomMessageRegistry.init()` in `METAcraftLib.onInitialize`) made every guide page open without a crash. The fix was tested locally only and is not pushed anywhere.
* **The ovvar guide is listed twice on the Booklet index. Confirmed in game** (`booklet-index-duplicate-ovvar.png`): two "How to ovvar" entries plus "Decorating".
* **squaremap causes 2–5 s server-thread stalls during pregen.** About every 19 s the main thread falls 2–5 s behind (3–5 "Can't keep up" per 4,225-chunk pregen), and 1-min TPS drops to about 16.5–17.6. Bisect: B0 + squaremap alone reproduces it, B0 + every other S6 mod does not, and S6 with BlueMap instead of squaremap has no stalls.
* Every variant boots cleanly: B0, S6-vanilla, S6-heavy, S6+Carpet, S6 with BlueMap, S6 without AC. No mixin conflicts or mixin apply failures were reported. A vanilla 26.3 client joins S6-vanilla, S6-heavy and S6+Carpet.
* Alternate Current: the four rigs (piston door, observer clock, comparator subtraction, quasi-connectivity), plus a dust-tapped piston line, behave tick for tick the same with AC, without AC, and with AC + Carpet.
* PolyDecorations fork (built for 26.3-rc-1) loads and works on 26.3 final. With the S6 config, only 37 PolyDecorations recipes exist (canvas, mailbox, sign post, rope, hammer, trowel), against 223 with neither the config nor the S6 datapack.

## Machine

| | |
|---|---|
| CPU | 4 vCPU, Intel Xeon Processor @ 2.10 GHz, 1 thread per core |
| RAM | 15 GiB, no swap |
| Kernel | Linux 6.18.44-fc-v70 x86_64 |
| Java | Temurin 25.0.4.1+1 (installed for this run) |
| Server JVM | `-Xms8G -Xmx8G`, default G1, no other flags |
| Disk | 30 GB free at start |

All five required hosts answered: piston-meta.mojang.com, maven.fabricmc.net, api.modrinth.com, cdn.modrinth.com and github.com (`preflight.txt`). The first Gradle run got HTTP 429 from Maven Central, and a retry worked. The noise caveat for timing numbers is under Overhead.

## Exact versions

Minecraft 26.3 (protocol 777, data pack 121.0), Fabric loader 0.19.5, Fabric API 0.161.0+26.3, Fabric server launcher from installer 1.1.2.

| Mod (Modrinth slug) | Version loaded | File |
|---|---|---|
| fabric-api | 0.161.0+26.3 | fabric-api-0.161.0+26.3.jar |
| c2me-fabric | 0.4.2-alpha.0.89+26.3 | c2me-fabric-mc26.3-0.4.2-alpha.0.89.jar |
| distanthorizons | 3.3.4 | DistantHorizons-3.3.4-26.3-fabric-neoforge.jar |
| spark | 1.10.187 | spark-1.10.187-fabric.jar |
| chunky | 1.5.3 | Chunky-Fabric-1.5.3.jar |
| polymer (bundled) | 0.18.2+26.3 | polymer-bundled-0.18.2+26.3.jar |
| alternate-current | 1.9.0 | alternate-current-mc26.3-1.9.0.jar |
| lithium | 0.26.2+mc26.3 | lithium-fabric-0.26.2+mc26.3.jar |
| ferrite-core | 9.0.0 | ferritecore-9.0.0-fabric.jar |
| krypton | 0.3.2 | krypton-0.3.2.jar |
| scalablelux | 0.3.0-alpha.0.6+26.3 | ScalableLux-fabric-mc26.3-0.3.0-alpha.0.6-all.jar |
| no-chat-reports | 26.3-v2.21.0 | NoChatReports-FABRIC-26.3-v2.21.0.jar |
| ledger | 1.3.25 (+ fabric-language-kotlin 1.14.1+kotlin.2.4.20) | ledger-1.3.25.jar |
| vanish | 1.6.16+26.3 | vanish-1.6.16+26.3.jar |
| luckperms | 5.5.85 | LuckPerms-Fabric-5.5.85.jar |
| simple-voice-chat | 2.6.24+26.3 | voicechat-fabric-2.6.24+26.3.jar |
| squaremap | 1.4.0 | squaremap-fabric-mc26.3-1.4.0.jar |
| bluemap | 5.28 | bluemap-5.28-fabric.jar |
| worldedit | 7.4.6-beta-02+2c90a77a1 | worldedit-mod-7.4.6-beta-02.jar |
| filament | 1.8.4+26.3 | filament-1.8.4+26.3.jar |
| booklet | 0.4.0+26.3 | booklet-0.4.0+26.3.jar |
| carpet | 26.3+v260915 | fabric-carpet-26.3+v260915.jar |
| fabrictailor | 2.11.0 (reports `${version}`) | fabrictailor-2.11.0.jar |
| forgiving-void | 26.3.0.2 (+ balm 26.3.0.3) | forgivingvoid-fabric-26.3-26.3.0.2.jar |
| meowanti-xray | 1.7.0 | meowantixray-fabric-1.7.0.jar |
| terralith | 2.6.5 (+ lithostitched 2.0.4) | Terralith_26.3_v2.6.5+26.3.jar |
| tectonic | 3.0.31 | tectonic-3.0.31-fabric-26.3.jar |
| dungeons-and-taverns | 6.0.2 | dungeons-and-taverns-6.0.2.jar |
| incendium | 5.5.3+26.3 (alpha) | Incendium_26.3_v5.5.3+26.3.jar |
| nullscape | 2.0.1+26.3 | Nullscape_26.3_v2.0.1+26.3.jar |
| Danse fork | 2.6.0-metacraft.2+26.3 (GitHub release v2.6.0-metacraft.2) | danse-2.6.0-metacraft.2+26.3.jar |
| PolyDecorations fork | 0.13.1+26.3-rc-1.metacraft.1 (GitHub release) | polydecorations-0.13.1+26.3-rc-1.metacraft.1.jar |
| METAmods dist | metacraft 1.0.0, sha1 f4421fd2614d9c4167f2782d2b601fc90ae74e16 | built here, see below |

Nested libraries as loaded: polymer-core 0.18.2+26.3, sgui 2.2.0+26.3, placeholder-api 3.2.0+26.3, server_translations_api 3.2.0+26.3, map-canvas-api 0.9.1+26.2, factorytools 0.12.1+26.3, dialogutils 1.8.4+26.1 (inside Danse, built for 26.1, loads fine), mixinsquared 0.3.7-beta.3. Full trees are in `raw/<variant>/modlist.txt`. Modrinth ids, hashes and URLs are in `resolved.json` and `resolved-extra.json`.

Known missing on 26.3, confirmed against the Modrinth API: Noisium and Leukocyte have no 26.3 Fabric build. `squaremap-banners` and `antixray` return 404 (no such slug). Alternatives found: meowanti-xray 1.7.0 (used here) and minertrack 2.1.2.0 (downloaded, not tested).

## METAmods build

* Scratch branch: local `scratch-s6-merge` = `origin/ovvar-danse-fork` (57beb114) merged with `origin/metacraft-booklet` (af121822). It was never pushed.
* Merge: the only conflict was in `gradle.properties`, where both sides added `booklet_version=0.4.0+26.3`. I kept the booklet side's block (booklet, server_translations, polydecorations, simple_image_renderer, map_canvas_api). `settings.gradle` and the root `build.gradle` merged without conflict.
* `./gradlew runDatagen` passed. `./gradlew build` **fails only on `:mods:ovvar:checkstyleMain`**: `DanseModels.java:137 Indent must use tab characters`, a tab-plus-space alignment that is also on `origin/ovvar-danse-fork`. With `--continue`, every other task passes, including the game tests. The dist jar is produced.
* Repo pins differ from the servers: the repo pins fabric_api 0.160.5+26.3 and polymer 0.18.0+26.3-rc-1, while the servers ran 0.161.0+26.3 and 0.18.2+26.3. No problems were seen from this.
* Dist jar contents: metacraft-booklet, metacraft-bundles, metacraft-core, metacraft-cutscenes, metacraft-lib, metacraft-minigame-util, metacraft-moderation, metacraft-pause, metacraft-player-specific-scoreboards, metacraft-resource-packs, metacraft-zones, moredyes, ovvar, point-system, rivals-paint.
* Asked-for modules: **ovvar, moredyes, metacraft-moderation and metacraft-booklet are in the jar. Missing: portal-blocker, portal-opening, better-pets, metacraft-revival, metacraft-relay.** None of the five is in `settings.gradle` on either branch. Commit 82c6d809 "Disabled a bunch of modules" removed them on purpose. I did not re-add or port them.
  * What S6 loses: **portal-opening and portal-blocker carry the planned nether event, so S6 has no code for it.** There is also no revival (death/revive dialogs), no relay and no better-pets.
  * Revival's removal also caused the Booklet crash above, because it was the only thing that initialised `CustomMessageRegistry` at startup.

## Mod table

"Boots" means it loaded in every variant that contains it. Issues are what this run observed.

| Mod | 26.3 build? | Boots? | Issues seen |
|---|---|---|---|
| Fabric API 0.161.0 | yes | yes | – |
| C2ME | yes (alpha) | yes | disables its own `chunk_serializer` rewrite mixin (also in B0) |
| Distant Horizons 3.3.4 | yes | yes | **ERROR once per boot in every variant, including B0:** "Failed to invoke chunk load event … Chunky is not loaded" (DH's Chunky hook runs before Chunky has loaded); G1 warning |
| spark | yes | yes | – |
| Chunky | yes | yes | see DH |
| METAmods dist | built here | yes | **crash on first custom click action** (Booklet navigation); five modules missing (above) |
| Danse fork | yes (GitHub) | yes | ovvar logs "Danse is here: gestures will wear the ovve". Gestures not tested in game |
| PolyDecorations fork | built for 26.3-rc-1 | **yes, on 26.3 final** | WARN "Recipe polydecorations:canvas_clone can't be placed due to empty ingredients and will be ignored" (with and without the S6 config) |
| Polymer 0.18.2 | yes | yes | auto-host is off by default, so without it vanilla clients get no pack (see check 2) |
| Alternate Current | yes | yes | none; `/alternatecurrent` reports enabled |
| Lithium | yes | yes | force-enables its WorldEdit compat mixin (expected) |
| FerriteCore | yes | yes | – |
| Krypton | yes | yes | – |
| ScalableLux | yes (alpha) | yes | – |
| No Chat Reports | yes | yes | – |
| Ledger | yes | yes | – |
| Vanish | yes | yes | – |
| LuckPerms | yes | yes | – |
| Simple Voice Chat | yes (beta) | yes | voice server starts on UDP 24454; voice itself not tested |
| squaremap | yes | yes | web UI answers 200; **main-thread stalls during pregen** (Overhead) |
| BlueMap | yes | yes | needs `accept-download: true` in `config/bluemap/core.conf`, then renders and serves on :8100; no stalls during pregen |
| WorldEdit | yes (beta) | yes | `//wand` registered; not exercised |
| Filament | yes | yes | – |
| Booklet | yes | yes | see check 5 |
| Carpet | yes | yes | none (see verdict) |
| FabricTailor | yes | yes | WARN: version reported as `${version}` (unexpanded in the jar) |
| forgiving-void | yes | yes | – |
| meowanti-xray (AntiXray alternative) | yes | yes | not checked for effectiveness; loads, mode 2 by default |
| minertrack | yes | not tested | – |
| Terralith | yes | yes (S6-heavy) | – |
| Tectonic | yes | yes (S6-heavy) | – |
| Dungeons and Taverns | yes | yes (S6-heavy) | 3× per pregen: WARN "Couldn't find template pool reference: minecraft:minecraft_empty" from c2me workers (source mod not identified) |
| Incendium | yes (alpha) | yes (S6-heavy) | – |
| Nullscape | yes | yes (S6-heavy) | – |
| Noisium, Leukocyte, Squaremap Banners, AntiXray | **no** | – | – |

## Check 1: boots

Each variant was booted with `scripts/s6-compat/bootcheck.sh` on its own fresh world with seed 6262026. Errors and warnings are in `raw/<variant>/`.

| Variant | Mods loaded (incl. nested) | `Done (…)` | Errors | Notable warnings |
|---|---|---|---|---|
| B0 | 74 | 3.3 s | DH→Chunky "not loaded" | none |
| S6-vanilla | 197 | 3.7 s | DH→Chunky only | fabrictailor `${version}`, polydecorations canvas_clone |
| S6-heavy | – | 11.7 s | DH→Chunky only | same + spark "Timed out waiting for world statistics" |
| S6-vanilla + Carpet | – | 4.1 s | DH→Chunky only | same as S6-vanilla |
| S6-vanilla, BlueMap instead of squaremap | – | 4.5 s | DH→Chunky; one `api.minecraftservices.com` public-key read timeout (network) | BlueMap missing resources until download is accepted |
| S6-vanilla without AC | – | 3.7 s | DH→Chunky only | same as S6-vanilla |

**No mixin conflict, `@Overwrite` clash or mixin apply failure was reported in any variant.**

## Check 2: vanilla client join

* mineflayer and minecraft-protocol **can't be used**: minecraft-data 3.x on npm supports only up to 26.1.
* A **real vanilla 26.3 client works headless**. I downloaded it directly from Mojang (`getclient.py`; portablemc stalled here) and ran it under Xvfb. OpenGL failed ("Couldn't find matching GLX visual"), and the client fell back to **Vulkan on Mesa lavapipe**, which rendered. Login was offline mode with the username Bot1.
* It joined **S6-vanilla, S6-heavy and S6-vanilla + Carpet**, and patched S6 servers. Screenshots: `client-joins-s6h-carpet.png`, `booklet-*.png`. B0 and the BlueMap variant were not joined with a client.
* Two things to set on the real server:
  * 26.3 writes `white-list=true` into a new `server.properties`, B0 included. The first join was refused with "not white-listed".
  * Polymer auto-host defaults to `enabled: false`, so the vanilla client got no resource pack. With auto-host on, the client gets the "requires a custom resource pack" prompt, loads the 4.3 MB generated pack and joins.
* `pause-when-empty-seconds=60` is the 26.3 default. I set it to -1 for all tests so empty servers keep ticking.

## Check 3: Alternate Current

The rigs are built by the datapack from `make_acpack.py` at y=200 on a stone platform in force-loaded chunks. Each of 80 ticks is recorded with `say`, and `compare_ac.py` diffs the servers. Logs: `raw/ac/`.

* A: 3-piston door (dust line into a stone block that powers 3 sticky pistons), on at T1 and off at T41.
* E: a 15-long dust line with sticky pistons tapped by dust branches at x=4 and x=9 and at its end.
* B: observer clock (two facing observers placed at T1).
* C: five comparators in subtract mode with rear 15 and side 15, 12, 8, 4, 1.
* Q1: quasi-connectivity. A redstone block appears diagonally above a sticky piston at T1, a block update next to the piston follows at T21, the power is removed at T41 and the update block at T61.
* Q2: quasi-connectivity through dust on glass pointing into the space above a piston.

| | S6 (AC on) | S6 without AC | S6 + Carpet (AC on) |
|---|---|---|---|
| Door A and line E | all pistons extend at T2, all retract at T42 | same | same |
| Observer clock | pulse pattern 10,10,01,01,00,00 (6-tick period) | same | same |
| Comparator outputs | 0, 3, 7, 11, 14 (= 15 − side, correct) | same | same |
| QC with redstone block (Q1) | stays retracted (BUD) until the T21 update, extends T22, retracts T42 | same | same |
| QC through dust (Q2) | extends at T2 (dust updates reach the diagonal piston) | same | same |
| **Ticks differing from S6** | – | **0 of 80** | **0 of 80** |

`/alternatecurrent` reports "Alternate Current is currently enabled" on S6-heavy and S6+Carpet. Limitation: these rigs are simple. Contraptions that depend on subtle dust update order (some 0-tick or locational builds) were not tested, and AC is known to differ from vanilla there.

## Check 4: ovvar and Danse

* ovvar loads ("6 chapter(s), 18 patch(es)", wardrobe glyphs) and finds Danse ("Danse is here: gestures will wear the ovve").
* `/ovvar` registers reload, guide, look, stash, patch give, give, patches, showcase, stands, minigame, stitch, aimlog and store.
* `/danse source` answers: "danse (Metacraft fork, AGPL-3.0) — source: https://github.com/Froosty11/danse".
* `/ovvar guide` opens ovvar's own guide in game (`ovvar-guide-command.png`).
* Not tested: Danse gestures and ovve sewing in game.

## Check 5: Booklet and PolyDecorations

* `/guide` is registered for everyone (from the console it says "A player is required").
* **Unpatched build: the first click on any `/guide` index entry crashes the server.** Crash report: `raw/crash/crash-2026-10-05_12.13.54-server.txt`. Stack: `ServerCommonPacketListenerImpl.handleCustomClickAction` → metacraft-lib's mixin `wrapWithCondition$…$handleCustomClickAction` → `CustomMessageRegistry.<clinit>` → `FabricRegistryBuilder.buildAndRegister` → "Registry is already frozen". From the stack, any custom click action will do this, not only Booklet's. I only reproduced it with Booklet.
* With the one-line fix (local test build only), all of this works in game (`booklet-chapters-patched.png`):
  * Index: **"How to ovvar", "How to ovvar", "Decorating"**, so the duplicate is confirmed.
  * First entry: ovvar's own `ovvar:guide`, a text intro with four chapter links (The ovve, Patches, Sewing, Wardrobe and stash). `/ovvar guide` opens this same page.
  * Second entry: metacraft-booklet's `metacraft:ovvar`, with the ovve hero image beside the text.
  * Decorating: the decorating chapter, with canvas, mailboxes and so on, plus an image.
* PolyDecorations recipes. Two independent mechanisms cut the set: the fork's config (`polydecorations.json` from the booklet branch) and metacraft-booklet's built-in datapack `metacraft-booklet:polydecorations_s6`, which is enabled by default. "Total recipes" is what `/recipe give Bot1 *` unlocked after `/recipe take Bot1 *`. Lists are in `raw/polydecorations/`.

| polydecorations.json | polydecorations_s6 datapack | total recipes | PolyDecorations recipes present (of 226 recipe files in the jar) |
|---|---|---|---|
| S6 config | on | 1821 | 37 |
| S6 config | off | 1821 | 37 |
| default (all features true) | on | 1821 | 37 |
| default | off | 2007 | 223 |

The 37 recipes cover: canvas (+cut, dye, glowing, uncut, undye, unglowing, waxing), 13 mailboxes, 13 sign posts, rope, hammer and trowel. Recipes outside the S6 set are gone, and either mechanism alone is enough.

## Check 6: C2ME + Lithium + ScalableLux + DH pregen

Chunky radius 500 blocks around 0,0, 4,225 chunks, DH on default config (server-only, builds LODs from loaded chunks).

* **0 ERROR or exception lines during pregen** on B0 (×2), S6-vanilla (×3), S6-heavy (×2) and S6 with BlueMap.
* Warnings during pregen:
  * "Can't keep up" stalls on all S6 runs except the BlueMap one (caused by squaremap, see below).
  * 3 "Couldn't find template pool reference: minecraft:minecraft_empty" on S6-heavy.
* DH LOD database after pregen: 21 MB (vanilla terrain) and 14 MB (S6-heavy).

## Check 7: overhead (spark)

4 vCPU VM, 8 GB heap. Same seed, a fresh world per run, no players. Idle: 90 s settle, then `spark health`, `spark tps` and a 60 s server-thread profile. Pregen: an all-threads profile across the whole Chunky run. **Run-to-run noise is large.** S6-vanilla took 78, 112 and 81 s. The 112 s run had GC logging on; its longest GC pause was 0.63 s, with 2 full GCs. Treat differences under about 15 % as noise.

| Run | Boot `Done` | Idle MSPT 1 m (min/med/p95/max) | Idle CPU (process) | Pregen 4,225 ch | Stalls ("Can't keep up") in pregen | TPS 1 m at end of pregen | Pregen MSPT 1 m (med/p95/max) | spark links (health / idle profile / pregen profile) |
|---|---|---|---|---|---|---|---|---|
| B0 #2 | 3.3 s | 0.1/0.2/0.2/20.6 | 2–4 % | 71 s | 0 | 20.0 | 0.2/0.8/21.0 | [health](https://spark.lucko.me/grARrKaYJr) / [idle](https://spark.lucko.me/RjEzUXPR8k) / [pregen](https://spark.lucko.me/J5jHYrQRcs) |
| B0 #3 | 3.3 s | 0.1/0.2/0.3/21.4 | – | 76 s | 0 | 20.0 | 0.2/0.8/14.7 | [health](https://spark.lucko.me/QFIPQbFGU7) / [idle](https://spark.lucko.me/a13dm84O3G) / [pregen](https://spark.lucko.me/Aef7vCERzP) |
| S6-vanilla #1 | 5.3 s | 0.2/0.4/0.6/18.7 | 2–4 % | 78 s | 4 (12.2 s behind in total) | 16.6 | 0.3/2.1/24.7 | [health](https://spark.lucko.me/wbAp80kdVb) / [idle](https://spark.lucko.me/KRAvs6q3RH) / [pregen](https://spark.lucko.me/9CT5JHHuoK) |
| S6-vanilla #2 (GC log) | 3.5 s | – | – | 112 s | 5 (20.8 s) | – | – | [health](https://spark.lucko.me/LBJMMq5ieV) / [idle](https://spark.lucko.me/X26JUYPjAf) / [pregen](https://spark.lucko.me/WsxXk1Ot7l) |
| S6-vanilla #3 | – | 0.2/0.4/0.6/23.7 | – | 81 s | 4 (13.0 s) | 16.5 | 0.3/2.2/74.0 | [health](https://spark.lucko.me/1W3tVIuguP) / [idle](https://spark.lucko.me/LSvAnMuVRP) / [pregen](https://spark.lucko.me/tg3fOMC18r) |
| S6-heavy #1 | 8.8 s | 0.6/0.8/1.2/4.6 | 2–4 % | 82 s | 3 (7.6 s) | 17.6 | 0.6/3.4/13.1 | [health](https://spark.lucko.me/zS6TZZ5Ajd) / [idle](https://spark.lucko.me/m3X1P0YdMz) / [pregen](https://spark.lucko.me/IW2x6gRNjt) |
| S6-heavy #2 | – | 0.6/0.8/1.1/5.0 | – | 87 s | 4 (11.9 s) | 16.7 | 0.6/3.7/44.2 | [health](https://spark.lucko.me/4AAkrwjA5A) / [idle](https://spark.lucko.me/CrTe61m2Or) / [pregen](https://spark.lucko.me/yJHqgikpMr) |
| S6-vanilla with BlueMap instead of squaremap | – | – | – | 86 s | **0** | – | – | [health](https://spark.lucko.me/3m2DiaZVpG) / [idle](https://spark.lucko.me/WwwC0gExRZ) / [pregen](https://spark.lucko.me/MGmQEyUjrf) |

What this shows:

* **Idle cost.** S6-vanilla's median tick is about 0.4 ms against 0.2 ms for B0, and S6-heavy's is about 0.8 ms. All are far below the 50 ms budget, and idle CPU is a few percent in every case.
* **Pregen throughput.** The full modlist costs roughly 5–20 % more wall time than B0 (71–76 s against 78–87 s). Given the noise, I would not claim a more precise figure.
* **Main-thread stalls.** The real cost during pregen is 2–8 s stalls about every 19 s on S6. Tick durations themselves stay short (p95 ≤ 3.7 ms), so the time is lost outside measured ticks.

Pregen stall bisect (QUICK runs, no idle profile; outputs in `raw/perf/perf-bis*.txt`):

| Mods on top of B0 | Pregen | Stalls |
|---|---|---|
| Lithium, FerriteCore, Krypton, ScalableLux, AC | 70 s | 0 |
| All of S6 except those five | 86 s | 4 |
| squaremap, meowanti-xray, Ledger, WorldEdit, LuckPerms | 85 s | 4 |
| METAmods, Polymer, Danse, PolyDecorations, Booklet, Filament, Vanish, Voice Chat, FabricTailor, forgiving-void, NCR | 75 s | 0 |
| **squaremap only** | 87 s | **4 (2.0, 3.9, 4.7, 2.0 s)** ([pregen profile](https://spark.lucko.me/YFfodQ59KH)) |
| meowanti-xray only | 75 s | 0 |

Single-player session with a client (patched build, Booklet pages opened; [profile](https://spark.lucko.me/et9uvlt5n9)): 1-min MSPT was 10.4 ms median and 47.5 ms p95, with a 540 ms max over the join. A 2.2 s stall came right after the join. The software-rendered client used the same 4 cores, so these numbers say little about production. In an earlier session, a 27 s stall occurred while opening Booklet pages. It did not happen again under spark, and I could not attribute it.

## Carpet verdict

Carpet 26.3+v260915 boots alongside C2ME, Lithium, AC, ScalableLux and the full S6 list, with no mixin warnings and no new errors. `/carpet` works. A vanilla client joins. The AC rigs give identical results with Carpet present. **Nothing measured here argues against adding it.** Carpet rules and Carpet's interaction with METAmods' fake-player blocker (that module is not in the dist) were not tested.

## Not tested, and why

* mineflayer and minecraft-protocol joins: no 26.3 support in minecraft-data.
* Voice chat audio, WorldEdit editing, Ledger rollbacks, LuckPerms permission trees and Vanish behaviour: they load and their commands register; nothing beyond that.
* Danse gestures, ovve sewing, moredyes blocks and rivals-paint in game.
* meowanti-xray's effectiveness. minertrack was not booted.
* Online-mode authentication: servers ran offline mode because the test client has no Microsoft account.
* B0 and the BlueMap variant were not joined with a client. The BlueMap variant was booted, rendered and profiled.
* No portal-opening or portal-blocker testing, since those modules are not built.

## Open problems

1. **Booklet click crashes the server** (metacraft-lib `CustomMessageRegistry` never initialised now that revival is gone). Proposed fix: `reports/s6-modlist/proposed-fix-custom-message-registry.patch`.
2. **Duplicate "How to ovvar" on the Booklet index**: `ovvar:guide` (ovvar) and `metacraft:ovvar` (metacraft-booklet). `/ovvar guide` opens the ovvar one.
3. **squaremap stalls the main thread 2–5 s about every 19 s during pregen.** BlueMap does not. Consider BlueMap, or check squaremap's render settings before S6.
4. Five modules are out of S6 by design. **The nether event (portal-opening, portal-blocker) has no code in S6.** Revival, relay and better-pets are also absent.
5. `./gradlew build` fails on a single ovvar checkstyle error (`DanseModels.java:137`) on ovvar-danse-fork.
6. Server config needed for vanilla clients: Polymer `auto-host.json` `enabled: true` (or external pack hosting), `white-list` as intended, and BlueMap `accept-download: true` if BlueMap is chosen.
7. Small items:
   * DH logs "Chunky is not loaded" once per boot (harmless here, also in B0).
   * The PolyDecorations `canvas_clone` recipe is dropped for empty ingredients.
   * FabricTailor reports version `${version}`.
   * S6-heavy logs `minecraft:minecraft_empty` template-pool warnings during worldgen.
