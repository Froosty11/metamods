# METAcraft-Void-Anchor

A respawn anchor for the End void. Elytra rockets are off on Metacraft, so nothing used to save a
player who fell off an End island; a void anchor does.

- **Craft** it like a respawn anchor: crying obsidian on the top and bottom rows, eyes of ender in the middle.
- **Charge** it with the fuel item (echo shards by default, the stuff recovery compasses are made of), up to four charges. A dispenser holding
  the fuel item tops up an anchor in front of it.
- **Bind** it by using it with an empty hand. It only works in the End; elsewhere it refuses to bind
  (and, unlike a respawn anchor, never explodes or sets your spawn). One anchor per player; several
  players may share one, and its charges.
- **Fall.** Below the End's lowest Y a rift opens beneath a bound player, their fall eases into it,
  and they come out beside their anchor, which loses one charge. If the anchor is empty, broken or
  blocked, the player is told so and falls as before (into season 5's overworld teleport, if that
  is on). Players without an anchor see no change.

`/voidanchor rift [pos]` (permission `metacraft.voidanchor`, level 2) opens a rift that only
looks, for checking how it renders.

## Config

`config/metacraft-void-anchor.json`:

| Key | Default | |
|---|---|---|
| `fuel_item` | `minecraft:echo_shard` | the item that adds one charge |
| `trigger_y_offset` | `0` | the rift opens below the End's lowest Y plus this |
| `rift_depth` | `6.0` | how far below the player the rift opens |
| `descent_speed` | `0.3` | blocks per tick while sinking into the rift |
| `rift_ticks` | `30` | the longest a player sinks before the rift takes them |
| `rift_size` | `3.0` | the rift's width in blocks |

## The rift shader

The rift is an item display of `metacraft:rift`, a flat quad with a painted, animated sprite
tinted exactly `#FEFEFD`. The mod's pack overrides `minecraft:shaders/core/item.vsh` and
`item.fsh`: a vertex with that tint is flagged, and its fragments draw a moving swirl of stars
instead of the sprite. Every other item takes the vanilla path. A client without the shader still
sees the painted sprite.

The overrides are vanilla 26.3's shaders plus that one branch, so **re-diff them against vanilla on
every Minecraft update**. 26.3 compiles shaders to SPIR-V, hence `gl_VertexIndex`, not `gl_VertexID`.
Which vertex counts as a quad's first depends on where its draw starts in a shared buffer, so the
swirl repeats every quarter turn: a shifted corner order turns it by 90° and nothing visibly changes.

Block and rift textures are placeholders drawn by `tools/gen_textures.py`; replace them with real art
whenever.

## Tests

- `./gradlew :mods:metacraft-void-anchor:runDatagen :mods:metacraft-void-anchor:test` runs the
  game tests (charging, binding, dispensers, and the rescue: empty, gone, flying, creative,
  disconnecting, saved-themselves and shared anchors).
- `./gradlew :mods:metacraft-void-anchor:runClientGameTest` opens a window: a vanilla client joins
  an in-process server, looks down at a rift, and checks that the shader ran, the rift moves, and
  it looks the same turned a quarter (see above).
  Screenshots land in `build/run/clientGameTest/screenshots`.
