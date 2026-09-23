# METAcraft Booklet

The server's guidebook: [Booklet](https://github.com/Patbox/booklet) (Patbox's server-side, data-driven
guidebook, a dialog on a vanilla client) with a chapter for each mod METAcraft runs. `/guide` (anyone)
opens the index, "The Encyclopedia", which lists every chapter. `docs/` has a screenshot of every page.

## Chapters are hooks

Each chapter is a built-in datapack in `src/main/resources/resourcepacks/<name>/`, registered only when
its mod is loaded (`MetacraftBooklet.HOOKS`). No chapter's mod is a dependency: a server without ovvar
has no ovvar chapter, and no ovvar item icons to resolve.

| hook | mod | what it adds |
| --- | --- | --- |
| `ovvar` | ovvar | "How to ovvar": the ovve, patches, sewing, wardrobe and stash |
| `canvas` | polydecorations | "Canvas": making, painting, finishing and copying a canvas |
| `polydecorations_canvas_only` | polydecorations | turns off every PolyDecorations recipe except the canvas's |

A new chapter: a folder under `resourcepacks/` with a `pack.mcmeta` (data format 121 for 26.3) and its
pages in `data/metacraft/booklet/pages/en_us/`, then one line in `HOOKS`. The chapter's main page goes in
`category=booklet:main_page` with an `order=` (ovvar 10, canvas 20); its sub-pages in a category of its
own that the main page lists with `### Category Entries:`. See Booklet's
[USAGE.md](https://github.com/Patbox/booklet/blob/master/USAGE.md) for the page format. Two gotchas: `-`
lines are lists and numbered lines are not, and `<anything>` in angle brackets is parsed as a tag.

**metacraft-kultur** has no chapter yet: the mod was not available when this was written. Its hook is the
same shape as the others.

## Page images, beside the text

Every picture sits to the right of the text it goes with. Dialog bodies stack, so there is no such
layout in Booklet; `Beside` makes one. Booklet draws an image as rows of 9 px glyph lines, the height
of a line of text, so a page that names an image under `beside/`

    ### Image: metacraft:beside/ovvar/hero The text that goes to the left of the picture.<nl2>More.

has its text wrapped to the room left of the picture and each text line joined to one image row
(`ImageBodyMixin` swaps Booklet's own layout for that one). Here one image pixel is one UI pixel, so
each picture is made at its display size, about 80–180 px wide.

The isometric renders are made the way Patbox makes PolyFactory's, with his client-only
[Simple Image Renderer](https://github.com/Patbox/SimpleImageRenderer), driven by a client test instead of
by hand. `BookletRenders` builds each scene in a flat world (mannequins in patched ovvar, a sewing stand,
a painted wall of canvases) and renders it; it also gives the Tester a design and a stash, opens
`/ovvar stash` at GUI scale 1 and cuts the menu out of a screenshot at its own pixels. The script trims
the renders into `src/main/resources/assets/metacraft/textures/booklet/image/beside/`:

    METACRAFT_BOOKLET_RENDER=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
    python3 mods/metacraft-booklet/tools/booklet_images.py

`GuideTests` fails if a page names an image that is not there. A new scene is a line in
`BookletRenders.SCENES` and a few in its `build`.

## PolyDecorations: canvas only

PolyDecorations has no config to switch features off, so `polydecorations_canvas_only` overrides every
recipe and recipe advancement that is not a canvas one with a file whose only content is a
`fabric:false` load condition, which removes it. The files were generated from PolyDecorations
0.13.1+26.3-rc-1; `polyDecorationsHasOnlyItsCanvasRecipes` fails, naming them, if a newer version adds
recipes. The pack is enabled by default and can be turned off with `/datapack disable`.

Recipes are not the whole mod. These still work, because PolyDecorations does them by changing vanilla
code rather than through recipes: lanterns hung on the side of a block, a lead tied to a container,
looser placement rules for hanging signs, more plants in flower pots, and a shulker-box tweak. The
other items stay registered and can be given by an operator or taken from the creative menu.

## The question mark

Vanilla puts a warning button beside the title of every dialog a server sends. The resource pack
replaces its three sprites (`assets/minecraft/textures/gui/sprites/dialog/warning_button*.png`) with a
question mark in the same style. It applies to every server dialog, not only the book.

## Testing

    JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-booklet:runServer -PrunDir=/tmp/mcb
    ./gradlew :mods:metacraft-booklet:runClientGameTest

The dev runtime loads ovvar (run its `runDatagen` first) and PolyDecorations, so every chapter is on.
`GuideTests` checks every page loads with a title, description and icon, is reachable from the index,
and names only pages and items that exist, and that PolyDecorations is down to its canvas recipes.
`GuideClientTests` opens every page on a vanilla client in a 1080p window and photographs it, and again
scrolled down (`docs/*_more.png`).
