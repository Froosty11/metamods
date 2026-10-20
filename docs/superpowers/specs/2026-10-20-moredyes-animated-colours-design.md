# More Dyes: animated colours (slow RGB fade)

> **Update 2026-10-20 (Edvin):** the shipped colour is **RGB** (id `rgb`, name "RGB"), not
> Rainbow; same 24-hue, 3-minute fade. The harness and llama carpet get static red/green/blue
> stripes. Wherever this document says Rainbow, read RGB.

Status: draft, 2026-10-20. Module `mods/moredyes` (repo is on Minecraft 26.3-rc-1 / Polymer
0.18.0+26.3-rc-1; the module README still says 26.2).

## Intent

A More Dyes colour can be *animated*: instead of one colour, it slowly drifts through a list of
colours, e.g. a full rainbow over about three minutes. Every block and item that colour produces
(wool, concrete, glass, beds, sheep, and so on) fades through those colours on a vanilla client,
using only the auto-served resource pack. Dyed leather armour (and horse/wolf armour) made with
that dye fades too.

The fade is meant to be **slow and calm**, something you only notice changing if you watch it.
It should not strobe or pulse.

## Goals

- Ship **Rainbow** as the third More Dyes colour, next to Cerise and Laserviolet. It's the last
  planned colour: Edvin says the copper-bars donors that glass panes use fit three colours. (The
  module README still says two; `Looks` decides at startup and logs it. If panes flip to the
  display look with three colours, bring that back as a finding.)
- `colors.json` can describe an animated colour. Everything else is still derived from it by
  `./gradlew runDatagen` with no Java that knows colour ids, as for static colours.
- Each texture an animated colour produces fades smoothly. The default animation is a full rainbow
  over about 3 minutes (24 keyframes × 150 ticks = 3600 ticks = 180 s, `interpolate: true`).
- Worn dyed armour of an animated colour fades. For each player, the armour stays in step with
  the blocks that player sees.
- Static colours behave exactly as before: their generated files are byte-identical to today's.

## Non-goals

- **Different phases for different blocks.** Every animated sprite of a colour shares one phase,
  so a wall of rainbow wool changes as a whole. You can't send a wave across placed blocks with
  pack animation without spending donor states per phase.
- **All players seeing the same colour at the same moment.** Pack animation runs on each client's
  own clock, which starts when that client loads the pack (on join). Each player's cycle starts at
  the first keyframe when they join. Agreed in conversation.
- Animating things that only take a single RGB with no per-viewer packet route: firework
  explosions and the map colour. These use the colour's static `rgb`.
- Changing donor budgets or `Looks`. An animated colour costs the same donor states as a static
  one.

## Anti-goals (failure even if it "works")

- A visibly stepping or flickering fade on blocks.
- Armour that visibly jumps between colours.
- Changing the real `ItemStack`s on the server to animate armour. That would churn item data,
  break stacking and comparisons, and dirty every container. Only the copy sent to each client
  may differ.
- Silent fallbacks. The mod's existing rule holds: a missing texture/mask is logged as an error
  and the build or startup fails where it does today.

## How it works

### 1. `colors.json`

An entry may carry an optional `animation` object:

```json
{
  "id": "rainbow",
  "name": "Rainbow",
  "rgb": "#FF4040",
  "animation": {
    "frametime": 150,
    "interpolate": true,
    "frames": ["#FF0000", "#FF4000", "#FF8000", "#FFBF00", "#FFFF00", "#BFFF00",
               "#80FF00", "#40FF00", "#00FF00", "#00FF40", "#00FF80", "#00FFBF",
               "#00FFFF", "#00BFFF", "#0080FF", "#0040FF", "#0000FF", "#4000FF",
               "#8000FF", "#BF00FF", "#FF00FF", "#FF00BF", "#FF0080", "#FF0040"]
  }
}
```

- `frames`: list of keyframes. Each is either a hex string (its ramp comes from
  `ModColor.deriveRamp`) or `{ "rgb": "#...", "ramp": ["#dark", "#light"] }` for hand-tuned stops,
  like a static colour's `ramp`. At least 2 frames.
- `frametime`: ticks per keyframe (positive int). One cycle = `frames.size() × frametime` ticks.
- `interpolate`: passed through to the mcmeta. Defaults to `true`.
- `rgb` stays required. It is the static fallback (fireworks, map colour, startup log) and the
  colour used anywhere this spec doesn't animate.

Keep about 24 keyframes even for slow cycles. Vanilla interpolation blends linearly in RGB, so
widely spaced hues go muddy between keyframes (red→green passes through olive).

`ModColor` gains an optional animation (frames with rgb and ramp, frametime, interpolate). It is
also loaded at runtime because the armour clock needs it. `ModColors` validation stays strict:
a malformed animation fails loading, like any other malformed entry.

### 2. Block and item textures (datagen)

`GeneratedAssets.run` already writes each `TEXTURES` entry (plus the derived sheep textures:
sheared body, wool hurt, sheared hurt) as `src.recolour(dark, light)`. For an animated colour,
each of those becomes:

- a vertical strip: `src.recolour(f.dark, f.light)` for every keyframe `f`, stacked top to bottom
  (width unchanged, height × frame count);
- a sibling `<name>.png.mcmeta`: `{"animation": {"frametime": N, "interpolate": true}}`.

`Tex` needs a vertical-stack operation. Derived textures (`composite`, `hurt`) are applied per
frame before stacking. Models, item definitions, loot, recipes, tags and lang are unchanged; they
point at the same texture names.

This covers wool, concrete, concrete powder, terracotta, glazed terracotta, carpet, candles
(block, lit, item), dye item, beds (7 faces), stained glass and pane top, bundles (3), the sheep
wool overlay and sheared bodies, the shulker block-atlas copy (`block/shulker/{c}`), and the
shulker entity texture (`entity/shulker/{c}`). Stairs, slabs, shulker boxes and sheep are drawn
by display entities using block-atlas models, so they animate for free.

All sprites of a colour use the same frame count and frametime, so they start together at pack
load and stay in phase.

**Exception: equipment textures** (`entity/equipment/...`: the happy ghast harness and the llama
carpet). In 26.3 the equipment layer renderer binds these directly, not from an atlas, so `.mcmeta`
animation does not run and a strip would draw squashed. For an animated colour they stay one static
frame in the colour's own ramp. For the same reason, a custom armour item with an animated
equipment texture is not an option for worn armour (checked 2026-10-20 against the 26.3-rc-1
client jar). Only the item icon could animate.

### 3. Banner patterns (pack build, runtime)

`BannerPatterns.generateTextures` multiplies each mask by `d.color().rgb()`. For an animated
colour it multiplies by every keyframe's `rgb`, stacks the results vertically, and adds the same
mcmeta next to it via the `ResourcePackBuilder`. Static colours: unchanged.

### 4. Dyed armour (server, per viewer)

**Rule (Edvin): rainbow dye overwrites.** Putting an animated dye on armour turns it into that
animated colour, replacing whatever colour it had. The old `dyed_color` doesn't weigh in.
Builder's calls, consistent with that rule:
- the crafting recipe matches an animated dye only on its own (armour + one animated dye, no
  other dyes in the grid);
- dyeing animated armour with normal dyes gives a plain static colour from those dyes alone (the
  rainbow's fallback `rgb` is not mixed in);
- washing in a cauldron clears both the colour and the animation, as vanilla does.

**Marking.** `DyedItemColor` is a single int, so an animated dye also sets a mod data component,
`moredyes:animated_dye` = the colour id. Because the dye overwrites, the id is all the marker
needs. The real stack's `dyed_color` is set to the colour's `rgb`, so with the mod gone it is
plain dyed armour.

**Rewriting.** `PolymerItemUtils.ITEM_MODIFICATION_EVENT` (with `CONTEXT_ITEM_CHECK` so plain
vanilla dyed items get routed through Polymer) rewrites the client-bound copy of any marked stack:
`dyed_color` = the colour at the *viewing player's* current phase. The real stack is never
touched.

**Clock per viewer.** Record when each player's client reported the main pack as
`SUCCESSFULLY_LOADED` (`ServerboundResourcePackPacket`). That is roughly when the client's atlas
animation clock starts at frame 0. The pack may arrive during the configuration phase, before a
`ServerPlayer` exists, so key it by profile UUID. Phase = elapsed time since that moment, in
ticks, mod cycle length. The colour is the linear RGB blend between the two surrounding
keyframes' `rgb`, matching the vanilla interpolation. Use wall-clock time (ms / 50), not server
ticks, so server lag doesn't pull armour out of step with the client.

**Refresh.** Every 20 ticks (the per-update change at a 3-minute cycle is 2–3 RGB units, which
you can't see), re-send equipment for entities wearing marked armour to everyone tracking them,
and the wearer's own armour slots. Re-sending is what triggers the rewrite above for each viewer.
Keep a set of current wearers, updated on equipment change, rather than scanning all entities.

**Scope:** whatever an entity is wearing (players, armour stands, horses, wolves), plus the
wearer's own equipped slots. Marked armour lying in inventories or containers shows the colour
from when it was last sent. It isn't refreshed on a timer (builder's call to keep packet volume
bounded; see "Calls I made").

### 5. Fireworks, map colour

Unchanged: they use the static `rgb`.

### 6. PolyDecorations canvas

**Rule (Edvin):** painting a canvas with an animated dye puts down a random colour per pixel.
Canvases use the fixed map palette and can't animate.

The canvas compat already exists: commit `d61ba906` ("moredyes: our dyes paint PolyDecorations
canvases", `compat/polydecorations/mixin/CanvasEntityMixin`). It is on `moredyes-features` /
`edvin-test`, **not** on `ovve-db`, so build on a branch that has it. Today its `getColor` hook
returns `CanvasColor.from(dye.color().mapColor(), NORMAL)`. For an animated colour it instead
returns a random pick, each time a pixel is painted, from the distinct nearest map colours of the
animation's keyframes, at `NORMAL` brightness. Builder's call: picking from the keyframes rather
than the whole palette keeps it looking like rainbow confetti, not random browns and greys.
Extend the existing `canvasTakesOurDyes` game test: painting with an animated dye gives a colour
from that set.

## Constraints

- Vanilla client and the Polymer pack only. No client mod.
- All colour-specific output still comes from `colors.json` through datagen. Generated assets
  stay gitignored.
- Pack size grows roughly with the frame count for each animated colour's textures (today's pack
  is about 360 KB for two static colours). Fine for one or two animated colours. Log the generated
  size in datagen so growth is visible.
- Atlas space is a single frame per sprite, so atlas size is not a concern.

## Verification

- Game tests (existing `MoreDyesGameTests` style):
  - an animated colour's blocks resolve to client states like any colour;
  - the armour rewrite sends a different `dyed_color` at two different phases and never changes
    the server stack;
  - washing and re-dyeing clear the marker.
- Datagen check: every texture of an animated colour has height = frames × source height and a
  matching `.mcmeta`. Static colours' output is unchanged.
- Visual check through a client screenshot test in the cloud (never a local client window).
  Shots of the showcase at two times about 45 s apart should show different hues. **These need
  confirming in the client**, because they draw from atlases other than the block atlas:
  - the shulker item (`entity/shulker/*`)
  - banner and shield patterns

  If an atlas turns out not to animate, that's a finding to bring back, not something to paper
  over.

## Left to the builder

- Package and class names, how `ModColor` represents the animation, and the exact Polymer and
  mixin hook points for the pack-status packet and for re-sending equipment.
- Whether the keyframe list in `colors.json` gets a shorthand (e.g. `"hues": 24`) in addition to
  the explicit list.
- How the showcase (`/moredyes showcase`) presents the animated colour, if at all.

## Calls I made (not discussed)

- Armour refresh every 20 ticks. Only worn armour and the wearer's equipped slots are refreshed;
  items in inventories and containers are not.
- Wall-clock time for the armour phase.
- The default rainbow is 24 evenly spaced fully saturated hues starting at red, with ramps derived
  automatically.
- Firework stars use the static `rgb`.
- On a canvas, the random pixel colour is picked from the rainbow's keyframes (their nearest map
  colours), not the whole map palette.
- An animated dye can't be mixed with other dyes in the crafting grid. Normal dyes on rainbow
  armour give a static colour from those dyes alone.
