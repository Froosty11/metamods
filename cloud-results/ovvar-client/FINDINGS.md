# ovvar-client cloud check (checked out eec5278 + merge of edvin/resource-packs-delayed-push, clean merge)

Env: Linux cloud container, Xvfb + llvmpipe, Java 25.0.5, BSL v10.1.8, Iris 1.11.7 / Sodium 0.9.3 (from build.gradle).
Network OK (all five hosts 200 on CONNECT). `runDatagen` + `:mods:ovvar-client:jar` built (ovvar-client-0.2.0.jar).

| Run | task | rc | time (UTC) | verdict |
|---|---|---|---|---|
| R1 vanilla | runClientGameTest | 0 | 15:56:28-16:00:07 | PASS (all client tests, incl. combination pack applied: server pack eae61945...) |
| R2 modded | runClientGameTest -PovvarClientMod | 1 | 16:00:22-16:02:01 | FAIL: OvvarClientTests.java:165 "901 pixel(s) over the mannequin flickered ... (budget 675)". Re-run (modded_rerun) failed with the identical 901 -> deterministic, not a flake |
| R3 bsl | -Piris=bsl.zip -Povvar.iris.expect=art -PovvarClientMod | 1 | 16:02:11-16:03:50 | FAIL: "right-leg crop does not show the Data ovve's cloth: only 0/4350 cerise pixels"; garment drew NEAR-BLACK (27,35,34), not the magenta checker. Matches the known llvmpipe/BSL dark ovve; cannot tell environmental vs real here |
| R4 nametag | R3 + chapter-text-colors pack | 1 (same R3 failure, after my scratch test ran first) | 16:04:33-16:07:08 | measured, see below: nametags NOT recoloured under BSL+patch |
| extra nametag_ctl2 | -PovvarClientMod -PnametagCtl=1 (no shader, pack on) | 1 (flicker as R2) | 16:12:05-16:14:09 | control: pack recolours nametags without a shader |
(nametag_ctl_noshader was invalid: pack was not selected, options.txt got reset; ignore.)

## R2 evidence
- PASS server log: `[ovvar] Tester draws patch combinations on their client (ovvar-client): no combination pack for them`
- PASS client-test: `ovvar-client: no combination pack; server packs pushed: [8c24d8a4-2d19-4cd4-89bb-a37314f0cbf6]` (one id, no ovvar:combos)
- PASS no `ovvar/<sha1>.zip` download in the log (0 matches)
- PASS `/ovvar reload` reply "Your client draws every patch combination itself (ovvar-client): there is no pack to reload"
- PASS 0x `cannot build equipment asset`, 0x `equipment asset hook is not active`
- NOT RUN (aborted by the flicker failure): rest of OvvarClientTests (frack_front, instants, chapters, punkphoz), OvvarEnchantmentClientTests, MobOvvarClientTests, OvvarDanseClientTests. Only screenshots 0000-0004 exist for R2. So frack_front.png comparison is impossible.
- R1 vs R2 screenshot diff (px >24 any channel, 1920x1080, diff_r1_r2.txt):
  0000_after 2.98% (bbox 0,0..1156,403: sky/clouds/time), 0001_after_gesture_end 0.586% (bbox 808,573..1110,784 mannequin),
  0002_after_frack 0.001%, 0003_front_four_patches 444 px (0.021%) same bbox as mannequin, 0004_front_four_patches_again 3041 px (0.147%).
  Visual: in 0003 patches have the same positions/colours R1 vs R2 (no blank garment, no magenta). In R2's 0004 the whole mannequin
  is rendered shifted down several px versus R2's 0003 (that is the flicker-test failure: frame-to-frame movement, same patches).
  Within the test's own subject box (780..1140 x 560..800) 0003 vs 0004: R1 1053 px, R2 1714 px >24 (identical in the re-run).
  R1 passes the budget, R2 does not.

## R3 evidence
- `[ovvar-client] bsl.zip -> bsl+ovvar.zip`, `[ovvar-client] iris.properties: shaderPack bsl.zip -> bsl+ovvar.zip`; iris pack in use=true name=bsl+ovvar.zip.
- OvvarShaderpackClientTests with `art`: 4350/4350 crop px differ from background (garment drew), 0/4350 cloth coloured -> FAIL. No magenta checker. Screenshot shots/bsl/0000_ovvar_iris_leg_art.png: mannequin garment near-black (27,35,34), player's own sleeve red (161,107,89), sky/grass lit normally.

## R4 measured (glyph pixels, 1920x1080, noon)
BSL + ovvar patch + chapter-text-colors pack (shots/nametag): darkest-5% glyph px
 dark_purple (103,89,136) [expected ~(232,61,132): R below B -> NOT cerise], dark_blue (95,110,188) [expected (204,153,255): NOT],
 light_purple (109,94,188), blue (72,94,190) [controls roughly unchanged]. Chat (the vanilla text shader, unaffected by Iris): dark_purple (232,61,132) PASS, dark_blue (204,153,255) PASS (exact).
No-shader control (shots/nametag_ctl2, same pack): dark_purple glyph (204,53,116) cerise, dark_blue (179,134,223) violet, light_purple (223,74,223), blue (74,74,223) -> pack works on nametags in vanilla rendering.
=> Under BSL the nametags stay stock colours (verdict FAIL for nametags-under-Iris, chat PASS).
Patch log: the in-game run only printed the 2 `[ovvar-client]` bsl lines (no per-program lines in game log). The CLI run (below) prints
`shaders/program/gbuffers_entities_translucent.glsl: chapter text colours (shaders/world-1/gbuffers_entities_translucent, world0/..., world1/...): gl_Color wrapped`
and bsl+ovvar.zip contains it; yet BSL nametags are unchanged, so either Iris draws nametags with a different program than gbuffers_entities_translucent in BSL, or the wrap does not take effect. Needs a human/dev look.
The automatic "diff vs base" metric printed by the scratch test is unreliable (sky/cloud movement); use the glyph numbers above (computed with scratch/Glyph.java, Sat.java, Chat.java).

## java -jar
`java -jar mods/ovvar-client/build/libs/ovvar-client-0.2.0.jar /tmp/jt/bsl.zip` (copy): rc 0, wrote bsl+ovvar.zip (1185109 bytes), "1 pack(s) patched, 0 skipped, 0 failed" (jar_run.txt). PASS.

## For a human
1. R2: ovvar-client makes OvvarClientTests' two-frame stability check fail deterministically (901 > 675); mannequin shifts between frames. Real regression or timing of the modded client (e.g. the equipment hook delaying a model update)? Look at shots/modded 0003 vs 0004.
2. R3/R4: BSL garment dark; nametags not recoloured under BSL+patch.
