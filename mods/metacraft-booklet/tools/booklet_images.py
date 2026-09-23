#!/usr/bin/env python3
"""Turns BookletRenders' output into the page images Booklet shows.

    METACRAFT_BOOKLET_RENDER=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
    python3 mods/metacraft-booklet/tools/booklet_images.py

Every picture sits beside text (Beside.java: an image under beside/), where one image pixel is one UI
pixel, so each is made at its display size: an isometric render is cut to what was drawn (the
background is transparent) and scaled to fit SIZE; a screenshot cut-out (the stash menu, taken at GUI
scale 1) is already at its own pixels and is copied as it is. The results go to
src/main/resources/assets/metacraft/textures/booklet/image/beside/<name>.png.
"""
import shutil
from pathlib import Path
from PIL import Image

MODULE = Path(__file__).resolve().parent.parent
SRC = MODULE / "build/booklet-renders"
DST = MODULE / "src/main/resources/assets/metacraft/textures/booklet/image/beside"
# (width, height) the picture must fit, in UI pixels.
SIZE = {"ovvar/chapters": (140, 110), "canvas/wall": (100, 110)}
DEFAULT_SIZE = (100, 118)
AS_IS = {"ovvar/stash"}
PAD = 2

shutil.rmtree(DST, ignore_errors=True)
for src in sorted(SRC.rglob("*.png")):
    name = src.relative_to(SRC).with_suffix("").as_posix()
    if name.startswith("probe/"):
        continue
    dst = DST / f"{name}.png"
    dst.parent.mkdir(parents=True, exist_ok=True)
    if name in AS_IS:
        shutil.copyfile(src, dst)
        print(f"{name}: as is -> {dst.relative_to(MODULE)}")
        continue
    im = Image.open(src).convert("RGBA")
    box = im.getbbox()
    if box is None:
        raise SystemExit(f"{name}: the render is empty")
    im = im.crop(box)
    bw, bh = SIZE.get(name, DEFAULT_SIZE)
    scale = min(bw / im.width, bh / im.height)
    im = im.resize((max(1, round(im.width * scale)), max(1, round(im.height * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (im.width + 2 * PAD, im.height + 2 * PAD))
    out.alpha_composite(im, (PAD, PAD))
    out.save(dst, optimize=True)
    print(f"{name}: {out.size[0]}x{out.size[1]} -> {dst.relative_to(MODULE)}")
