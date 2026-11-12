#!/usr/bin/env python3
"""Chapter banner-pattern item icons: each chapter's symbol on the banner-pattern scroll.

The 16x16 icons copy the hand-made Pirkko icon (it/item/pirkko.png): its scroll background
(the vanilla banner-pattern item texture, give or take one pixel) with the symbol in its
dark brown, inside the same 7x7 area (columns 4-10, rows 7-13; even-width symbols may use
column 11 to stay centred on the paper).

Each symbol starts from Edvin's banner art (art/kultur/<chapter>/banner/<id>.png, front face)
or his shield art (shield/<id>.png), downscaled by area coverage to the symbol area and
thresholded to a 1-bit mask (`--draft`). The masks below are those drafts cleaned by hand,
pixel by pixel; they are machine-made derivations of Edvin's art, so redraw any you like.

  python3 tools/chapter_item_icons.py                      # write art/kultur/<ch>/item/<ch>.png
  python3 tools/chapter_item_icons.py --draft fysik 0.5    # print the automatic mask drafts
  python3 tools/chapter_item_icons.py --preview out.png    # also write a labelled preview grid

Run from mods/metacraft-kultur. Needs Pillow.
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw

ART = os.path.join("src", "main", "resources", "art", "kultur")
PIRKKO = os.path.join(ART, "it", "item", "pirkko.png")

INK = (99, 78, 66, 255)  # Pirkko's symbol colour (also vanilla's)
MID = (170, 160, 154, 255)  # vanilla's half-tone symbol colour, for '+' in a mask
PAPER = (235, 235, 235, 255)  # the paper under Pirkko's symbol
AREA_X, AREA_Y, AREA_W, AREA_H = 4, 7, 8, 7  # Pirkko uses columns 4-10; column 11 is still paper

# '#' ink, '+' half-tone, '.' paper. Hand-cleaned from the --draft output.
MASKS = {
    "arkitektur": """
        #######
        #######
        ##...##
        ##...##
        ##...##
        ##...##
        ###.###
    """,
    "bergs": """
        ..#.#..
        ...#...
        ##.#.##
        ..###..
        ..###..
        .#.#.#.
        #..#..#
    """,
    "bygg": """
        ##.#.##
        #.###.#
        .#.#.#.
        .#.#.#.
        #..#..#
        #..#..#
        #######
    """,
    "cl": """
        .....#.
        ####.##
        ###.###
        ##.###.
        #.####.
        .####..
        #.##...
    """,
    "data": """
        .###.
        .#...
        ..#..
        .#.#.
        #...#
        #...#
        .###.
    """,
    "doktorand": """
        ..###..
        .#...#.
        #..#..#
        #.###.#
        .#...#.
        ..###..
        ...#...
    """,
    "elektro": """
        ##...##
        .##.##.
        ..###..
        .#.#.#.
        .#####.
        #.#.#.#
        .#.#.#.
    """,
    "energi": """
        .##....
        #..#...
        #...#.#
        .#..##.
        ..#.#..
        ...#...
        ...#...
    """,
    "flyg": """
        ....###
        ...####
        ##.###.
        #####..
        .###...
        ######.
        .####..
    """,
    "fysik": """
        ..###..
        .#...#.
        #..#..#
        #.###.#
        #######
        .#####.
        ..###..
    """,
    "indek": """
        ...#...
        ######.
        ...#..#
        ..#...#
        ..#..#.
        ..#.##.
        .###...
    """,
    "it": """
        ..###..
        .#...#.
        #.#...#
        #.###.#
        #...#.#
        .#...#.
        ..###..
    """,
    "kemi": """
        #.....#
        ##.#.##
        .##.##.
        ..###..
        .#...#.
        ..###..
        #.#.#.#
    """,
    "maskin": """
        #.#.#.#
        #######
        .#####.
        #.###.#
        .#...#.
        .#...#.
        #.###.#
    """,
    "media": """
        .##....
        ####...
        #...##.
        #....##
        #...##.
        ####...
        .##....
    """,
    "mit": """
        #.###.#
        .#.#.#.
        #..#..#
        #..##.#
        #...#.#
        .#..##.
        #.###.#
    """,
    "open": """
        ..###..
        .#...#.
        #..#..#
        #.###.#
        #.###.#
        ..#.#..
        .##.##.
    """,
    "produktion": """
        .#.#.#.
        #.###.#
        .#####.
        #.###.#
        ...#...
        ...#...
        ..###..
    """,
    "samhall": """
        ..###..
        .#.###.
        #..####
        #..#..#
        ####..#
        .###.#.
        ..###..
    """,
    "tbas": """
        ...#...
        .#...#.
        #.#.#.#
        #..#..#
        #..#..#
        .#...#.
        ...#...
    """,
}


def parse(mask):
    rows = [r.strip() for r in mask.strip().splitlines()]
    w = len(rows[0])
    if any(len(r) != w for r in rows) or w > AREA_W or len(rows) > AREA_H:
        raise ValueError("ragged or oversized mask")
    return rows


def background():
    """Pirkko's icon with its symbol painted over in paper."""
    im = Image.open(PIRKKO).convert("RGBA")
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y)) == INK:
                if not (AREA_X <= x < AREA_X + AREA_W and AREA_Y <= y < AREA_Y + AREA_H):
                    sys.exit("pirkko.png has ink outside the symbol area; the background would be wrong")
                im.putpixel((x, y), PAPER)
    return im


def icon(bg, mask):
    rows = parse(mask)
    w, h = len(rows[0]), len(rows)
    x0 = AREA_X + (AREA_W - w) // 2  # odd widths centre on column 7 like Pirkko, even ones on 7.5
    y0 = AREA_Y + (AREA_H - h) // 2
    im = bg.copy()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x0 + x, y0 + y), INK if ch == "#" else MID)
    return im


def source(chapter, kind):
    """The opaque part of the banner front face or the shield face, cropped to its bounds."""
    im = Image.open(os.path.join(ART, chapter, kind, chapter + ".png")).convert("RGBA")
    a = im.crop((1, 1, 21, 41) if kind == "banner" else (1, 1, 13, 23)).getchannel("A")
    return a.crop(a.getbbox())


def draft(chapter, threshold):
    for kind in ("banner", "shield"):
        a = source(chapter, kind)
        s = min((AREA_W - 1) / a.width, AREA_H / a.height)
        small = a.resize((max(1, round(a.width * s)), max(1, round(a.height * s))), Image.BOX)
        print(f"{chapter} {kind} {a.size} -> {small.size} at {threshold}")
        for y in range(small.height):
            print("    " + "".join("#" if small.getpixel((x, y)) >= threshold * 255 else "." for x in range(small.width)))


def preview(icons, path):
    pirkko = Image.open(PIRKKO).convert("RGBA")
    cell_w, cell_h, cols = 16 + 8 + 128 + 8 + 128 + 8 + 60 + 24, 128 + 24, 4
    rows = (len(icons) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (44, 44, 52, 255))
    d = ImageDraw.Draw(sheet)
    for i, (chapter, im) in enumerate(icons.items()):
        x, y = (i % cols) * cell_w + 6, (i // cols) * cell_h + 4
        d.text((x, y), chapter, fill=(255, 255, 255, 255))
        y += 16
        sheet.alpha_composite(im, (x, y))
        sheet.alpha_composite(im.resize((128, 128), Image.NEAREST), (x + 24, y))
        sheet.alpha_composite(pirkko.resize((128, 128), Image.NEAREST), (x + 160, y))
        face = Image.open(os.path.join(ART, chapter, "banner", chapter + ".png")).convert("RGBA").crop((1, 1, 21, 41))
        dark = Image.new("RGBA", face.size, (20, 20, 20, 255))
        dark.alpha_composite(face)
        sheet.alpha_composite(dark.resize((60, 120), Image.NEAREST), (x + 296, y + 4))
    sheet.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--draft", nargs=2, metavar=("CHAPTER", "THRESHOLD"))
    ap.add_argument("--preview")
    args = ap.parse_args()
    if args.draft:
        draft(args.draft[0], float(args.draft[1]))
        return
    bg = background()
    icons = {}
    for chapter, mask in MASKS.items():
        icons[chapter] = icon(bg, mask)
        icons[chapter].save(os.path.join(ART, chapter, "item", chapter + ".png"), optimize=True)
    if args.preview:
        preview(icons, args.preview)


if __name__ == "__main__":
    main()
