# ovvar promotional shots

Re-shot 2026-09-19 on `origin/rivals-main-seam` (7737ff41), the first build with the full 18-patch catalogue: Vlad's IN, IN (gold), Nyckeln 0x0/0x1/0x2 and KomMN, Måns's JGS, TMEIT and TMEIT Marshal and his 12×12 Ticket to my heart. Nyckeln'26 is gone from the mod and from every design here.

Taken by `OVVAR_PROMO=1 ./gradlew :mods:ovvar:runClientGameTest` against a real dedicated server, by a
vanilla client that gets everything through the auto-served resource pack. Source:
`mods/ovvar/src/gametest/java/metacraft/ovvar/clienttest/PromoShots.java`. How to run it: [README.md](README.md).

**Scene.** An ordinary generated overworld, fixed seed `ovvar-promo`, no superflat anywhere. Two stages
are chosen by reading the generator's noise over a 1024-block square and scoring each candidate by what
stands *west* of it, because the figures face east into the morning sun and every scene camera is east
of them looking west (sun over the camera's shoulder, no silhouettes).

* group stage — `512 / 64 / 416`, taiga, snowy windswept hills and a lake to the west
* cover stage — `160 / 64 / -240`, forest by a meadow and water, ~600 blocks away

**Figures.** Six mannequins, six different vanilla default skins (Ari, Noor, Efe, Sunny, Steve, Zuri;
mixed wide and slim), each with its own design of 8–13 patches drawn from all 16 plain patches and both seat patches, ordered so no two neighbours share a
colour: cerise (up) · silicon blue (up) · purple (rolled down) · black tailcoat · cerise (rolled down) ·
purple (up).

**Chapters shown — and one deliberately excluded.** Only `DATA` (cerise), `IT` (purple), `IT_KISEL`
(silicon blue) and `MEDIA` (black tailcoat) appear. `DATA_POLYMITER` and `IT_POLYMITER` are another
artist's reference textures, due to be removed from the mod, and are **on purpose in none of these
pictures** — see `SHOWN` in `PromoShots.java`. The six figures are therefore four chapters with two
repeats, told apart by design, skin and whether the top is up or rolled down.

Scene frames are 3840×2160; the four GUI frames are 2560×1440 (the window is resized to that size so
the screens lay themselves out for it, rather than being rendered large and drawn small in a corner).

---

## Hero — the poster frames

| file | what's in it | quality |
|---|---|---|
| **`01_hero_group_low_three_quarter.png`** | **THE HERO.** Low three-quarter camera at the south-east corner, 36° FOV. Nearest figure (cerise, Ari) large and sharp with every patch legible, five more receding along the path, snowy peak and taiga behind, room for type bottom-right. | **best of the set** |
| `02_hero_group_low_wide.png` | Same corner at 52°: more lawn and sky, the whole stage in frame. | good; figures smaller |
| `03_hero_group_telephoto.png` | 30° from further back — flattest, most catalogue-like. All six readable at once, snowy peaks behind. | good; the strongest alternative hero |
| `04_hero_vertical_stack.png` | Camera almost along the line and very low: figures stacked in depth near the centre, so a ~3:4 crop out of the middle is a portrait poster. | good for a vertical crop; near figure's face is turned away |
| `05_group_front.png` | Straight on at chest height: six whole garments side by side, cherry tree at the edge, mountains behind. Reference frame, not a hero. | good |

## Close-ups

| file | what's in it | quality |
|---|---|---|
| `06_closeup_shoulder_corner.png` | Shoulder and sleeve at once — **the patch wrapping round a body corner** is clearly visible on the near arm. | very good |
| `07_closeup_chest.png` | Silicon-blue chest, four front cells, texels square. | very good |
| `08_closeup_leg_rolled_down.png` | Rolled-down purple ovve: the top bunched at the belt, the leg carrying the whole design. | good; a background head intrudes at the top |
| `10_closeup_back_big_cell.png` | The black tailcoat's 16×16 back cell with the PUNG seat patch under it. | very good |
| `11_closeup_seat.png` | The seat patch across both legs from behind. | **weakest frame** — mostly flat dark cloth, PUNG near the bottom edge |

## Back

| file | what's in it | quality |
|---|---|---|
| `09_group_back.png` | The group from the west in late afternoon (sun still behind the camera): big back cells and PUNG seats. | fair; figures smallish, a grass bank behind |

## Cover model (single figure, cut-out friendly)

| file | what's in it | quality |
|---|---|---|
| `13_cover_front_three_quarter.png` | Full body, front three-quarter, 21 patches, clean sky and forest behind. | very good — the cut-out shot |
| `12_cover_back_three_quarter.png` | Same figure from behind: big back cell and PUNG seat, clean lawn behind. | good |
| `14_cover_in_the_landscape.png` | The same figure small in its meadow, water behind. | fair; figure is small by design |

## The player actually wearing one

| file | what's in it | quality |
|---|---|---|
| `15_player_third_person_front.png` | Third-person front. The player really is wearing a 21-patch Data ovve (legs slot + companion top), lake and snowy spruce behind. | very good |
| `16_player_walking.png` | Strafing, so the body turns off the head — a three-quarter a still pose can't give. | fair; cropped low |
| `17_player_third_person_back.png` | Third-person back: the PUNG seat patch on a moving player. | good |

## Items and screens

| file | what's in it | quality |
|---|---|---|
| `18_items_on_the_wall.png` | The eighteen patch items and the four garments in invisible item frames, 5×5 on smooth stone. | good |
| `19_wardrobe_screen.png` | `/ovvar stash`: the wardrobe, a full stash, a sewn design, the paper doll showing patches — over the scene, centred and large. | good |
| `20_sewing_dialog.png` | The stitching minigame on a sewing stand, ITK on the chest, group behind. | very good |
| `21_inventory_worn_ovve_pockets.png` | Survival inventory, cursor on the **worn** ovve in the legs slot: the vanilla bundle tooltip with eight stacks and a fill bar, plus the ovve's own lines (+2 Armor, zipped up, 20 patches, the patch list with Nyckeln 0x2 and IN (gold) in it, the use hints). | very good — the "an ovve is a bundle" shot |
| `22_inventory_ovve_tooltip.png` | Same screen, cursor on a spare Silicon-blue IT ovve in the pack. | good |

## Real armour over an ovve

| file | what's in it | quality |
|---|---|---|
| `23_armour_over_ovve_front.png` | Four figures: elytra (red wings, torso back to bare skin because the armour takes the chest slot), gold-trimmed diamond helmet+chestplate+boots, netherite chestplate, and — furthest — one with **no** chestplate so the ovve's own top shows. Patched legs and seat visible on all four. | good |
| `24_armour_over_ovve_back.png` | The same four from behind in the afternoon: elytra, trimmed diamond back, PUNG seats, a woodland structure on the ridge. | good |

## Shader packs (Iris 1.11.6 + Sodium 0.9.2 on MC 26.3)

Opt-in: `OVVAR_PROMO_SHADERS=<label> OVVAR_PROMO_SHADERPACK=<zip>` with Iris and Sodium added through
`-Dfabric.addMods`. The packs were run through the `OvvarShaderPatcher.jar` built from this commit first (`+ovvar` copies, re-patched 2026-09-19 because `ovvar.glsl` changed).

| file | what's in it | quality |
|---|---|---|
| `30_shaders_complementary_hero.png` | The hero framing under **Complementary Reimagined r5.9.3 +ovvar**. Soft shadows, bounce light, warm sky — patches render correctly. | **the most striking single image in the set** |
| `31_shaders_complementary_group_front.png` | Straight-on group, same pack. | very good |
| `32_shaders_complementary_closeup.png` | Shoulder close-up, same pack. | very good |
| `30/31/32_shaders_bsl_*.png` | Same three under **BSL v10.1.5 +ovvar**. Cooler, more contrast, volumetric light. | very good |
| `30/31/32_shaders_photon_*.png` | Same three under **Photon v1.3b +ovvar**. Cleanest, most neutral. | very good |
| `30/31/32_shaders_complementary_unpatched_*.png` | The control: the **unpatched** Complementary pack. | see the caveat below |
| `33_shaders_patched_vs_unpatched.png` | Labelled stacked comparison of the group under the patched and unpatched pack (stacked and diffed by hand afterwards; the script for it was not kept). | good — use this to make the point |

**Honest caveat on the unpatched control.** The unpatched pack does *not* strip the patches back to plain
overalls. What it does is place them wrongly: cells land on the wrong faces, some are duplicated across a
limb, and the wrap round corners breaks. 9.2 % of the pixels of the whole frame differ, and the
difference is concentrated entirely on the six garments (the background differs only by shader dither).
`33_shaders_patched_vs_unpatched.png` shows it plainly — it is a "the art is scrambled" comparison, not a
"the art is gone" one. Claiming the latter would be wrong.

---

## Best picks

* **Hero:** `01_hero_group_low_three_quarter.png` — vanilla rendering, works everywhere.
* **Hero, if a shader look is acceptable:** `30_shaders_complementary_hero.png`.
* **Vertical / portrait crop:** `04_hero_vertical_stack.png` (crop the centre ~3:4).
* **Cut-out single figure:** `13_cover_front_three_quarter.png`, with `12_…_back_…` for the back.
* **Detail:** `06_closeup_shoulder_corner.png` for the corner wrap, `10_closeup_back_big_cell.png` for
  the big cell and the seat.

## Known weak spots

* `11_closeup_seat.png` is the one frame worth re-shooting; the seat patch reads better in
  `10_closeup_back_big_cell.png` and `17_player_third_person_back.png` anyway.
* `09_group_back.png` puts the group a bit far from the camera and the backdrop east of the stage is a
  grass bank — the stage is scored for its *western* skyline, which is the side five of the six group
  frames use.
* `16_player_walking.png` crops the player low; `15` and `17` are the usable player frames.

The BSL and unpatched-Complementary invocations end with the ordinary client pixel test failing under the shader look (`it (lilac rim): 0 matching pixel(s)`); that test runs after the promo frames in the same invocation, and all the frames had been written by then.
