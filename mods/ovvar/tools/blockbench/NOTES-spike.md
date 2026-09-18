# Task 0 spike — the six-cube ovve in Blockbench 5.1.6

Scratch notes. Tasks 2, 7 and 8 quote them; Task 9 deletes the file.

Blockbench 5.1.6 (`/Applications/Blockbench.app`, `package.json` `"version": "5.1.6"`).
Source extracted with `@electron/asar` + the bundle sourcemap: **wrote 369 sources to /tmp/bbsrc**,
matching the brief's expected count exactly.

## Step 2 — the armour table, read out of Blockbench's own skin format

`/tmp/bbsrc/.._js_formats_minecraft_skin.ts`, `skin_presets.armor_main` (lines 1444–1462) and
`skin_presets.armor_leggings` (lines 1500–1518), verbatim, bedrock-style `origin`/`size`:

```
{"name": "Chestplate",       "origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16], "inflate": 1.01}
{"name": "Right Arm Armor",  "origin": [-8, 12, -2], "size": [4, 12, 4], "uv": [40, 16], "inflate": 1}
{"name": "Left Arm Armor",   "origin": [ 4, 12, -2], "size": [4, 12, 4], "uv": [40, 16], "inflate": 1, "mirror": true}
{"name": "Belt",             "origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16], "inflate": 0.51}
{"name": "Right Leg Armor",  "origin": [-3.9, 0, -2], "size": [4, 12, 4], "uv": [0, 16], "inflate": 0.5}
{"name": "Left Leg Armor",   "origin": [-0.1, 0, -2], "size": [4, 12, 4], "uv": [0, 16], "inflate": 0.5, "mirror": true}
```

This is the table **Task 8 builds**, with three corrections:

- `inflate` **1.01 → 1.0** and **0.51 → 0.5**. Blockbench fudges them so the armour does not
  z-fight the skin underneath; the game uses `Spot.inflate` exactly. Confirmed in the mod:
  `mods/ovvar/src/main/java/metacraft/ovvar/content/Spot.java:112`
  — `return piece == Piece.TOP ? 1.0 : 0.5;`
- the **±0.1 x-fudge on the legs → 0** (`origin[0]` `-3.9`/`-0.1` become `-4`/`0`). Same reason.
  Consequence to expect and ignore: with the fudge gone the two inflated leg boxes overlap by
  1 unit in x (right leg `x −0.5 … 4.5`, left leg `x −4.5 … 0.5`), which is exactly what the
  fudge existed to avoid. The game has the same overlap and does not care.

## Step 3 — bedrock `origin`/`size` → Blockbench `from`/`to`

`formats/bedrock/bedrock.js`, `parseCube`, verbatim:

```
base_cube.from.V3_set(s.origin)
base_cube.from[0] = -(base_cube.from[0] + s.size[0])
base_cube.to[0] = s.size[0] + base_cube.from[0]
```

So `from = [-(origin[0] + size[0]), origin[1], origin[2]]` and `to = from + size`.
Converted — **this is the table Task 8 uses**:

| cube | `from` | `to` | `uv_offset` | `inflate` | `mirror_uv` | texture |
|---|---|---|---|---|---|---|
| `ovve_body` | `[-4, 12, -2]` | `[4, 24, 2]` | `[16, 16]` | `1.0` | `false` | A(top) |
| `ovve_arm_r` | `[4, 12, -2]` | `[8, 24, 2]` | `[40, 16]` | `1.0` | `false` | A(top) |
| `ovve_arm_l` | `[-8, 12, -2]` | `[-4, 24, 2]` | `[40, 16]` | `1.0` | `true` | B(top) |
| `ovve_belt` | `[-4, 12, -2]` | `[4, 24, 2]` | `[16, 16]` | `0.5` | `false` | A(bottom) |
| `ovve_leg_r` | `[0, 0, -2]` | `[4, 12, 2]` | `[0, 16]` | `0.5` | `false` | A(bottom) |
| `ovve_leg_l` | `[-4, 0, -2]` | `[0, 12, 2]` | `[0, 16]` | `0.5` | `true` | B(bottom) |

**Blockbench's +x is the wearer's right** — the right arm lands at `x 4 … 8`. Confirmed by the
conversion (`Right Arm Armor` origin `-8` → `from[0] = -(-8 + 4) = 4`) and by the render: the
Data ovve's bib straps and its back logo come out the right way round from the front and back.

Alex arms are 3 px wide *in the skin*, but the **armour** model is always 4 wide — there is no
slim armour model, so **this table never changes**.

## Correction to the brief: the UV resolution is 64 × 32, not 128 × 64

The brief's Step 4 says `Project.texture_width = 128; Project.texture_height = 64;`. **That is
wrong and it breaks the model.** Blockbench box UV is computed in *cube-size units* offset by
`uv_offset` and only then divided by the UV resolution — `Canvas.updateUV` in
`/tmp/bbsrc/.._js_outliner_types_cube.js:1281`:

```
let uv = [ f.from[0] + element.uv_offset[0], f.from[1] + element.uv_offset[1], … ]
…
let pw = Project.texture_width, ph = Project.texture_height;
if (tex && Format.per_texture_uv_size && …) { pw = tex.getUVWidth(); ph = tex.getUVHeight(); }
```

The mod's armour layers are the **64 × 32 vanilla armour layout drawn at 2× resolution**: of the
2048 2×2 blocks in `humanoid/data/top.png`, 2044 are uniform — it is an upscale with only four
blocks of genuine 128-res detail. The offsets `[16,16]` / `[40,16]` / `[0,16]` are skin texels,
so the UV resolution must stay **64 × 32** while the image stays 128 × 64.

- Set `Project.texture_width = 64`, `Project.texture_height = 32`.
- On a format with `per_texture_uv_size` (`free` has it; a custom `ModelFormat` should set it or
  leave it off), also set `texture.uv_width = 64`, `texture.uv_height = 32`, or `getUVWidth()`
  falls back to the project values anyway.
- Evidence: `/tmp/bbspike/M_res.png` — left pane UV res 64 × 32 (a correct ovve), right pane the
  brief's literal 128 × 64 with the same offsets (arms gone, body and legs reading garbage).

**Task 8 must use 64 × 32 as the UV resolution.** The `128, 64` in the brief's Step-4 assertion
is the *image* size and should be checked as `[texture.width, texture.height]`, not as
`[Project.texture_width, Project.texture_height]`.

## Correction to the brief: the Skin format cannot host this spike

`skin` is `single_texture: true` (`.._js_formats_minecraft_skin.ts:307`), so it cannot hold
`top.png` and `bottom.png` at once, let alone the two mirror-strip variants. Used `free` instead
(`.._js_formats_generic.ts:22` — `optional_box_uv: true`, `per_texture_uv_size: true`), which
keeps per-cube `box_uv`. Verified live: all six cubes came back `box_uv === true` with the
`mirror_uv` / `inflate` / `uv_offset` asked for. Task 8's own `ModelFormat` (`box_uv: true`) is
fine; it just must not be `skin`.

Both loaders the brief leans on exist on this build:
`typeof Texture.prototype.fromPath === 'function'`, `typeof Texture.prototype.fromDataURL === 'function'`.

## Step 6 / Step 7 — the six answers

1. **Does the body's front/back art land on the right faces?** **Yes.** The bib and its two
   shoulder straps are on the north face and the section logo (the Data δ) is on the south face,
   both the right way up and not flipped, matching `07_hero_lineup_back.png`.
2. **Does the right arm show the right sleeve's art unmirrored?** **Yes.** `ovve_arm_r` reads
   `north = u 44…48, v 20…32` — increasing u, no flip — which is the vanilla armour sleeve rect.
3. **Does the left arm show the *same strip* mirrored?** **Yes, and that is the bug.** With
   texture A on both arms, `ovve_arm_l` reads the *same* `u 40…56` strip with every face reversed
   in u and east/west swapped (`north = 48,20,44,32`; `east = 52,20,48,32` = the right arm's west
   rect flipped). A plain `mirror_uv` cube on texture A shows the **right** arm's art flipped, not
   the left arm's own art. Seen directly in `/tmp/bbspike/A_arms_front.png`: the two sleeves are
   horizontal mirror images of each other.
   **With the mirror strip (texture B) the left limb shows its own art the right way round — yes,
   this is the whole spike.** Texture B is A with skin rows 0…16 copied onto rows 16…32 for the
   limb columns (`Spot.MIRROR_SHIFT = 16` in `Spot.java:170` — the javadoc reads "how far up the mirror strip sits
   from the limb boxes"), which puts the left limb's pre-mirrored art where the mirrored cube
   reads. Proof, on committed art whose strip genuinely differs
   (`humanoid_leggings/data_polymiter/bottom_nercabbad.png`, 152 differing texels in the leg
   strip): applying texture B to `ovve_leg_l` changed **37 434** rendered pixels on the left leg
   and **0** on the right leg. `/tmp/bbspike/M_nerc.png` (A | B) and
   `/tmp/bbspike/M_nerc_strips.png` (base strip | mirror strip).
   Caveat worth carrying forward: on a **plain** garment the strip is nearly a no-op — for
   `humanoid/data/top.png` only 36 of 1024 arm-strip texels differ (shading only) and the leg
   strip is byte-identical. A plugin regression test must pick art whose strip actually differs,
   or it will pass with the strip copy deleted.
4. **Does the leg's inflate 0.5 read visibly thinner than the arm's 1.0?** **Yes.** Read off the
   built three.js geometry, not by eye: arm bounding box `6 × 14 × 6`, leg `5 × 13 × 5` — one
   model unit (1/16 block) narrower and shallower per axis. Visible as a step in at the waist.
5. **Any face rotated or off by a texel?** **No.** Every one of the 36 computed face rects is
   integer skin texels and matches the vanilla armour layout exactly — e.g. body
   `north = 20,20,28,32`, `south = 32,20,40,32`, `up = 28,20,20,16`; right arm
   `north = 44,20,48,32`; legs `north = 4,20,8,32`. No rotation anywhere, no half-texel drift.
   (This only holds at UV resolution 64 × 32 — see the correction above.)
6. **Does the model's silhouette match the screenshots?** **Yes.** Torso + sleeves one unit
   proud of the skin, a hard step in at the belt, legs half a unit proud, sleeves and torso
   merging into one block across the shoulders — the same outline as
   `01_hero_lineup_front.png`, `07_hero_lineup_back.png` and `04_closeup_torso_sleeve.png`.

## How this was actually driven (no GUI operator)

The project was written as `.bbmodel` JSON by hand and opened in the real Blockbench 5.1.6, then
driven through the renderer's console over the Chrome DevTools Protocol
(`--remote-debugging-port=9222` + `Runtime.evaluate`) — the same objects the brief's console
snippets use (`Cube`, `Texture`, `Project`, `Canvas`, `Preview`, `Screencam`), not a simulation.
Screenshots are Blockbench's own `Screencam.screenshotPreview`, i.e. *View › Screenshot Model*.

Two environment gotchas, for whoever runs this next:

- The harness exports `ELECTRON_RUN_AS_NODE=1`. Blockbench inherits it, starts as plain Node,
  tries to `require()` the `.bbmodel` and exits 0 with no window. Launch with
  `env -u ELECTRON_RUN_AS_NODE /Applications/Blockbench.app/Contents/MacOS/Blockbench …`.
- The Step-7 canvas trick was **not** used. The strip copy was done on raw RGBA instead, because
  `CanvasRenderingContext2D` premultiplies alpha and would destroy the RGB of alpha-0 texels —
  the brief flags this itself, and Task 7 must do the copy on the image type.

Screenshots: `/tmp/ovvar-spike-front.png`, `/tmp/ovvar-spike-back.png`,
`/tmp/ovvar-spike-mirrored.png`; supporting montages under `/tmp/bbspike/evidence/`.
