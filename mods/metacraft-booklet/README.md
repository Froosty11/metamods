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
| `decorating` | polydecorations | "Decorating": canvas, mailboxes, rope, sign posts, lanterns and leads, hammer and trowel |
| `polydecorations_s6` | polydecorations | turns off every PolyDecorations recipe except those of the things above |
| `qol` | metacraft-qol | "Quality of life": the void anchor, the concrete cauldron, and silencing mobs with the muffler |

A new chapter: a folder under `resourcepacks/` with a `pack.mcmeta` (data format 121 for 26.3) and its
pages in `data/metacraft/booklet/pages/en_us/`, then one line in `HOOKS`. The chapter's main page goes in
`category=booklet:main_page` with an `order=` (ovvar 10, decorating 20, quality of life 30); its sub-pages in a category of its
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

has its text laid beside the picture (`ImageBodyMixin` swaps Booklet's own layout for `Beside`'s).
The picture is a font of the mod's own, `assets/metacraft/font/beside.json`: cut into 16×9 UI px tiles
(the height of a line of text), four image pixels to a UI pixel, the detail a 1080p screen at GUI scale 4
shows. Each line of the body is a line of text wrapped to the room on the left and padded to exactly
that width, a gap, and one row of tiles: only ordinary positive advances, so nothing depends on another
font's spacing on the client.

Two things the client is strict about. A bitmap glyph is as wide as its rightmost drawn column, so fully
clear pixels are written with alpha 1 (invisible, but drawn) or clear-edged tiles would come out narrow.
And the dialog's text widget wraps at the body width less 2 × 4 px and breaks the moment a line's running
width passes that, so the −1 spacer goes *before* each tile (after it, the last tile would overshoot by
one) and every line keeps a few pixels in hand; `Beside.SLACK`.

The isometric renders are made the way Patbox makes PolyFactory's, with his client-only
[Simple Image Renderer](https://github.com/Patbox/SimpleImageRenderer), driven by a client test instead of
by hand. `BookletRenders` builds each scene in a flat world (mannequins in patched ovvar, the Tester at a
sewing stand with a patch in hand and ovvar's washed-out preview on the stand, a painted wall of canvases, a mailbox with mail, rope, sign posts, a wall lantern, a lead between fences, a trowelled path) and renders it; it also gives the Tester a design and a stash, opens
`/ovvar stash` at GUI scale 1 and cuts the menu out of a screenshot at its own pixels. The script cuts
the renders into that font (`textures/font/beside/`, `font/beside.json`, and `beside/index.json`, which
`Beside` reads for each picture's width and rows):

    METACRAFT_BOOKLET_RENDER=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
    python3 mods/metacraft-booklet/tools/booklet_images.py

`GuideTests` fails if a page names an image that is not there. A new scene is a line in
`BookletRenders.SCENES` and a few in its `build`.

## PolyDecorations: only what we keep

METAcraft keeps the PolyDecorations things that look like vanilla: the canvas, mailboxes, rope, sign
posts, the hammer and the trowel, and its changes to vanilla blocks (lanterns on walls, leads from fence
to fence, lanterns and hanging signs under rope). The furniture, statues and the rest are out.

The server runs our PolyDecorations fork
([Froosty11/PolyDecorations](https://github.com/Froosty11/PolyDecorations/releases), 0.13.1+26.3-rc-1.metacraft.1),
which can switch features off completely: no blocks, items, Polymer block states or resource-pack assets.
`server-config/polydecorations.json` is the file that goes in the server's `config/` folder; the dev runs
copy it into theirs before starting, so they match the server.

With upstream PolyDecorations, which has no such config, `polydecorations_s6` still hides the rest: it
overrides every other recipe and recipe advancement with a file whose only content is a `fabric:false`
load condition, which removes it. The files were generated from PolyDecorations 0.13.1+26.3-rc-1;
`polyDecorationsHasOnlyTheRecipesWeKeep` fails, naming them, if a newer version adds recipes. The pack
is enabled by default and can be turned off with `/datapack disable`.

## The question mark

Vanilla puts a warning button beside the title of every dialog a server sends. The resource pack
replaces its three sprites (`assets/minecraft/textures/gui/sprites/dialog/warning_button*.png`) with a
question mark in the same style. It applies to every server dialog, not only the book.

## Testing

    JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-booklet:runServer -PrunDir=/tmp/mcb
    ./gradlew :mods:metacraft-booklet:runClientGameTest

The dev runtime loads ovvar (run its `runDatagen` first) and the PolyDecorations fork with Season 6's
features, so every chapter is on.
`GuideTests` checks every page loads with a title, description and icon, is reachable from the index,
and names only pages and items that exist, and that PolyDecorations is down to the recipes we keep.
`GuideClientTests` opens every page on a vanilla client in a 1080p window and photographs it, and again
scrolled down (`docs/*_more.png`).
