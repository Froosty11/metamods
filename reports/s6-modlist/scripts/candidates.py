"""Build candidates.md from versions.json (Modrinth 26.3 resolution), jarscan.json (asset scan) and manual verdicts."""
import json,os
H=os.path.dirname(os.path.abspath(__file__)); R=H+"/.."
V=json.load(open(R+"/versions.json")); J=json.load(open(R+"/jarscan.json"))
OK="server-only OK"; CAVE="server-only OK (caveat)"; REJ="rejected"; NA="not on 26.3"
rows=[ # slug, type, verdict, reason, deps
("terralith","terrain",OK,"Datapack biomes/terrain from vanilla blocks; client_side optional; no client assets except lang.","lithostitched"),
("tectonic","terrain",OK,"Density-function terrain shaping; 55 classes, no blocks/items.","lithostitched (bundles apollib)"),
("geophilic","terrain",OK,"Datapack overhaul of vanilla biomes (minecraft namespace overrides + features); vanilla blocks only.","none"),
("wwoo","terrain",OK,"William Wythers' Overhauled Overworld, mod wrapper of the datapack (built-in resource packs under resources/). Vanilla blocks.","cristel-lib, cloth-config (both client optional)"),
("william-wythers-overhauled-overworld-(datapack)","terrain",OK,"Same content as wwoo as a plain datapack; benchmarked as the wwoo mod jar only.","none"),
("continents","terrain",OK,"Noise/density-function datapack; continents separated by oceans.","none"),
("hybrid-beta","terrain",OK,"Datapack: Beta-style terrain + 55 biomes, vanilla blocks.","none"),
("lithostitched","library",OK,"Worldgen library; client_side unsupported (server-only by design).","fabric-api"),
("geologica","terrain",NA,"No 26.3 build on Modrinth.",""),
("yungs-better-caves","caves",NA,"No 26.3 build.","yungs-api"),
("cave-overhaul","caves",NA,"No 26.3 build.",""),
("yungs-api","library",NA,"No 26.3 build; whole YUNG's Better * family therefore untestable. (YUNG's API is client_side=required on Modrinth, though the structure mods themselves are unsupported client-side.)",""),
("yungs-better-mineshafts","structures",NA,"No 26.3 build.","yungs-api"),
("yungs-better-strongholds","structures",NA,"No 26.3 build.","yungs-api"),
("yungs-better-dungeons","structures",NA,"No 26.3 build.","yungs-api"),
("yungs-better-ocean-monuments","structures",NA,"No 26.3 build.","yungs-api"),
("yungs-better-desert-temples","structures",NA,"No 26.3 build.","yungs-api"),
("explorify","structures",NA,"No 26.3 build.",""),
("dungeons-and-taverns","structures",CAVE,"Structures from vanilla blocks; ships 33 item models/textures for custom loot items (data-driven, vanilla item ids) - those show as missing-model items on vanilla clients unless a server resource pack is sent.","none"),
("structory","structures",OK,"Vanilla-block structures datapack.","none"),
("structory-towers","structures",OK,"Vanilla-block towers datapack.","none"),
("towns-and-towers","structures",OK,"Village/outpost variants from vanilla blocks.","cristel-lib, cloth-config"),
("sparsestructures","structures",OK,"Changes structure spacing only (mixin), no content.","none"),
("repurposed-structures-fabric","structures",OK,"Vanilla-block structure variants; client_side unsupported.","fabric-api, midnightlib"),
("moogs-voyager-structures","structures",OK,"130 vanilla-style structures (MVS).","moogs-structure-lib"),
("mss-moogs-soaring-structures","structures",OK,"Floating islands with vanilla blocks.","moogs-structure-lib"),
("formations-overworld","structures",OK,"Vanilla-block structures.","formations"),
("katters-structures","structures",CAVE,"Vanilla-block structures + 3 biomes; 8 item models for custom items (same resource-pack caveat as D&T).","none"),
("tidal-towns","structures",OK,"Ocean village datapack.","none"),
("hopo-better-mineshaft","structures",OK,"Mineshaft variants, vanilla blocks.","none"),
("hopo-better-underwater-ruins","structures",OK,"Ocean ruins, vanilla blocks.","none"),
("hopo-better-ruined-portals","structures",OK,"Ruined portals, vanilla blocks.","none"),
("ati-structures-vanilla-edition","structures",OK,"Vanilla edition of ATi Structures (no custom items).","none"),
("villages-and-pillages","structures",OK,"Witch village; client_side unsupported.","fabric-api"),
("vanilla-structure-update","structures",OK,"Reworked vanilla structures, vanilla blocks.","none"),
("epic-structures-villages","structures",OK,"Replaces vanilla villages, vanilla blocks.","none"),
("snow-under-trees-remastered","features",OK,"Feature: snow under trees; no new blocks.","fabric-api, mru"),
("incendium","nether",OK,"Nether biomes + structures; custom items are vanilla-item-based (no item models in jar).","none"),
("amplified-nether","nether",OK,"Nether noise datapack.","none"),
("amethyst-nether","nether",OK,"Nether biomes/structures from vanilla blocks.","none"),
("formations-nether","nether",OK,"Nether structures.","formations"),
("mns-moogs-nether-structures","nether",OK,"Nether structures.","moogs-structure-lib"),
("nullscape","end",OK,"End terrain/biomes datapack.","none"),
("stellarity","end",CAVE,"End overhaul; works on vanilla clients for terrain, but ships 530 item models / 434 textures / 3 blockstates for custom items/display entities - needs a server resource pack for those to look right.","none"),
("endercon","end",OK,"End terrain datapack.","enderscape (optional)"),
("mes-moogs-end-structures","end",OK,"End structures.","moogs-structure-lib"),
("scalablelux","perf",OK,"Lighting engine performance (Starlight-style); server-side.","none"),
("structure-layout-optimizer","perf",OK,"Jigsaw structure placement optimisation.","resourceful-config"),
("zfastnoise","perf",OK,"Vanilla noise optimisation; declares incompatibility with moonrise and anti-xray only.","zconfig"),
("ksyxis","perf",REJ,"Not worldgen (skips spawn-chunk loading at startup); out of scope, not benchmarked.","none"),
("noisium","perf",NA,"No 26.3 build (known).",""),
("leukocyte","perf",NA,"No 26.3 build (known).",""),
("biomes-o-plenty","terrain",REJ,"Adds 493 blockstates/677 item models (new blocks) - needs client mod. Also TerraBlender (client required).","terrablender, glitchcore"),
("terrablender","library",REJ,"client_side required on Modrinth; only needed by BOP-family.",""),
("oh-the-biomes-weve-gone","terrain",REJ,"No 26.3 build; adds blocks (client required).",""),
("wilder-wild","terrain",REJ,"Adds 301 blockstates, mobs; client_side required.","frozenlib"),
("streams-reflowing","terrain",REJ,"No 26.3 build; client_side required (custom fluid rendering).",""),
("ecologics","terrain",REJ,"Adds blocks/mobs; client_side required.",""),
("structurify","structures",REJ,"Config tool, client_side required.",""),
("natures-compass","utility",REJ,"Item mod, client required, not worldgen.",""),
("explorers-compass","utility",REJ,"Item mod, client required, not worldgen.",""),
("waystones","utility",REJ,"Adds blocks, client required, not worldgen.",""),
("naturalist","mobs",REJ,"Adds mobs, client required.",""),
("biome-replacer","utility",REJ,"Tool for removing biomes; not a look/perf candidate.",""),
("oh-the-trees-youll-grow","library",REJ,"Tree library for OTBYG family only.",""),
("dungeons-and-taverns-*-overhaul (splinters)","structures",REJ,"Standalone subsets of Dungeons and Taverns; covered by the full pack.",""),
("ati-structures-fabricforge","structures",REJ,"Non-vanilla edition (custom items); vanilla edition tested instead.",""),
]
out=["# METAcraft S6 worldgen candidates (Minecraft 26.3, Fabric)","",
"Built from Modrinth search (`categories:worldgen`, `versions:26.3`, `project_type` mod and datapack, `server_side` required/optional; sorted by downloads and by follows, 50 each; "
f"{len(json.load(open(R+'/search_raw.json')))} unique projects, raw list in `search_raw.json`) plus the names in the brief. "
"26.3 builds resolved with `/v2/project/SLUG/version?game_versions=[\"26.3\"]&loaders=[\"fabric\"]` (then `[\"datapack\"]`). "
"Every downloaded jar was scanned for client assets (`jarscan.json`: blockstates, item models, entity textures, client entrypoints).","",
"Verdict key: **server-only OK** = no new blocks/items/entities needing client assets; **caveat** = works on vanilla clients but ships custom item models that need a server resource pack; **rejected** = needs a client mod or out of scope; **not on 26.3** = no build.","",
"| slug | type | 26.3 build (file) | client_side | verdict | reason | deps |","|---|---|---|---|---|---|---|"]
for slug,typ,verd,reason,deps in rows:
    v=V.get(slug,{}); b=v.get("v263")
    build=f"{b['version_number']} (`{b['file']}`)" if b else "—"
    out.append(f"| {slug} | {typ} | {build} | {v.get('client_side','—')} | {verd} | {reason} | {deps} |")
out+=["","Baseline/tooling jars (all 26.3 builds): "+", ".join(f"{s} {V[s]['v263']['version_number']}" for s in ["fabric-api","c2me-fabric","distanthorizons","spark","chunky","bluemap"] if V.get(s,{}).get("v263"))]
open(R+"/candidates.md","w").write("\n".join(out)+"\n")
print(len(rows))
