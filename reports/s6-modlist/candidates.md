# METAcraft S6 worldgen candidates (Minecraft 26.3, Fabric)

Built from Modrinth search (`categories:worldgen`, `versions:26.3`, `project_type` mod and datapack, `server_side` required/optional; sorted by downloads and by follows, 50 each; 100 unique projects, raw list in `search_raw.json`) plus the names in the brief. 26.3 builds resolved with `/v2/project/SLUG/version?game_versions=["26.3"]&loaders=["fabric"]` (then `["datapack"]`). Every downloaded jar was scanned for client assets (`jarscan.json`: blockstates, item models, entity textures, client entrypoints).

Verdict key: **server-only OK** = no new blocks/items/entities needing client assets; **caveat** = works on vanilla clients but ships custom item models that need a server resource pack; **rejected** = needs a client mod or out of scope; **not on 26.3** = no build.

| slug | type | 26.3 build (file) | client_side | verdict | reason | deps |
|---|---|---|---|---|---|---|
| terralith | terrain | 2.6.5+26.3 (`Terralith_26.3_v2.6.5+26.3.jar`) | optional | server-only OK | Datapack biomes/terrain from vanilla blocks; client_side optional; no client assets except lang. | lithostitched |
| tectonic | terrain | 3.0.31-fabric-26.3 (`tectonic-3.0.31-fabric-26.3.jar`) | optional | server-only OK | Density-function terrain shaping; 55 classes, no blocks/items. | lithostitched (bundles apollib) |
| geophilic | terrain | 3.7 (`Geophilic v3.7.mod.jar`) | optional | server-only OK | Datapack overhaul of vanilla biomes (minecraft namespace overrides + features); vanilla blocks only. | none |
| wwoo | terrain | 3.0.1 (`wwoo-fabric-26.3-3.0.1.jar`) | optional | server-only OK | William Wythers' Overhauled Overworld, mod wrapper of the datapack (built-in resource packs under resources/). Vanilla blocks. | cristel-lib, cloth-config (both client optional) |
| william-wythers-overhauled-overworld-(datapack) | terrain | 3.0.2 (`William Wythers' Overhauled Overworld v3.0.2.zip`) | optional | server-only OK | Same content as wwoo as a plain datapack; benchmarked as the wwoo mod jar only. | none |
| continents | terrain | 1.2.1+26.3 (`Continents_26.3_v1.2.1+26.3.jar`) | optional | server-only OK | Noise/density-function datapack; continents separated by oceans. | none |
| hybrid-beta | terrain | 1.0.7+mod (`hybrid-beta-1.0.7.jar`) | optional | server-only OK | Datapack: Beta-style terrain + 55 biomes, vanilla blocks. | none |
| lithostitched | library | 2.0.4-fabric-26.3 (`lithostitched-2.0.4-fabric-26.3.jar`) | unsupported | server-only OK | Worldgen library; client_side unsupported (server-only by design). | fabric-api |
| geologica | terrain | — | optional | not on 26.3 | No 26.3 build on Modrinth. |  |
| yungs-better-caves | caves | — | unsupported | not on 26.3 | No 26.3 build. | yungs-api |
| cave-overhaul | caves | — | unsupported | not on 26.3 | No 26.3 build. |  |
| yungs-api | library | — | required | not on 26.3 | No 26.3 build; whole YUNG's Better * family therefore untestable. (YUNG's API is client_side=required on Modrinth, though the structure mods themselves are unsupported client-side.) |  |
| yungs-better-mineshafts | structures | — | unsupported | not on 26.3 | No 26.3 build. | yungs-api |
| yungs-better-strongholds | structures | — | unsupported | not on 26.3 | No 26.3 build. | yungs-api |
| yungs-better-dungeons | structures | — | unsupported | not on 26.3 | No 26.3 build. | yungs-api |
| yungs-better-ocean-monuments | structures | — | unsupported | not on 26.3 | No 26.3 build. | yungs-api |
| yungs-better-desert-temples | structures | — | unsupported | not on 26.3 | No 26.3 build. | yungs-api |
| explorify | structures | — | optional | not on 26.3 | No 26.3 build. |  |
| dungeons-and-taverns | structures | 6.0.2+mod (`dungeons-and-taverns-6.0.2.jar`) | optional | server-only OK (caveat) | Structures from vanilla blocks; ships 33 item models/textures for custom loot items (data-driven, vanilla item ids) - those show as missing-model items on vanilla clients unless a server resource pack is sent. | none |
| structory | structures | 1.3.18+26.3 (`Structory_26.3_v1.3.18+26.3.jar`) | optional | server-only OK | Vanilla-block structures datapack. | none |
| structory-towers | structures | 1.0.19+26.3 (`Structory_Towers_26.3_v1.0.19+26.3.jar`) | optional | server-only OK | Vanilla-block towers datapack. | none |
| towns-and-towers | structures | 1.13.12 (`t_and_t-fabric-neoforge-1.13.12.jar`) | optional | server-only OK | Village/outpost variants from vanilla blocks. | cristel-lib, cloth-config |
| sparsestructures | structures | 3.1.5 (`sparsestructures-fabric-26.3-3.1.5.jar`) | unsupported | server-only OK | Changes structure spacing only (mixin), no content. | none |
| repurposed-structures-fabric | structures | 7.8.2+26.3-fabric (`repurposed_structures-7.8.2+26.3-fabric.jar`) | unsupported | server-only OK | Vanilla-block structure variants; client_side unsupported. | fabric-api, midnightlib |
| moogs-voyager-structures | structures | 5.1.3 (`MoogsVoyagerStructures-universal-1.21-5.1.3.jar`) | unsupported | server-only OK | 130 vanilla-style structures (MVS). | moogs-structure-lib |
| mss-moogs-soaring-structures | structures | 2.2.0 (`MoogsSoaringStructures-universal-1.21-2.2.0.jar`) | unsupported | server-only OK | Floating islands with vanilla blocks. | moogs-structure-lib |
| formations-overworld | structures | 1.0.5c-mc1.21+ (`formationsoverworld-1.0.5c-mc1.21+.jar`) | unsupported | server-only OK | Vanilla-block structures. | formations |
| katters-structures | structures | 2.7-mod (`Katters Structures v2.7.jar`) | optional | server-only OK (caveat) | Vanilla-block structures + 3 biomes; 8 item models for custom items (same resource-pack caveat as D&T). | none |
| tidal-towns | structures | 2.0+mod (`tidal-towns-2.0.jar`) | optional | server-only OK | Ocean village datapack. | none |
| hopo-better-mineshaft | structures | 1.3.8 (`HopoBetterMineshaft-[26.3]-1.3.8.jar`) | optional | server-only OK | Mineshaft variants, vanilla blocks. | none |
| hopo-better-underwater-ruins | structures | 1.2.9 (`HopoBetterUnderwaterRuins-[26.3]-1.2.9.jar`) | optional | server-only OK | Ocean ruins, vanilla blocks. | none |
| hopo-better-ruined-portals | structures | 1.5.2 (`HopoBetterRuinedPortals-[26.3]-1.5.2.jar`) | optional | server-only OK | Ruined portals, vanilla blocks. | none |
| ati-structures-vanilla-edition | structures | 1.4.7 (`ATi Structures Vanilla V1.4.7 (26.3).jar`) | optional | server-only OK | Vanilla edition of ATi Structures (no custom items). | none |
| villages-and-pillages | structures | fabric-2.0.0+mc26.3 (`villagesandpillages-fabric-2.0.0+mc26.3.jar`) | unsupported | server-only OK | Witch village; client_side unsupported. | fabric-api |
| vanilla-structure-update | structures | V2.11+mod (`vanilla-structure-update-V2.11.jar`) | optional | server-only OK | Reworked vanilla structures, vanilla blocks. | none |
| epic-structures-villages | structures | 2.0.0+mod (`epic-structures-villages-2.0.0.jar`) | optional | server-only OK | Replaces vanilla villages, vanilla blocks. | none |
| snow-under-trees-remastered | features | 2.7.6+26.3 (`SnowUnderTrees-2.7.7+26.3.jar`) | unsupported | server-only OK | Feature: snow under trees; no new blocks. | fabric-api, mru |
| incendium | nether | 5.5.3+26.3 (`Incendium_26.3_v5.5.3+26.3.jar`) | optional | server-only OK | Nether biomes + structures; custom items are vanilla-item-based (no item models in jar). | none |
| amplified-nether | nether | 1.3.0+26.3 (`Amplified_Nether_26.3_v1.3.0+26.3.jar`) | optional | server-only OK | Nether noise datapack. | none |
| amethyst-nether | nether | v4.4+mod (`amethyst-nether-v4.4.jar`) | optional | server-only OK | Nether biomes/structures from vanilla blocks. | none |
| formations-nether | nether | 1.0.5b-mc1.21+ (`formationsnether-1.0.5b-mc1.21+.jar`) | unsupported | server-only OK | Nether structures. | formations |
| mns-moogs-nether-structures | nether | 3.1.1 (`MoogsNetherStructures-universal-1.21-3.1.1.jar`) | unsupported | server-only OK | Nether structures. | moogs-structure-lib |
| nullscape | end | 2.0.1+26.3 (`Nullscape_26.3_v2.0.1+26.3.jar`) | optional | server-only OK | End terrain/biomes datapack. | none |
| stellarity | end | 6.0.0+mod (`Stellarity-6.0.0.jar`) | optional | server-only OK (caveat) | End overhaul; works on vanilla clients for terrain, but ships 530 item models / 434 textures / 3 blockstates for custom items/display entities - needs a server resource pack for those to look right. | none |
| endercon | end | 4.0+mod (`endercon-4.0.jar`) | optional | server-only OK | End terrain datapack. | enderscape (optional) |
| mes-moogs-end-structures | end | 2.1.1 (`MoogsEndStructures-universal-1.21-2.1.1.jar`) | unsupported | server-only OK | End structures. | moogs-structure-lib |
| scalablelux | perf | 0.3.0-alpha.0.6+26.3 (`ScalableLux-fabric-mc26.3-0.3.0-alpha.0.6-all.jar`) | optional | server-only OK | Lighting engine performance (Starlight-style); server-side. | none |
| structure-layout-optimizer | perf | 1.1.4+26.3-fabric (`structure_layout_optimizer-1.1.4+26.3-fabric.jar`) | unsupported | server-only OK | Jigsaw structure placement optimisation. | resourceful-config |
| zfastnoise | perf | 1.1.1+26.3 (`zfastnoise-1.1.1+26.3.jar`) | unsupported | server-only OK | Vanilla noise optimisation; declares incompatibility with moonrise and anti-xray only. | zconfig |
| ksyxis | perf | 1.4.5 (`Ksyxis-1.4.5.jar`) | unsupported | rejected | Not worldgen (skips spawn-chunk loading at startup); out of scope, not benchmarked. | none |
| noisium | perf | — | unsupported | not on 26.3 | No 26.3 build (known). |  |
| leukocyte | perf | — | unsupported | not on 26.3 | No 26.3 build (known). |  |
| biomes-o-plenty | terrain | 26.3.0.0.13 (`BiomesOPlenty-fabric-26.3-26.3.0.0.13.jar`) | required | rejected | Adds 493 blockstates/677 item models (new blocks) - needs client mod. Also TerraBlender (client required). | terrablender, glitchcore |
| terrablender | library | 26.3.0.0.9 (`TerraBlender-fabric-26.3-26.3.0.0.9.jar`) | required | rejected | client_side required on Modrinth; only needed by BOP-family. |  |
| oh-the-biomes-weve-gone | terrain | — | required | rejected | No 26.3 build; adds blocks (client required). |  |
| wilder-wild | terrain | 4.3.2-mc26.3-fabric (`WilderWild-4.3.2-mc26.3-fabric.jar`) | required | rejected | Adds 301 blockstates, mobs; client_side required. | frozenlib |
| streams-reflowing | terrain | — | required | rejected | No 26.3 build; client_side required (custom fluid rendering). |  |
| ecologics | terrain | — | — | rejected | Adds blocks/mobs; client_side required. |  |
| structurify | structures | — | — | rejected | Config tool, client_side required. |  |
| natures-compass | utility | — | — | rejected | Item mod, client required, not worldgen. |  |
| explorers-compass | utility | — | — | rejected | Item mod, client required, not worldgen. |  |
| waystones | utility | — | — | rejected | Adds blocks, client required, not worldgen. |  |
| naturalist | mobs | — | — | rejected | Adds mobs, client required. |  |
| biome-replacer | utility | — | — | rejected | Tool for removing biomes; not a look/perf candidate. |  |
| oh-the-trees-youll-grow | library | — | — | rejected | Tree library for OTBYG family only. |  |
| dungeons-and-taverns-*-overhaul (splinters) | structures | — | — | rejected | Standalone subsets of Dungeons and Taverns; covered by the full pack. |  |
| ati-structures-fabricforge | structures | — | — | rejected | Non-vanilla edition (custom items); vanilla edition tested instead. |  |

Baseline/tooling jars (all 26.3 builds): fabric-api 0.161.0+26.3, c2me-fabric 0.4.2-alpha.0.89+26.3, distanthorizons 3.3.4-26.3, spark 1.10.187-fabric, chunky 1.5.3, bluemap 5.28-fabric
