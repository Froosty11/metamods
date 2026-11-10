# Cloud debug evidence

Env: 4 cores, 15 GB RAM, Mesa llvmpipe (LLVM 20.1.2) GL 4.5 under Xvfb (see env.txt). Java 25.0.5.

Checkouts: branch edvin/ovvar-enchantments = 443f842 (confirmed). Upstream dev fetched = **1f1c35b**
("Re-add skns to survival...") NOT 2e73aefa; 2e73aefa is an ancestor of it (upstream dev moved on).
Gradle builds needed retries for Maven Central 429s; datagen passed in both checkouts (datagen_*.log).

| run | rc | start–end (UTC) |
|---|---|---|
| A dev_ovvar | 1 | 11:14:05–11:16:53 (2m48) |
| B branch_ovvar | 1 | 11:16:53–11:18:56 (2m03) |
| C kultur | 1 | 11:18:56–11:20:06 (1m10) |
| D kultur_plain | 1 | 11:20:06–11:21:04 (0m58) |

## A (upstream dev, 1f1c35b): "Timeout loading world"
- Two client test classes run: OvvarDanseClientTests (server port 25600) then OvvarClientTests (port 25599).
- Danse test: world load fine (server "Done (0.798s)", client joined 11:15:13, "Can't keep up! 2401ms/48 ticks" at 11:15:09 and 5024ms/100 ticks at 11:15:29, resource-pack reloads at 11:15:09/:19/:47, disconnect 11:15:48). Passed. Screenshots 0000_after, 0001_after_gesture_end, 0002_after_frack exist.
- OvvarClientTests.runTest (OvvarClientTests.java:64, `server.connect()` -> waitForWorldLoad): server on port 25599 started 11:15:51 ("Done (0.276s)"), client "Connecting to localhost, 25599" 11:15:51, server-pack reload "Reloading ResourceManager" 11:15:53, atlases created 11:15:55 (last client log line apart from the repeating poll) ... nothing further for ~57 s. Server logs "Server empty for 60 seconds, pausing" and "Stopping server" 11:16:52; AssertionError "Timeout loading world" 11:16:52.
- Client sat on `ConnectScreen` the whole time (1181 "World loading finished: null screen: ConnectScreen@62fab229" polls; never LevelLoadingScreen, never "Tester joined" on 25599). No server-side errors. No `[ovvar-clienttest]` log lines exist in the test classes' output (grep finds none). No screenshot taken at the timeout.
- So the baseline unchanged upstream dev ALSO hits "Timeout loading world" here.

## B (branch 443f842): "Timeout loading world"
- Failed earlier: in the FIRST test, OvvarDanseClientTests.runTest (OvvarDanseClientTests.java:145), port 25600. Server Done (0.817s) 11:17:50, "Connecting to localhost, 25600" 11:17:50, pack reload 11:17:54, "Can't keep up! 3542ms/70 ticks" 11:17:55, atlases created 11:17:57, then silence until 11:18:55 (Stopping server, AssertionError). 1201 polls all on ConnectScreen@6ef5663e. No screenshots produced. Server-side: only ovvar warnings ("Punkphöz: leaving out kultur:*_banner_pattern ... isn't registered"). 
- Same signature as A's second failure: client stuck on ConnectScreen after the resource-pack reload that follows the connect (after the atlas-creation lines), ~60 s with no log output, no join.
- Earlier loads in same run: none (first connect). In A, earlier loads (Danse) reloaded 3 times and joined ~7 s after connecting.

## C (kultur, Brewery+moredyes on) / D (plain)
Both fail the same way, only one `[kultur-clienttest] loom` line each (no vanilla-control line, no cerise lines reached):
- C:  `loom with a banner and black_dye: 35 selectable on the server, 35 on the client; 3 pattern(s) without an item checked`
- D:  `loom: More Dyes is not loaded, vanilla dye only` then the same `loom with a banner and black_dye: 35 selectable on the server, 35 on the client; 3 pattern(s) without an item checked`
- Then server throws on sending container_set_slot: `EncoderException: Failed to encode packet 'clientbound/minecraft:container_set_slot'` Caused by `IllegalArgumentException: Can't find id for 'Reference{ResourceKey[minecraft:banner_pattern / kultur:arkitektur]=BannerPattern[...]}' in map Registry$1` -> client disconnected -> `AssertionError: Timed out waiting for predicate` at KulturClientTests.awaitClient:291 <- put:280 <- chapterLoom:225 <- runTest:94.
- Other noise in C: NoSuchFileException for brewery asset files at server start (STDERR, brewery dev-resources path lookups).
- Screenshots: 0000_kultur_banner_shield_painting.png in both.

## Surprising
- Dev baseline is not 2e73aefa but 1f1c35b (a descendant).
- A and B time out at the same place type (ConnectScreen after server resource-pack reload, 57-60 s silent), but A's first test passed and B's first failed => looks like an intermittent/software-GL client hang, not specific to the branch.
- C/D reach a different failure: a server-side encode error for banner pattern kultur:arkitektur in the loom slot packet.
