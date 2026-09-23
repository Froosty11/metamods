#!/usr/bin/env python3
"""Turns BookletRenders' output into the page images Booklet shows.

    METACRAFT_BOOKLET_RENDER=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
    python3 mods/metacraft-booklet/tools/booklet_images.py

Every picture sits beside text (Beside.java: an image under beside/). Booklet draws an image
ceil(width / 292) image pixels to a UI pixel, so each is made 1152 px wide (4 × 288 UI px) with the
picture at its right and transparency to its left, where Beside puts the text: four image pixels per
UI pixel is what a 1080p screen at GUI scale 4 shows. Its height is a whole number of Booklet's 9 UI px
rows (36 px). A render is cut to what was drawn and scaled so the picture fits SIZE (UI px); a
screenshot cut-out (the stash menu, taken at GUI scale 1) is enlarged 4× pixel for pixel.
The results go to src/main/resources/assets/metacraft/textures/booklet/image/beside/<name>.png.
"""
import shutil
from pathlib import Path
from PIL import Image

MODULE = Path(__file__).resolve().parent.parent
SRC = MODULE / "build/booklet-renders"
DST = MODULE / "src/main/resources/assets/metacraft/textures/booklet/image/beside"
# (width, height) the picture must fit, in UI pixels.
SIZE = {"ovvar/chapters": (150, 110), "canvas/wall": (100, 110), "ovvar/stand": (150, 120)}
DEFAULT_SIZE = (100, 120)
AS_IS = {"ovvar/stash"}
SCALE, WIDTH, ROW, PAD = 4, 1152, 36, 8

shutil.rmtree(DST, ignore_errors=True)
for src in sorted(SRC.rglob("*.png")):
    name = src.relative_to(SRC).with_suffix("").as_posix()
    if name.startswith("probe/"):
        continue
    dst = DST / f"{name}.png"
    dst.parent.mkdir(parents=True, exist_ok=True)
    im = Image.open(src).convert("RGBA")
    if name in AS_IS:
        im = im.resize((im.width * SCALE, im.height * SCALE), Image.NEAREST)
    else:
        box = im.getbbox()
        if box is None:
            raise SystemExit(f"{name}: the render is empty")
        im = im.crop(box)
        bw, bh = SIZE.get(name, DEFAULT_SIZE)
        scale = min(bw * SCALE / im.width, bh * SCALE / im.height)
        im = im.resize((max(1, round(im.width * scale)), max(1, round(im.height * scale))), Image.LANCZOS)
    height = -(-(im.height + 2 * PAD) // ROW) * ROW
    out = Image.new("RGBA", (WIDTH, height))
    out.alpha_composite(im, (WIDTH - PAD - im.width, (height - im.height) // 2))
    out.save(dst, optimize=True)
    print(f"{name}: {im.width // SCALE}x{im.height // SCALE} UI px in {WIDTH}x{height} -> {dst.relative_to(MODULE)}")
