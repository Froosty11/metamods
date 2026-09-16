# More Dyes

METAmods module `mods/moredyes` (mod id `moredyes`). Targets Minecraft 26.2 / Polymer 0.17.1+26.2.
Assets are generated: `./gradlew runDatagen` (repo root) after editing `colors.json`.

Server-side dye colours for vanilla clients: Cerise, Laserviolet, and whatever `colors.json`
says next. Fabric + [Polymer](https://polymer.pb4.eu), Minecraft 26.2, Java 25. Players need
only the auto-served resource pack; no client mod.

The design and phase plan live in the "Cerise & Laserviolet" artifact; this file is the
how-to-build. Status: all block phases done — dyes; wool, concrete, concrete powder,
terracotta, glazed terracotta (visible note-block donors); carpet (flat tripwire donor, no
collision); candles (invisible lantern donor + item display, server-sent flame particles) and
candle cakes (vanilla cake on the client + candle display); wool and concrete stairs and slabs
(invisible shaped donor + one item display per block); beds (spare bed-state donors); shulker
boxes (invisible donor + base/lid displays); stained glass and panes (leaves / copper-bars
donors — since 26.x the client picks the render layer per sprite, so they are truly
translucent); bundles; sheep (colour attachment, vanilla sheep sent invisible, whole-sheep
display-entity rig animated server-side); banner patterns (every registered pattern, vanilla or
datapack, derived per colour at registry load and tinted at pack build; applied through the
dye-loom GUI); recipes for everything vanilla dyes make, including leather/horse/wolf armour
and firework stars mixed with vanilla dyes. Not supported by design: wolf/cat collars, sign
text, and harnesses. Beacon beams used to be on that list; they are now a spike — the beam is
rebuilt server-side out of display entities, see "Beacon beam (spike)" below.

## Adding a colour

Add an entry to `src/main/resources/colors.json` — `id`, `name`, `rgb`; `ramp: [dark, light]` is
optional (derived in CIELAB from `rgb` when absent) — and run `./gradlew runDatagen`. Everything
else loops over the list: blocks, items, recipes, tags, banner patterns, tests, the showcase.

What limits how many colours fit is Polymer's donor-state budget, and `Looks` decides once at
startup, per family, whether the colour count fits the visible-donor pools ("donor" look: our
model as chunk geometry) or the family switches to a shared invisible donor plus one item display
per placed block ("display" look). The decision is logged at startup and applies to all colours of
that family; `-Dmoredyes.look.<family>=donor|display` forces it for comparison. Today only glass
panes are planned this way (their copper-bars pools hold two colours in donor mode); beds
(about 15), carpets (about 30) and glass (about 50) get the same treatment when needed. A pool
that cannot serve even the chosen look still stops the server.

## Using it in game

- Dye a sheep: right-click it with a dye. Shear/kill drops the coloured wool. Vanilla dye turns
  it back. Admins: `/moredyes dye <targets> <color>`.
- Banner patterns: hold a banner (or a shield with a base colour) in one hand and a dye in the
  other, right-click the air. Pick a pattern; it is applied with white as the carrier colour.
- Dyes have no recipe yet (a flower is planned); use the creative tab or `/give`.

## Ground rules (short version)

- **Never extend `DyeColor`.** Colours are a mod-owned registry (`ModColor`); our dyes are not
  vanilla `DyeItem`s. Anything DyeColor-keyed that cannot show a new colour on a vanilla
  client (wolf/cat collars, sign text, vanilla dye-tag recipes) is simply not supported.
- **Fallbacks are errors.** A Polymer donor pool that is short at startup stops the server
  with a message naming the family. A visual path that fails at runtime shows a command block
  and logs an error. The one deliberate vanilla approximation is the map colour (64-entry
  client palette), computed as the nearest by RGB at init.
- **The pack is required.** A vanilla client that declines is disconnected by the game.
  Enable auto-hosting once on the server: `config/polymer/auto-host.json` → `{ "enabled": true }`
  (behind a proxy also set `"forced_address"`).

## Adding a colour

1. Add an entry to `src/main/resources/colors.json`:
   ```json
   { "id": "seafoam", "name": "Seafoam", "rgb": "#5FE0C0", "ramp": ["#1F7A66", "#C8FFF0"] }
   ```
   `ramp` is the dark and light stop the texture recolouring maps onto; tune it by eye.
2. `./gradlew runDatagen` (standard Fabric data generation, `metacraft.moredyes.datagen`; output in `src/main/generated`).
3. `./gradlew build`. Everything else — blocks, items, models, loot, recipes, tags, names — is
   derived. No Java knows a colour id.

Never delete an entry once worlds exist; mark it `"retired": true` so existing blocks resolve.

## Build

```
export JAVA_HOME=/path/to/jdk-25
./gradlew runDatagen build
```

Output: `build/libs/moredyes-<version>.jar` (Polymer bundled). Server needs Fabric Loader
≥ 0.19.2 and Fabric API 0.150.x for 26.2.

Dev server: `./gradlew mods:moredyes:runServer` (or `Start Server.command`); `run/` ships `eula.txt`,
`server.properties` with `online-mode=false`, and `config/polymer/auto-host.json` enabling the
pack host. Vanilla client: `python3 tools/vanilla_client.py` (or `Start Vanilla Client.command`)
downloads the real 26.2 client into `run-vanilla/`, starts it with an offline profile and joins
`localhost`. That is the client that counts. `./gradlew runClient` also exists but it is a
Fabric client with Polymer's client half loaded, so it does not show what players see; it runs
in `run-client/` so it cannot clobber the server's generated pack.

## Beacon beam (spike)

Status: **spike**, throwaway-grade, `metacraft.moredyes.beacon`. One pass with a real vanilla client
has happened; the beam draws and the beacon block reads correctly.

A vanilla client computes the beam colour entirely client-side, from the `BeaconBeamBlock`s it can
see in the column. Our stained glass is sent as a leaves donor, so the beam goes through it white,
and no packet we send about that block can change it. So the beam is replaced instead:

- **Near players** (default 128 blocks, horizontal) are sent a **barrier** where the beacon is and get
  an `ElementHolder` at the beacon: a `BlockDisplayElement` putting the beacon's own look back, plus
  a fixed pool of 16 beam **segments** of 2 `ItemDisplayElement`s each — an opaque core and a
  translucent glow, vanilla's two beam parts. **33 entities per
  beacon, always.** Each carries a `dyed_color` component that an item-model definition's
  `minecraft:dye` tint multiplies into the beam texture — block displays cannot be tinted to an
  arbitrary RGB, item displays can. The beacon still works: the barrier is only what the client is
  shown, the server block is the real beacon.
- A segment covers a **power of two blocks, up to 16**, and its texture carries one copy of the beam
  tile per block, so the pattern is the same density as vanilla's at any height (a 13-block section
  is 8 + 4 + 1 segments). Like vanilla's `BeaconRenderer` (which
  draws the last section with height 1024 whatever the walk said) the **last section is extended to
  the sky** — to the world's build height plus 64, or as far as the 16 × 16 = 256-block pool reaches,
  whichever is shorter. 256 blocks is past the near radius, so it reads as "to the sky"
  while keeping the entity count flat.
- **The element pool is allocated once and never changes.** Segments are shown by giving them an item
  and hidden by giving them `ItemStack.EMPTY`; nothing is ever added or removed. That is load-bearing
  — see the comment on `BeaconBeamHolder`. `ElementHolder.stopWatching` builds its
  `ClientboundRemoveEntitiesPacket` directly off its own live `IntList` field, and that packet keeps
  the list by reference, so an element added or removed before Netty encodes it corrupts the packet
  and kicks the client.
- **Far players** keep the real beacon and get our glass in that column swapped for **ghost vanilla
  stained glass**, so their own client tints the beam approximately. One dye can only ever be one of
  16 colours, which next to the display beam is a visible jump, so where the block above ours is air
  we spend a **second ghost block** and search all 16 × 16 ordered pairs: the beacon is already
  section 0, so vanilla takes the lower glass raw and `ARGB.average`s the upper one into it, and the
  average reaches colours no single dye does. In CIELAB (same rule as the map colour) that is **60%
  closer for cerise** (`magenta` + `red` -> `#BB3E71`, against `pink` `#F38BAA` alone) and **63% for
  laserviolet** (`white` + `purple`). With no room above it falls back to the single nearest dye.
  Crossing the boundary resends the beacon and our glass to that player alone — including the block
  above each of ours, which is the only way to clear a ghost once the player comes back in range.
- The sections are vanilla's own walk (`BeaconBlockEntity.tick`), re-implemented in `BeamWalk` with
  our glass added as a colour source — same `ARGB.average` merge rule, same stop condition.
- **Only columns that contain one of our colours are taken over.** A plain vanilla beacon is left
  completely alone and keeps its own, correct, client-side beam.
- The beam scrolls with no packets, at vanilla's rate and vanilla's two pattern scales. A block
  model cannot tile a face's UV or sample outside its sprite, so everything vanilla gets from
  wrapping one 16 × 16 texture is baked into ours (`runDatagen`, from
  `entity/beacon/beacon_beam`, as `textures/block/beacon_beam_{core,glow}_{1,2,4,8,16}.png` under
  `block/` so the blocks atlas picks them up). Read off `BeaconRenderer`:
  - **Stretch.** The inner quads run `v` from `-1 + frac` up to `height * textureScale * (0.5f /
    radius)`, which at `SOLID_BEAM_RADIUS` 0.2 is **2.5 texture units a block**; the glow quads stop
    at `height * textureScale`, **one unit a block**. So the glow's pattern is 2.5× the core's, and
    crawls 2.5× as fast up the beam. Ours bakes 2.5 repeats a block into the core's textures and 1
    into the glow's.
  - **Speed.** Both `v`s carry `frac(-animationTime * 0.2f - floor(-animationTime * 0.1f))`, and
    `animationTime` is `floorMod(gameTime, 40) + partialTick`: **0.2 of a texture unit a tick**,
    upwards (`v` is largest at the top of the quad). So a tile passes every five ticks: **5 frames,
    `frametime 1`**, frame *f* rolled by `round(f * tile / 5)` px — 3, 3, 4, 3, 3 px on the glow's
    16 px tile and 6, 7, 6, 7, 6 on the core's 32 px one, both exactly one tile per five ticks.
    `interpolate` is **off**: a crossfade blurs a pattern this fine, and vanilla's own sampling is
    nearest.
  - **Size.** The frames sit **side by side**, not stacked (`SpriteContents.AnimatedTexture` reads
    frame *i* from `i % (imageWidth / frameWidth)`), because the frame is what has to stay small: a
    16-block core frame is 1280 px on its own and five stacked would be a 6400 px sprite dragging
    the whole atlas up a power of two. Largest sheet 80 × 1280, all ten about 240k texels — less
    than a 512 × 512 texture. Every frame height is a multiple of 16 (datagen fails if one is not),
    or the atlas would drop a mipmap level for every block in the game.
- The **core** spins, like vanilla's. `BeaconRenderer` pushes a pose, turns it by
  `animationTime * 2.25 - 45` degrees about `Axis.YP` (2.25° a tick), submits the inner beam, and
  **pops the pose before laying out the glow** — so vanilla's outer beam is a fixed, axis-aligned
  box and only the inner one turns inside it. Ours does the same. We cannot send a packet a tick, so
  every 20-tick refresh each shown core gets **+45° of left rotation** with
  `interpolation_duration` 20 and a start-interpolation flag, and the client slerps across exactly
  the second it takes for the next one to arrive: one small metadata update per segment per second.
  The accumulated angle wraps at **720°, not 360°** — a quaternion halves its angle, so q(720°) is
  exactly q(0°) while q(360°) is −q(0°), the same rotation with the opposite sign, which a
  shortest-path slerp would walk backwards through.
- The beam model is **two-sided**. Item displays backface-cull while vanilla's beam render type does
  not, so an outer box alone shows only its near walls and the translucent glow reads flat. Each wall
  gets a zero-thickness twin just inside it carrying the opposite face (a `south` quad behind the
  `north` wall, and so on) with u mirrored — the same trick `twoSidedBox` uses for shulker boxes.
  `shade` is off on every element: a beam is emissive, not a lit box.
- **The glow's render layer is pinned in the model, not inherited from the carrier item.** In 26.3 a
  quad's layer is baked *per quad* from its own sprite: `FaceBakery.computeMaterialTransparency`
  takes `force_translucent` from the material, else analyses the sprite region's alpha, and
  `BakedQuad.MaterialInfo.of` turns that into `Sheets.translucentBlockItemSheet` or
  `cutoutBlockItemSheet` (there is no `ItemBlockRenderTypes` any more). So the glow model sets
  `"force_translucent": true` on its texture, exactly as vanilla's own `block/glass` model does, and
  the layer cannot depend on how the alpha analysis reads an animated strip. The carrier split —
  glow on `white_stained_glass`'s item, core on `stone`'s — stays as belt and braces.
- **The glow is alpha 32/255, and the core is 0.283 blocks wide.** Both were read straight off
  `BeaconRenderer`: it draws the glow part with `ARGB.color(32, color)`, so our 0.3 (alpha 77) read
  as a second solid beam rather than a haze around one; and its inner part is not a 0.4-wide box but
  four quads running corner to corner, `(0, r), (r, 0), (0, -r), (-r, 0)`, so `SOLID_BEAM_RADIUS`
  0.2 is the **half-diagonal** of a square only `0.2 × √2 ≈ 0.283` wide. Ours is a box, so the core
  takes half that as its half-side; at 0.2 the beam was visibly fatter than vanilla's.
  `BEAM_GLOW_RADIUS` 0.25 needs no such correction — vanilla's glow part is an axis-aligned box too.

Known rough edges: the block display is `FULL_BRIGHT`, but the **barrier the client is sent emits no
light**, so a taken-over beacon does not light its surroundings the way a real one does. The block
itself looks right; only the light it casts is missing. Vanilla's core is a diamond (four quads
corner to corner) turning inside its box-shaped glow, where ours is a box turning inside a box of
the same width — the silhouette matches, the corners are 45° out of phase. And a 1-block core
segment carries 2.5 tiles, so the pattern meets the next segment half a tile out of step; every
other segment height is a whole number of tiles.

Knobs: `-Dmoredyes.beacon=off` disables the whole thing; `-Dmoredyes.beacon.near=<blocks>` moves the
boundary (make it small, e.g. 8, to see both sides without walking far).

### Verifying it in game

```
./gradlew mods:moredyes:runDatagen                          # generated assets, including the beam
./gradlew mods:moredyes:runServer -Dmoredyes.beacon.near=16 # dev server, small near radius
python3 tools/vanilla_client.py                             # the real vanilla client, joins localhost
```

Then, in game: place a beacon with sky above it, put a cerise stained glass block a few blocks over
it, and look for

1. a **cerise** beam above the glass and a white one below it (near the beacon);
2. the beacon block itself still looking like a beacon and still opening its menu on right-click;
3. walking past 16 blocks: the beam snaps to a vanilla-tinted one (the glass turns pink for you,
   within one second — the holder re-checks every 20 ticks) and back when you return;
4. `-Dmoredyes.beacon=off` giving the plain vanilla white-through-our-glass beam again.

Things a client would have to confirm, which no server-side test can: whether a 256-block beam gets
culled despite `setDisplaySize(0, 0)` and `setViewRange(4)`, and whether a second of interpolation
per element really reads as a continuous spin at the top of a long beam.

**Never pre-map a state into a block update packet.** Our glass by the beacon rendered as a bare
azalea leaf while the same block placed anywhere else was fine, because `hide()` resent the *donor*
state instead of the server state. Polymer maps every `BlockState` written to a packet on its way
out, per player (`ByteBufCodecsEntriesMixin` on `Block.BLOCK_STATE_REGISTRY` →
`PolymerBlockUtils.getPolymerBlockState` → `BlockMapper`), so a donor handed back to it is mapped a
**second** time — and the second pass is not the identity. `BlockExtBlockMapper.toClientSideState`
sends anything that is not one of our `PolymerTexturedBlock`s through its `stateMap`, which
`BlockResourceCreator.requestBlockImpl` fills with one entry per donor pointing at that donor's look
for a client *without* the pack: for a leaves donor, `defaultBlockState().setValue(PERSISTENT, true)`
— a state `DefaultModelData.generateDefault` deliberately removes from the pool, so the pack never
gives it a model. The client stacks blockstate definitions (`BlockStateModelLoader` `putAll`s each
pack's variants into one map), so that state keeps *vanilla's* leaf model and our glass becomes a
leaf. The fix is to send `level.getBlockState(pos)` and let Polymer map it once, exactly as vanilla's
own block updates do; `send()` now logs an error for any state Polymer would re-map, and
`beaconResendMustCarryTheServerState` pins it.

**A white base one block tall is the column, not the layout.** `BeamWalk` is vanilla's walk quirk for
quirk, so the first section is as tall as the gap between the beacon and the first colour — and
vanilla, like us, draws section 0 from the beacon block's own bottom, where the beacon hides it. A
beam logged as `241 block(s), 16 of 16 segments` can only be a 1-block white section (1 + 15 × 16);
3 blocks of white would log 227 (2 + 1 + 14 × 16). The layout log now prints every segment as
`+offset h height #colour` so the column is readable straight off the server log, and
`beaconBeamLayout` asserts the tuples for the three-block case.

Known rough edges: panes in the column contribute their colour to the walk but are not swapped for
far players (glass only); a section whose height is not a power of two spends up to four segments on
it (8 + 4 + 2 + 1), so a busy column runs out of pool sooner than 256 blocks;
a column with more than 16 colour changes runs out of segments
and the beam stops there; and on a fresh chunk send a player briefly sees the default look until the
next 20-tick sweep, because `PolymerBlock#getPolymerBlockState` has no block position (Polymer maps
whole chunk palettes with it), so the per-player exceptions have to be pushed as explicit block
updates from the holder, which does know the positions.

## Tests

`./gradlew mods:moredyes:runGametest` (or `Run Tests.command`) runs the server-side game tests in
`metacraft.moredyes.gametest` on a throwaway test server and fails the build on any failure; the
JUnit-style report lands in `build/test-results/gametest.xml`. They cover what a vanilla client is
*sent* — every block state resolving to a real client state, the sheep being sent invisible, the
derived banner registry — plus drops and interactions (double slabs, beds, shulker contents,
concrete hardening, sheep dye/shear/death/vanilla-dye reset, candle tags). In a normal world an op
can run the same tests with vanilla's `/test runall` and watch the beacons. Pixels stay a visual
check with the vanilla client and `/moredyes showcase`.

## Layout

```
colors.json                         single source of truth
metacraft.moredyes.datagen.GeneratedAssets                 colours → textures, models, lang, loot, recipes, tags
metacraft.moredyes.MoreDyes         entrypoint: colours → content → resource pack
metacraft.moredyes.color            ModColor record, colors.json loader
metacraft.moredyes.content          Family enum, ColoredBlocks, ShapedBlocks (stairs/slabs +
                                    display holder), items, creative tab, ClientStates
metacraft.moredyes.sheep            SheepColors (attachment + events), SheepOverlay, SheepWoolRig
metacraft.moredyes.banner           BannerPatterns (derive + tint), DyeLoomGui (sgui)
metacraft.moredyes.mixin            Sheep shear/colour/breeding, Mob death loot, RegistryLoadTask
```

Generated assets are gitignored (they are recoloured Mojang textures); regenerate, don't edit.

## Reference

The stairs/slabs pattern (invisible shaped donor + item display per block) comes from
[craftycorvid/wool-polymer](https://github.com/craftycorvid/wool-polymer) (MIT), which also
provided the 26.2 build template.
