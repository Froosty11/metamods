#!/usr/bin/env python3
"""Trims BookletRenders' output into the page images Booklet shows.

    METACRAFT_BOOKLET_RENDER=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
    python3 mods/metacraft-booklet/tools/booklet_images.py

Each render is cut to what was drawn (the background is transparent) and laid, centred, on a
transparent image WIDTH px wide. Booklet draws an image at one pixel per UI pixel up to 292 px wide
and divides larger ones by ceil(width / 292), so a 584 px image is shown at half size: twice the
detail in the same space. The subject is scaled to fit BOX (in image pixels, so half that in UI
pixels). The result goes to src/main/resources/assets/metacraft/textures/booklet/image/<name>.png.
"""
from pathlib import Path
from PIL import Image

MODULE = Path(__file__).resolve().parent.parent
SRC = MODULE / "build/booklet-renders"
DST = MODULE / "src/main/resources/assets/metacraft/textures/booklet/image"
WIDTH = 584
BOX = {"ovvar/chapters": (480, 260), "canvas/wall": (400, 230)}
DEFAULT_BOX = (400, 240)
PAD = 6

for src in sorted(SRC.rglob("*.png")):
    name = src.relative_to(SRC).with_suffix("").as_posix()
    if name.startswith("probe/"):
        continue
    im = Image.open(src).convert("RGBA")
    box = im.getbbox()
    if box is None:
        raise SystemExit(f"{name}: the render is empty")
    im = im.crop(box)
    bw, bh = BOX.get(name, DEFAULT_BOX)
    scale = min(bw / im.width, bh / im.height)
    im = im.resize((max(1, round(im.width * scale)), max(1, round(im.height * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (WIDTH, im.height + 2 * PAD))
    out.alpha_composite(im, ((WIDTH - im.width) // 2, PAD))
    dst = DST / f"{name}.png"
    dst.parent.mkdir(parents=True, exist_ok=True)
    out.save(dst, optimize=True)
    print(f"{name}: {out.size[0]}x{out.size[1]} -> {dst.relative_to(MODULE)}")
