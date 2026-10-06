# Kultur

METAmods module `mods/metacraft-kultur` (mod id `kultur`). Chapter culture for vanilla clients:
banner and shield patterns for the chapters and their clubs, and paintings. Fabric +
[Polymer](https://polymer.pb4.eu), Minecraft 26.3, Java 25. Players need only the auto-served
resource pack; no client mod. The successor of PolymITer (the IT chapter's 1.21.10 mod this content
comes from); the ovvar and patches from there live in `mods/ovvar`.

## What is here, and what is not

IT's content: ITK, QMISK and TMEIT banner patterns free in any loom, the Pirkko pattern behind a
pattern item ("gated", below — `kultur:pirkko_banner_pattern`, from creative or `/give` for now),
and Emelie Stark's painting "The Guardian of Kistan" (3×4). Data and Media have their places in the
catalogue and nothing in them yet — that is what this file is for.

Not here yet, on purpose: loot tables for the pattern items, the chapter drinks (they will be a
Patbox's Brewery datapack in this module), any Data or Media art.

With `moredyes` on the same server every pattern here also comes in Cerise and Laserviolet through
its dye loom; nothing in this module knows about that. That dye loom lists every derived colour of
every pattern with no pattern-item check, so a Cerise or Laserviolet Pirkko needs no Pirkko item —
the gate holds for vanilla colours only.

## Adding a banner pattern

1. Draw the pattern on a copy of a vanilla banner texture: 64×64 (or 128×128; any multiple of 64
   works, and the regions below scale with it). The flag's front face is the 20×40 area at (1,1).
   Draw there and nowhere else; datagen copies the front onto the back face for you and clears
   the rest.
2. Save it as `src/main/resources/art/kultur/<chapter>/banner/<id>.png`. `<id>` is lowercase
   letters, digits and underscores, unique across every chapter (`slaggan`, not `släggan`).
3. Optionally draw a shield version, `.../shield/<id>.png`, same size, in the shield's 12×22 face
   at (1,1) (vanilla stays inside the 10×20 at (2,2)). Without one, the banner's front face is
   scaled to half size onto the shield, which is fine for bold logos and poor for thin text.
4. Add a line to `src/main/resources/kultur.json` under your chapter:
   `{ "id": "<id>", "name": "<Name>" }`. Add `"item": true` to make it need a pattern item in the
   loom, and then also draw a 16×16 icon at `.../item/<id>.png`.
5. `./gradlew runDatagen` from the repo root, then commit your art and your `kultur.json` line.
   `src/main/generated` is a build artefact, not something to commit — everyone regenerates it
   locally, and the build refuses to package until `runDatagen` has run.

Names: the lang file is generated from `name` — "Light Blue ITK" and so on for all sixteen dyes,
and "<Name> Banner Pattern" for the item.

## Adding a painting

1. A PNG at 16 pixels per block: 48×64 for a 3×4 painting. Save it as
   `src/main/resources/art/kultur/<chapter>/painting/<id>.png`.
2. `{ "id": "<id>", "title": "...", "author": "...", "width": 3, "height": 4 }` under your chapter in
   `kultur.json`.
3. `./gradlew runDatagen`.

Every painting is placeable; a player gets it the vanilla way (a painting item with the variant, or
luck with a blank painting on a wall big enough).

## Adding a chapter

`{ "id": "<chapter>", "name": "<Chapter>", "patterns": [], "paintings": [] }` in `kultur.json`, and a
folder `art/kultur/<chapter>/`.

## Building and testing

- `./gradlew :mods:metacraft-kultur:build` — needs `runDatagen` to have run; the build says so if not.
- `./gradlew :mods:metacraft-kultur:test` — unit tests: the catalogue's validation and the texture
  normaliser (`Masks`).
- `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-kultur:runServer` —
  server game tests (`KulturGameTests`): every catalogue entry in its registry and tag, gated
  patterns behind their item, every asset in the jar.
- `./gradlew :mods:metacraft-kultur:runClientGameTest` — a real client joins a test server and
  screenshots a banner, a shield and the painting into
  `build/run/clientGameTest/screenshots/`. Look at it; that is the test.
- `./gradlew :mods:metacraft-kultur:runServer` — a dev server with the pack auto-hosted; join with a
  vanilla client, `/give @s minecraft:loom`, `/give @s kultur:pirkko_banner_pattern`.

## How it works

`kultur.json` is the only list. `GeneratedAssets` (datagen) writes, per pattern, the
`banner_pattern` definition, the tag entries, the normalised banner and shield textures and sixteen
lang lines; per painting, the `painting_variant` definition, its texture and two lang lines; per
gated pattern, the item's model and icon. Banner patterns and painting variants are dynamic
registries the server syncs to every client, so a vanilla client needs nothing but the textures,
which Polymer serves in the pack. The only Java that runs on a server is `ModContent`: one Polymer
item per gated pattern, disguised as the vanilla flower pattern item with our model, carrying
`provides_banner_patterns` for its tag.
