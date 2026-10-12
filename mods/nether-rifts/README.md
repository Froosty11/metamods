# Nether Rifts

While **rift mode** is on (a new world starts with it on), lighting a nether portal in the overworld opens a
flickering rift instead of a portal. Light it twice more and it tears open: a raid of nether mobs comes
through in waves, built on vanilla raids (small / medium / large portals raid like easy / normal / hard).
They drop a little quartz, gold, glowstone, nether wart and, from brutes, blaze rods. Winning closes the rift
and turns its obsidian frame to crying obsidian. While rift mode is on, eyes of ender can't be crafted.

`/rifts stabilise` ends rift mode: waiting rifts become working portals and portal-blocker's nether locks
lift. After that a crying-obsidian frame, lit with flint and steel, opens a rift on purpose (and crumbles
when the raid ends).

A rift is drawn by metacraft-core's item shader as pixel art fixed to the world (16 pixels a block): a
purple membrane in the nether portal's colours, glitching, with cracks that widen as the rift tears, and
through them a far-off nether (a panorama captured in game). Players whose core shaders are replaced (Iris,
OptiFine) see the panel's raw sprite instead: that panorama's six cube faces, stretched over the rift; unless
their shaderpack went through ovvar's shader patcher, which puts the same drawing into the pack (seen working
in Complementary Reimagined and Unbound, BSL and Bliss).

Config: `config/nether-rifts.json5`. Commands: `/rifts …`, permission `metacraft.nether_rifts.admin`.

Tests (game tests): `./gradlew :mods:nether-rifts:test` also runs the tests of the modules it depends on, and
metacraft-lib's fail in a fresh checkout until its data is generated, so run
`./gradlew :mods:metacraft-lib:runDatagen` once first. Or skip the dependencies' tests:

```
./gradlew :mods:nether-rifts:test -x :mods:portal-blocker:test -x :mods:portal-opening:test -x :mods:simple-custom-features:test -x :mods:metacraft-zones:test -x :mods:metacraft-core:test -x :mods:metacraft-config:test -x :mods:metacraft-lib:test
```

Screenshots: `./gradlew :mods:nether-rifts:runClientGameTest` (opens a window).

The panorama: `./gradlew :mods:nether-rifts:runClientGameTest -PcapturePanorama` takes it again (a fixed
seed, an open spot over lava by a crimson forest) into `tools/panorama/`; `python3 tools/gen_panorama.py`
turns it into the panel's sprite.
