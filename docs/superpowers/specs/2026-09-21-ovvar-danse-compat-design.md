# Ovvar × Danse: gestures in a patched ovve

Date: 2026-09-21. Branch: `ovvar-danse`, off `upstream/minigame`.

## Problem

Danse (https://github.com/tomalbrc/danse, 2.6.0 for 26.3, Fabric + Polymer) plays gestures by
hiding the real player (empty equipment packet to watchers, invisible flag) and animating a
stand-in built of item displays: one per body part, one pixel per skin texel, colours carried in
`custom_model_data`. It draws armour itself: `TextureCache.armorCustomModelData(part, stack, inner)`
reads the *server-side* item's `Equippable` asset id, opens the equipment JSON and its layer PNGs
from the Polymer pack builder, and samples them as 64×32 armour textures.

An ovve fails every step of that:

- ovvar's layer textures are 128×64 (2 texels per skin texel), so Danse samples the wrong quadrant;
- the per-combination asset, the dye colour that carries patch ranks, the trim that carries one more
  patch, the companion top's look and the virtual cuffs all live in the Polymer-side stack, which
  Danse never sees; the raw server stack carries only the base chapter asset;
- the base asset's dyeable preview layer has no colour on the server stack, and Danse multiplies the
  patch-rank colour in as a tint when it does;
- ovvar re-sends equipment on its own schedule (`Combos` after a pack load, `OvveFeet.sendCuffs` when
  a viewer's cuff dye changes), which would re-dress the invisible real player mid-gesture;
- Danse ends a gesture by resending the raw equipment (an empty feet slot); ovvar's cuff cache
  believes the viewer still has the cuffs, so leg patches in the boots channel stay hidden.

## Design

Everything is on ovvar's side, in a package `metacraft.ovvar.compat.danse`, active only when
Danse is loaded. Danse is `modCompileOnly` for ovvar, never a `depends`. Mixins into Danse are in
their own config (`ovvar-danse.mixins.json`) with a mixin plugin that applies them only when
`FabricLoader.isModLoaded("danse")`. No class outside that package references Danse types, and
the package's entry points are reached only behind `isModLoaded` checks.

### 1. `DansePixels` — the armour pixels Danse asks for

Input: a server-side stack and Danse's `(BodyPart part, boolean inner)`.

Which stacks are ours:

| Stack | Where | What Danse must draw |
|---|---|---|
| `OvveItem` | legs | the bottom: chapter base (rolled-down variant if the top is down) plus every sewn bottom placement |
| `OvveTopItem` (companion top) | chest | the top: chapter base plus every sewn top placement |
| chestplate with `WRAPPED_TOP` | chest | the top underneath, Danse's own chestplate pixels on top |
| boots with `WRAPPED` | feet | nothing of ours — the boots channel is a packet trick; legs are drawn from the ovve directly |

Output: a `CustomModelData` in exactly the order Danse builds it: for each of
`MinecraftSkinParser.DIRECTIONS` (S, N, W, E, U, D) the part's face pixels, `colors` and `flags`
(visible) parallel, via Danse's own `MinecraftSkinParser.extractTextureRGB`. Use the **CLASSIC**
(64×64) map with a synthesized 64×64 image in the classic skin layout: the right limbs and body in
their armour-layout positions, the **left** limbs drawn un-mirrored at the classic left-limb
positions (rows 48–64), which is how ovvar's datagen already thinks about left-side art. Read
`datagen/Tex.java` and `GeneratedAssets.java` to see where the base garment keeps its mirrored
left-limb art ("one strip up") and how placement textures are marked per side; reuse that code
where it can be shared, do not duplicate the layout maths.

Compositing, at 2× first, then downsampled to 1× by averaging the 2×2 block (alpha-weighted; a
block is visible if any texel is):

1. the chapter's base garment texture for the piece (the pack's `textures/entity/equipment/<layer>/<chapter>/<top|bottom|...>.png`, read from ovvar's own generated resources by classloader, not through Danse's builder);
2. each sewn placement in sewing order, as the placement texture datagen already makes (`patch/<cell>/<patch>.png`), the same layering as the equipment JSON would stack — later placements on top, `BACK_BIG` under the two small back cells;
3. no dyeable preview layer, no trim: those exist only to smuggle patches past a vanilla renderer, and here we have the whole list.

Which layer texture goes where (Danse's rule, keep it): for BODY, `inner` = the legs item and
`outer` = the chest item; for legs, `inner` = legs item, `outer` = feet item; for arms both are the
chest item. Ovvar textures are per piece, so the top answers body/arms and the bottom answers
legs/body-inner; an ovve asked for an arm, or a top asked for a leg, returns `CustomModelData.EMPTY`.

Cache by `(chapter, piece, nercabbad, sewn placements, part, inner)`; placements are the key,
not the stack.

### 2. Mixin into Danse's `TextureCache.armorCustomModelData`

- `@Inject(at = HEAD, cancellable)`: `OvveItem` or `OvveTopItem` → return `DansePixels` result.
- `@ModifyReturnValue` (or inject at RETURN): chestplate with `WRAPPED_TOP` → merge ovvar's top
  pixels *under* Danse's result (a pixel of ours shows where Danse's is not visible). Danse's
  `merged` is private; reimplement the 10-line merge rather than reach in.

### 3. Gesture etiquette — `DanseHooks`

- `DanseHooks.gesturing(LivingEntity)` → true when Danse is loaded and
  `GestureController.GESTURE_CAMS` holds the player's UUID. `Combos`'s resend-after-pack-load and
  `OvveFeet.sendCuffs` skip a gesturing wearer. (`OvveTop`'s companion logic is server-side state
  and keeps running; that is fine, Danse takes the equipment once at gesture start.)
- Mixin at TAIL of `GestureController.onStop(GestureCameraHolder)`: `OvveFeet.forget(player)` (drop
  the SENT map entry so the next tick re-sends cuffs to every tracker) and ask `Combos` to re-send
  the wearer's equipment to trackers, the same path it uses after a pack load. Check whether
  `ClientboundSetEquipmentPacket` built from raw stacks is Polymer-transformed on the way out
  (ovvar already relies on this in `Combos`); do the same.

### 4. Dev runtime and the screenshot proof

- Danse in the ovvar dev/test runtime only: `modCompileOnly` + `modLocalRuntime`. Prefer
  cursemaven (`curse.maven:danse-<projectId>:<fileId>`, the 26.3 file; project id is on the
  CurseForge page) with the cursemaven repository added to the root build. If no 26.3 file is on
  cursemaven, build Danse 2.6.0 from source (its jar bundles bil, DialogUtils, sgui, placeholder-api,
  map-canvas-api) and commit it under `mods/ovvar/libs/` with its LGPL notice and a line in the README
  saying where it came from. Danse's transitive Polymer modules are already in the workspace.
- A second client game test, `OvvarDanseClientTests` (registered next to `OvvarClientTests`; it
  returns at once when Danse is not loaded). The Tester wears a DATA ovve, top up, with four front
  patches (the same four as the existing test) and two sleeve patches and two leg patches, runs
  `/gesture <slowest built-in gesture>` and screenshots the stand-in from Danse's gesture camera at
  1920×1080 a few ticks in. Assertions: itk green and IT lilac are present in the subject box (reuse
  the helpers), and the real player's entity is not drawing armour (no cerise outside the stand-in's
  box, or simply that the frame contains exactly one cerise blob). Also take a screenshot after the
  gesture ends and assert the cuffs are back (the leg patch colours in the boots channel are present).
- The system property `ovvar.danse.compat=false` disables the compat layer (mixin plugin + hooks) so
  the same test produces the **before** picture. Run it both ways; commit both screenshots to
  `mods/ovvar/docs/danse/before.png` and `after.png` (and `after_gesture_end.png`), and reference
  them from a new README section "Danse gestures".
- A server-side game test (`OvvarGameTests` style) for `DansePixels`: for a DATA ovve with itk on
  FRONT_TOP_LEFT, the BODY/SOUTH block contains itk green in the top-left cell and cerise elsewhere;
  for a left-sleeve patch the LEFT_ARM/WEST... (the outer face) block carries it and RIGHT_ARM does
  not; downsampling keeps an 8×8 patch inside its 4×4 cell.

## Out of scope

Persistent Danse statues (`/summon danse:player_model`) get the same pixels for free through the
same `TextureCache` path; no extra work, but no test either. Danse's HUD player model is off in
Danse itself. Nothing changes for clients without Danse.
