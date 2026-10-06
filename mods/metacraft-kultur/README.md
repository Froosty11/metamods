# Kultur

METAmods module `mods/metacraft-kultur` (mod id `kultur`). Chapter culture for vanilla clients:
banner and shield patterns for the chapters and their clubs, paintings, and drinks. Fabric +
[Polymer](https://polymer.pb4.eu), Minecraft 26.3, Java 25. Players need only the auto-served
resource pack; no client mod. The successor of PolymITer (the IT chapter's 1.21.10 mod this content
comes from); the ovvar and patches from there live in `mods/ovvar`.

## What is here, and what is not

IT's content: ITK, QMISK and TMEIT banner patterns free in any loom, the Pirkko pattern behind a
pattern item ("gated", below — `kultur:pirkko_banner_pattern`, from creative or `/give` for now),
Emelie Stark's painting "The Guardian of Kistan" (3×4), and PolymITer's four drinks (below). Data
and Media have their places in the catalogue and nothing in them yet — that is what this file is for.

Not here yet, on purpose: loot tables for the pattern items, any Data or Media art.

With `moredyes` on the same server every pattern here also comes in Cerise and Laserviolet through
its dye loom; nothing in this module knows about that. The dye loom keeps a loom's rule, so a
Cerise Pirkko needs the Pirkko pattern item in the player's inventory, as a white one does.

## The drinks

PolymITer's IT drinks, brewed with [Patbox's Brewery](https://modrinth.com/mod/brewery): cook the
ingredients in a water cauldron over fire, a campfire or lava, take the brew out with a glass bottle, and distil the
two that say so in a brewing stand. No barrel ageing.

| drink | cauldron | cook | then | alcohol |
| --- | --- | --- | --- | --- |
| Alcoholic Beverage | 4 potatoes, 2 sugar | 10 min | — | 20 |
| Spiken Patch Drink | 4 potatoes, 4 sweet berries | 8 min | distil once | 30 |
| Släggan Patch Drink | 4 potatoes, 1 suspicious stew | 8 min | distil once | 35 |
| Nyckeln Patch Drink | 2 apples, 1 sugar | 2 min | — | 0 |

Each alcoholic one sends you fast or slow for a minute or more (Släggan: slow three times in four,
with strength once you are drunk, else fast with weakness), harder the drunker you are. Brewery's
own drunkenness (stagger, nausea, poisoning) comes on top. Past 100 alcohol a kultur drink blacks you
out, as PolymITer's fourth drink did: you come to somewhere within 250 blocks with a headache, blind
for ten seconds and a little hurt, at 30 alcohol. Brewery counts a poor brew as stronger than it is,
so bad ones get you there sooner.

The drinks are data, `src/main/resources/data/kultur/brewery_drinks/*.json` (Brewery's format; their
bottles, PolymITer's models, are `assets/kultur/items/drink/`). Brewery is not a dependency: without
it nothing reads them. Four potatoes, not six, and a second ingredient each, because a cauldron offers
every drink whose ingredients are all in it and Brewery's vodka is six potatoes.
`KulturDrinkTests` checks every recipe brews only its own drink.

Brewery 0.17 on 26.3 cannot find its own drunkenness file (`brewery_effects.json`: it lists the
resource root, which 26.3 refuses with "Invalid path ''"), so without help none of that loads on a
real server. `compat/brewery`'s mixin finds it per namespace instead; it applies only when Brewery is
loaded, and can go when Brewery fixes it.

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
