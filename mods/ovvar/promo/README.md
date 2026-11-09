# Ovvar promo screenshots and poster

This folder has everything behind the Ovvar promo poster (the "40,533,074,… ways to patch one ovve"
infographic):

| what | where |
|---|---|
| The code that takes the screenshots | `../src/gametest/java/metacraft/ovvar/clienttest/PromoShots.java` |
| What every frame shows, and which ones are good | [SHOTS.md](SHOTS.md) |
| The poster (HTML + CSS) | [poster/poster.html](poster/poster.html) |
| Turns the poster into a PNG | [poster/render.sh](poster/render.sh) |

The screenshots themselves are not committed (they are 3840×2160 and add up to ~250 MB). You make
them again by running the test below.

## 1. Taking the screenshots

`PromoShots` is a Fabric **client game test**. It starts a real Minecraft client and a real dedicated
server, generates an ordinary overworld on a fixed seed, sets up six mannequins wearing ovves, and
takes about 25 frames: hero shots, close-ups, the player wearing one, the item wall, the wardrobe
and sewing screens, and armour worn over an ovve. Because the client is a plain vanilla one (no
Polymer client mod), the frames show exactly what a player joining the server would see.

It does nothing unless `OVVAR_PROMO` is set, so an ordinary `runClientGameTest` is not affected.

You need Java 21, a machine with a display (a window opens and runs for a few minutes; don't touch
it), and to run from the repo root:

```sh
./gradlew :mods:ovvar:runDatagen        # once, and again whenever the patch art changes
OVVAR_PROMO=1 OVVAR_PROMO_OUT="$PWD/promo-shots" ./gradlew :mods:ovvar:runClientGameTest
```

The frames land in `OVVAR_PROMO_OUT` (default `/tmp/ovvar-promo/shots`).

Options (all environment variables):

| variable | effect |
|---|---|
| `OVVAR_PROMO` | Turns the promo run on. Any value. |
| `OVVAR_PROMO_OUT` | Output folder. |
| `OVVAR_PROMO_HD` | 1920×1080 instead of 3840×2160, which is faster for trying things out. |
| `OVVAR_PROMO_SEED` | World seed (default `ovvar-promo`). The two stages are found from the terrain, so a new seed gives new backdrops. |
| `OVVAR_PROMO_SHADERS` | A label for a shader run, see below. |
| `OVVAR_PROMO_SHADERPACK` | The shader pack zip for a shader run. |

Where to change things in `PromoShots.java`:

- **Which chapters appear:** `SHOWN`. Leave `DATA_POLYMITER` and `IT_POLYMITER` out. They are another
  artist's reference textures and are due to be removed from the mod.
- **The six figures** (skin, chapter, patches, top up or rolled down): `LINE_UP` (and `COVER` for the single cover model).
- **Cameras and times of day:** one method per group of frames. Each frame is a `camera(...)`
  followed by `shot(ctx, "<file name>")`.

### Shader frames (optional)

The `30_`–`33_` frames are taken under Iris shader packs. This is one run per pack:

1. Download Iris and Sodium for Minecraft 26.3 from Modrinth (we used Iris 1.11.6 and Sodium 0.9.2),
   plus the shader pack zips (Complementary Reimagined, BSL, Photon …). Keep them **outside** the
   repo's run folder, since the test rebuilds that folder on every run.
2. Patch each pack so the ovve patches render under it:
   ```sh
   ./gradlew :mods:ovvar:patcherJar
   java -jar mods/ovvar/build/libs/OvvarShaderPatcher.jar /path/to/Pack.zip
   ```
   This writes a `+ovvar` copy next to the original. Patch again whenever
   `ovvar.glsl` changes.
3. Run:
   ```sh
   JAVA_TOOL_OPTIONS="-Dfabric.addMods=/path/to/sodium.jar:/path/to/iris.jar" \
   OVVAR_PROMO=1 OVVAR_PROMO_OUT="$PWD/promo-shots" \
   OVVAR_PROMO_SHADERS=complementary OVVAR_PROMO_SHADERPACK="/path/to/Complementary+ovvar.zip" \
   ./gradlew :mods:ovvar:runClientGameTest
   ```
   A shader run only takes the hero, the group front and one close-up, named after the label.

Some packs (BSL, and any **unpatched** pack) make the command exit with an error at the very end.
That error comes from the ordinary pixel test that runs after the promo frames, not from the promo
run. The frames have already been written by then.

Photon needs compute shaders, so it doesn't work on a Mac.

## 2. Making the poster

The poster uses seven of the frames. Copy them from your output folder into `poster/img/` (git
ignores them there):

```
06_closeup_shoulder_corner.png        21_inventory_worn_ovve_pockets.png
08_closeup_leg_rolled_down.png        23_armour_over_ovve_front.png
10_closeup_back_big_cell.png          31_shaders_bsl_group_front.png
31_shaders_complementary_group_front.png
```

Then render it:

```sh
mods/ovvar/promo/poster/render.sh    # writes poster/poster.png, 2480×3508 (A4 at 300 dpi)
```

It uses headless Chrome. On Linux or Windows, point `CHROME` at your Chrome or Chromium binary. You
can also just open `poster.html` in a browser while editing it.

The patch art in `poster/img/patches/` is copied from the mod's textures. The font in `poster/fonts/`
is the OFL-licensed Minecraft font.

## Rules for what the poster says

- **The big number** is every possible design of one ovve, on one line:
  `(P+1)^31 × ((P+1)^2 + S)`, where P is the number of plain patches and S the number of seat patches.
  The +1 is "no patch" in that cell. With 16 plain and 2 seat patches that is `17^31 × 291`. Work it
  out again whenever patches are added or removed, and update the line under the number to match.
- Only count and show the chapters in `SHOWN` (DATA, IT, IT_KISEL, MEDIA). Never the PolymITer ones.
- "A first" is only said with "as far as we know", and only about **outfits players compose
  themselves that a vanilla client can see**. Drawing from a dye colour with a core shader has been
  done before (Fancy Pants), so the poster doesn't claim that as new.
- Don't advertise the game tests on the poster.
