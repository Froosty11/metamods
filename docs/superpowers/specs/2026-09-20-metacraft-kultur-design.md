# Kultur — chapter culture for METAcraft: design

Date: 2026-09-20. Module `mods/metacraft-kultur`, mod id `kultur`. Minecraft 26.3, Fabric,
Polymer 0.18. Server-side; players need only the auto-served resource pack.

## Purpose

A home for the student-culture content that does not belong to a more specific mod: banner and
shield patterns for the chapters and their clubs, paintings, and later the chapter drinks (on
Patbox's Brewery). It is the successor of PolymITer (Froosty11/PolymITer, Minecraft 1.21.10), whose
ovvar and patches already moved to `mods/ovvar`. It is chapter-agnostic: the catalogue is keyed by
chapter, and the first release carries IT's content only. Data and Media are present as empty
entries so their students can see where their art goes.

This first slice: banner and shield patterns, paintings. Later slices, out of scope here: the drinks
as a Brewery datapack plus custom consumption effects, loot injection, a chapter merchant.

## Content in this slice

| chapter | banner patterns (free in loom) | pattern items | paintings |
| --- | --- | --- | --- |
| it | itk, qmisk, tmeit | pirkko | draken — "The Guardian of Kistan", Emelie Stark, 3×4 |
| data | — | — | — |
| media | — | — | — |

Art comes from PolymITer's `assets/polymiter/textures/entity/banner/*.png`, `entity/shield/*.png`,
`item/pirkko_banner_pattern.png` and `painting/draken.png`.

"Free in loom" means the pattern is in the `minecraft:no_item_required` banner-pattern tag: any
loom offers it. A "pattern item" is a Polymer item carrying `minecraft:provides_banner_patterns`
pointing at a tag with just that pattern, the way vanilla's globe or flower pattern items work.
No loot table gives out the pattern items yet; they come from creative or commands until a later
slice decides how they are found.

## Catalogue

`src/main/resources/kultur.json`, the single source of truth. Datagen reads it; the runtime reads
it to register items. Shape:

```json5
{
  "chapters": [
    {
      "id": "it",
      "name": "IT",
      "patterns": [
        { "id": "itk",    "name": "ITK" },
        { "id": "qmisk",  "name": "QMISK" },
        { "id": "tmeit",  "name": "TMEIT" },
        { "id": "pirkko", "name": "Pirkko", "item": true }
      ],
      "paintings": [
        { "id": "draken", "title": "The Guardian of Kistan", "author": "Emelie Stark", "width": 3, "height": 4 }
      ]
    },
    { "id": "data",  "name": "Data",  "patterns": [], "paintings": [] },
    { "id": "media", "name": "Media", "patterns": [], "paintings": [] }
  ]
}
```

Ids are `[a-z0-9_]`, unique per kind (patterns among patterns, paintings among paintings) — a
pattern id is the registry path, not prefixed by chapter, so ids stay short and moredyes' derived
names stay readable. `item` defaults
to false. Display names are plain strings; the lang file is English only, matching ovvar and
moredyes.

Art, beside the catalogue, under `src/main/resources/art/kultur/<chapter>/`:

| file | size | required |
| --- | --- | --- |
| `banner/<pattern>.png` | 64×64 or an integer multiple (PolymITer's are 128×128); the art on the flag's front face, the 20×40 region at (1,1) in vanilla's layout, scaled with the file | yes |
| `shield/<pattern>.png` | 64×64 or the same multiple; the art in the shield's 10×20 pattern region at (2,2), scaled with the file | no — derived from the banner mask when absent |
| `item/<pattern>.png` | 16×16 | yes for `item: true` patterns |
| `painting/<painting>.png` | 16 px per block, so 48×64 for 3×4 | yes |

Registry ids are `kultur:<pattern>` and `kultur:<painting>`; asset ids are the same.

## Datagen

`./gradlew runDatagen` (root `setupRunDatagen("kultur")`, as ovvar and moredyes) writes into
`src/main/generated`, which is generated and git-ignored (`.gitignore`'s `generated/`); every
contributor regenerates it locally, and only the catalogue and the hand-drawn art are committed:

- `data/kultur/banner_pattern/<p>.json` — `asset_id: kultur:<p>`, `translation_key:
  block.kultur.banner.<p>`.
- `data/minecraft/tags/banner_pattern/no_item_required.json` (`replace: false`) — every free pattern.
- `data/kultur/tags/banner_pattern/pattern_item/<p>.json` — one per item-gated pattern.
- `data/kultur/painting_variant/<q>.json` — width, height, `asset_id`, `title` and `author` as
  translatable texts.
- `data/minecraft/tags/painting_variant/placeable.json` (`replace: false`) — every painting.
- `assets/kultur/textures/entity/banner/<p>.png`, `assets/kultur/textures/entity/shield/<p>.png`
  — normalised into vanilla's layout (`Masks`): a banner keeps only the front face and gets that
  face copied onto the back face at (22,1), the way vanilla's own pattern textures are, so the
  back of a banner shows the pattern too; a shield keeps only its 12×22 face at (1,1). Both are
  transparent everywhere else, which also drops stray pixels in the source art. A shield with no
  hand-drawn file is the banner's front face at half size (exactly the shield region's size): alpha
  is the max of each 2×2 block, colour the mean of its opaque texels.
- `assets/kultur/textures/painting/<q>.png` — copied.
- `assets/kultur/textures/item/<p>.png`, `assets/kultur/models/item/<p>_banner_pattern.json`
  (`item/generated`, layer0 the texture) and `assets/kultur/items/<p>_banner_pattern.json` — for
  item-gated patterns.
- `assets/kultur/lang/en_us.json` — `block.kultur.banner.<p>.<color>` for all 16 vanilla dye
  colours (`"<Color> <Name>"`), `item.kultur.<p>_banner_pattern` (`"<Name> Banner Pattern"`),
  `painting.kultur.<q>.title` and `.author`.

Datagen fails with a message naming the entry when: a banner PNG is missing or not 64×64; an
item-gated pattern has no item PNG; a painting PNG is missing or not `16·width × 16·height`; an id
repeats or is not `[a-z0-9_]+`. `processResources` refuses to build when the generated assets
folder is empty and the task is not `runDatagen`, as in moredyes.

The shield scaler lives in a small class of its own (`ShieldMask`) so a unit test can exercise it.

## Runtime

`metacraft.kultur.Kultur` (ModInitializer):

1. Loads the catalogue (`Catalogue.load()`, a record tree parsed from `kultur.json` with a codec;
   shared with datagen).
2. For each item-gated pattern registers `kultur:<p>_banner_pattern`: a `PatternItem extends Item
   implements PolymerItem`, vanilla item `minecraft:flower_banner_pattern` on the client, item
   model `kultur:<p>_banner_pattern` via the item-model component, max count 1, rarity rare,
   `PROVIDES_BANNER_PATTERNS` = `TagKey(kultur:pattern_item/<p>)`. The vanilla loom then does the
   rest.
3. `PolymerResourcePackUtils.addModAssets("kultur")` and `markAsRequired()`.
4. Logs the chapter, pattern and painting counts.

Banner patterns and painting variants are dynamic registries synced to the client, so vanilla
clients receive them from the data files alone. moredyes, when present, derives its coloured
variants from every loaded pattern and finds our masks through Polymer's builder; nothing here
references moredyes.

No mixins. No config.

## Tests

- **Game test** (`fabric-gametest` entrypoint, run as ovvar's: `-Dfabric-api.gametest=true`
  with `runServer`): for every catalogue pattern, the banner-pattern registry has it; free patterns
  are in `no_item_required`; each item-gated pattern has its item registered and the item's
  provides-patterns tag resolves to exactly that pattern; every painting is in the registry with the
  catalogue's size and in `placeable`. And Polymer's built pack contains the banner, shield and
  painting textures and the item model for each entry.
- **Unit test** (`test` source set): `ShieldMask` on the ITK banner gives a 64×64 image with alpha
  only inside the 12×22 face at (1,1) and nothing elsewhere; a fully transparent banner gives a
  fully transparent shield.
- **Screenshot check**, once, by hand before the PR: ovvar's client game test recipe
  (`runClientGameTest`) pointed at a world with an ITK banner and a Pirkko shield, and the PNG
  looked at, as the project's rule says.

## README

`mods/metacraft-kultur/README.md`, written for a Data or Media student new to the repo: what the mod
is and what it is not (no loot, no drinks yet, no Data/Media content yet); adding a banner pattern
(one catalogue line, one 64×64 PNG, optionally a shield PNG and an item PNG, then `./gradlew
runDatagen`); adding a painting (catalogue line, PNG at 16 px per block); the pixel regions for
banner and shield masks; where names come from; how to test locally.

## Build wiring

- `settings.gradle`: `include "mods:metacraft-kultur"`.
- `gradle.properties`: `kultur_version = 0.1.0`.
- `mods/metacraft-kultur/build.gradle`: `version = project.kultur_version`,
  `setupRunDatagen("kultur")`, `addPolymerDependency()`, `polymer-resource-pack`,
  `polymer-autohost`, the processResources guard.
- `fabric.mod.json` depends: fabricloader, minecraft `>=26.3-rc.1 <26.4`, java 25, fabric-api,
  polymer-core, polymer-resource-pack. Entrypoints: main, fabric-datagen, fabric-gametest.
- dist picks the module up automatically through `initDist()`.
