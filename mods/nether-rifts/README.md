# Nether Rifts

While **rift mode** is on (a new world starts with it on), lighting a nether portal in the overworld opens a
flickering rift instead of a portal. Light it twice more and it tears open: a raid of nether mobs comes
through in waves, built on vanilla raids (small / medium / large portals raid like easy / normal / hard).
They drop a little quartz, gold, glowstone, nether wart and, from brutes, blaze rods. Winning closes the rift
and turns its obsidian frame to crying obsidian. While rift mode is on, eyes of ender can't be crafted.

`/rifts stabilise` ends rift mode: waiting rifts become working portals and portal-blocker's nether locks
lift. After that a crying-obsidian frame, lit with flint and steel, opens a rift on purpose (and crumbles
when the raid ends).

Config: `config/nether-rifts.json5`. Commands: `/rifts …`, permission `metacraft.nether_rifts.admin`.

Tests: `./gradlew :mods:nether-rifts:test` (game tests), `./gradlew :mods:nether-rifts:runClientGameTest`
(screenshots; opens a window).
