#!/usr/bin/env python3
"""Renders seedscan dumps (out/dump/<seed>/) into biome maps and a hillshaded heightmap.

Usage: python3 -I render.py <dump-dir> <out-dir> [rank]
Writes <seed>_map_3000.png, <seed>_map_8000.png and <seed>_height_3000.png.
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# Biome colours (the usual Amidst/cubiomes palette, plus the 26.x additions).
COLORS = {
    "ocean": (0, 0, 112), "deep_ocean": (0, 0, 48), "warm_ocean": (0, 0, 172), "lukewarm_ocean": (0, 0, 144),
    "deep_lukewarm_ocean": (0, 0, 64), "cold_ocean": (32, 32, 112), "deep_cold_ocean": (32, 32, 56),
    "frozen_ocean": (112, 112, 214), "deep_frozen_ocean": (64, 64, 144), "river": (0, 0, 255), "frozen_river": (160, 160, 255),
    "plains": (141, 179, 96), "sunflower_plains": (181, 219, 136), "snowy_plains": (255, 255, 255), "ice_spikes": (180, 220, 220),
    "desert": (250, 148, 24), "swamp": (7, 249, 178), "mangrove_swamp": (44, 204, 142), "forest": (5, 102, 33),
    "flower_forest": (45, 142, 73), "birch_forest": (48, 116, 68), "old_growth_birch_forest": (88, 156, 108),
    "dark_forest": (64, 81, 26), "pale_garden": (110, 104, 96), "dappled_forest": (90, 140, 40), "taiga": (11, 102, 89),
    "snowy_taiga": (49, 85, 74), "old_growth_pine_taiga": (89, 102, 81), "old_growth_spruce_taiga": (129, 142, 121),
    "windswept_hills": (96, 96, 96), "windswept_gravelly_hills": (136, 136, 136), "windswept_forest": (80, 112, 80),
    "windswept_savanna": (229, 218, 135), "savanna": (189, 178, 95), "savanna_plateau": (167, 157, 100),
    "jungle": (83, 123, 9), "sparse_jungle": (98, 139, 23), "bamboo_jungle": (118, 142, 20), "badlands": (217, 69, 21),
    "eroded_badlands": (255, 109, 61), "wooded_badlands": (176, 151, 101), "meadow": (96, 164, 69),
    "cherry_grove": (255, 183, 219), "grove": (71, 114, 108), "snowy_slopes": (196, 196, 196), "frozen_peaks": (160, 160, 180),
    "jagged_peaks": (220, 220, 200), "stony_peaks": (123, 143, 116), "beach": (250, 222, 85), "snowy_beach": (250, 240, 192),
    "stony_shore": (162, 162, 132), "mushroom_fields": (255, 0, 255), "lush_caves": (100, 200, 60), "deep_dark": (30, 50, 60),
    "dripstone_caves": (130, 100, 70), "sulfur_caves": (200, 200, 40),
}
RARE = {"mushroom_fields", "ice_spikes", "eroded_badlands", "bamboo_jungle", "cherry_grove", "pale_garden", "mangrove_swamp",
        "badlands", "wooded_badlands", "jungle", "sparse_jungle", "old_growth_birch_forest", "sunflower_plains", "dappled_forest"}
UNDERGROUND = ["lush_caves", "deep_dark", "dripstone_caves", "sulfur_caves"]
STAGES = [(1000, "start: 1000 wide", (255, 255, 255)), (1500, "stage 2: 1500 wide", (255, 220, 0)),
          (2000, "stage 3: 2000 wide", (255, 90, 90))]
# type -> (label, shape, fill)
MARKERS = {
    "village": ("village", "circle", (230, 30, 30)),
    "trial_chambers": ("trial chambers", "diamond", (255, 140, 0)),
    "ancient_city": ("ancient city", "square", (0, 230, 230)),
    "stronghold": ("stronghold", "star", (190, 60, 255)),
    "desert_pyramid": ("desert temple", "triangle", (255, 240, 60)),
    "jungle_pyramid": ("jungle temple", "triangle", (120, 255, 60)),
    "swamp_hut": ("swamp hut", "triangle", (60, 200, 160)),
    "pillager_outpost": ("pillager outpost", "cross", (40, 40, 40)),
    "monument": ("ocean monument", "circle", (120, 220, 255)),
    "mansion": ("woodland mansion", "square", (140, 80, 30)),
    "trail_ruins": ("trail ruins", "diamond", (210, 180, 140)),
    "igloo": ("igloo", "triangle", (230, 250, 255)),
}
BG = (26, 26, 30)


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT, size)


def pretty(name):
    return name.replace("_", " ")


def marker(d, x, y, shape, fill, r=6):
    o = (0, 0, 0)
    if shape == "circle":
        d.ellipse([x - r, y - r, x + r, y + r], fill=fill, outline=o, width=2)
    elif shape == "square":
        d.rectangle([x - r, y - r, x + r, y + r], fill=fill, outline=o, width=2)
    elif shape == "diamond":
        d.polygon([(x, y - r - 2), (x + r + 2, y), (x, y + r + 2), (x - r - 2, y)], fill=fill, outline=o, width=2)
    elif shape == "triangle":
        d.polygon([(x, y - r - 2), (x + r + 2, y + r), (x - r - 2, y + r)], fill=fill, outline=o, width=2)
    elif shape == "cross":
        d.line([x - r, y - r, x + r, y + r], fill=o, width=5)
        d.line([x - r, y + r, x + r, y - r], fill=o, width=5)
        d.line([x - r, y - r, x + r, y + r], fill=fill, width=3)
        d.line([x - r, y + r, x + r, y - r], fill=fill, width=3)
    elif shape == "star":
        pts = []
        for k in range(10):
            a = -np.pi / 2 + k * np.pi / 5
            rr = (r + 4) if k % 2 == 0 else (r + 4) * 0.45
            pts.append((x + rr * np.cos(a), y + rr * np.sin(a)))
        d.polygon(pts, fill=fill, outline=o, width=2)


def outlined_text(d, xy, text, f, fill):
    x, y = xy
    for dx in (-2, -1, 0, 1, 2):
        for dy in (-2, -1, 0, 1, 2):
            d.text((x + dx, y + dy), text, font=f, fill=(0, 0, 0))
    d.text((x, y), text, font=f, fill=fill)


def draw_stages(d, to_px, cx, cz, scale_label=True):
    f = font(17, True)
    for width, label, col in reversed(STAGES):
        h = width / 2
        x0, y0 = to_px(cx - h, cz - h)
        x1, y1 = to_px(cx + h, cz + h)
        d.rectangle([x0, y0, x1, y1], outline=(0, 0, 0), width=5)
        d.rectangle([x0, y0, x1, y1], outline=col, width=3)
        if scale_label:
            outlined_text(d, (x0 + 6, y0 + 5), label, f, (255, 255, 255))
    x, y = to_px(cx, cz)
    d.line([x - 12, y, x + 12, y], fill=(0, 0, 0), width=5)
    d.line([x, y - 12, x, y + 12], fill=(0, 0, 0), width=5)
    d.line([x - 11, y, x + 11, y], fill=(255, 255, 255), width=2)
    d.line([x, y - 11, x, y + 11], fill=(255, 255, 255), width=2)


def stage_of(dx, dz):
    c = max(abs(dx), abs(dz))
    for i, (w, _, _) in enumerate(STAGES):
        if c <= w / 2:
            return i
    return 3


def biome_rows(grid, names, step, cx, cz, x0, z0, summary):
    """Per biome: first stage present, presence per stage, share of stage-3 area."""
    n = grid.shape[0]
    xs = x0 + step / 2 + step * np.arange(n)
    zs = z0 + step / 2 + step * np.arange(n)
    dx = np.abs(xs[None, :] - cx)
    dz = np.abs(zs[:, None] - cz)
    cheb = np.maximum(dx, dz)
    rows = []
    in3 = cheb <= 1000
    total3 = in3.sum()
    min_px = max(1, int(2048 / (step * step)))  # at least ~2000 m2
    for b in np.unique(grid):
        name = names[b]
        present = []
        for w, _, _ in STAGES:
            present.append(int(((grid == b) & (cheb <= w / 2)).sum()) >= min_px)
        if not any(present):
            continue
        share = ((grid == b) & in3).sum() / total3
        rows.append((present.index(True), name, present, share))
    for name in UNDERGROUND:
        if name in summary["biomes"]:
            st = summary["biomes"][name][0]
            rows.append((st, name, [i >= st for i in range(3)], None))
    return rows


def legend(img_h, summary, rows, extra_lines, title_note):
    w = 400
    L = Image.new("RGB", (w, img_h), BG)
    d = ImageDraw.Draw(L)
    y = 10
    d.text((12, y), f"seed {summary['seed']}", font=font(20, True), fill=(255, 255, 255))
    y += 28
    d.text((12, y), f"border centre / spawn: {summary['x']}, {summary['z']}", font=font(14), fill=(220, 220, 220))
    y += 20
    d.text((12, y), title_note, font=font(13), fill=(180, 180, 180))
    y += 18
    d.text((12, y), "dots: start / stage 2 / stage 3 has it", font=font(13), fill=(180, 180, 180))
    y += 22
    # Marker legend.
    used = {s[0] for s in summary["structures"]}
    f13 = font(13)
    col = 0
    for t, (label, shape, fill) in MARKERS.items():
        if t not in used:
            continue
        counts = [sum(1 for s in summary["structures"] if s[0] == t and s[3] <= k) for k in range(3)]
        x = 12 + col * 195
        marker(d, x + 8, y + 8, shape, fill, 5)
        d.text((x + 22, y), f"{label} {counts[0]}/{counts[1]}/{counts[2]}", font=f13, fill=(230, 230, 230))
        col += 1
        if col == 2:
            col = 0
            y += 19
    if col:
        y += 19
    d.text((12, y), "(counts within start / stage 2 / stage 3)", font=font(11), fill=(160, 160, 160))
    y += 18
    for line in extra_lines:
        d.text((12, y), line, font=font(13), fill=(220, 220, 220))
        y += 18
    y += 4
    groups = [("In start (1000)", (255, 255, 255)), ("New in stage 2 (1500)", (255, 220, 0)), ("New in stage 3 (2000)", (255, 90, 90))]
    dotcols = [(255, 255, 255), (255, 220, 0), (255, 90, 90)]
    fb = font(16, True)
    row_h = 19 if len(rows) < 40 else 17
    for g, (title, tcol) in enumerate(groups):
        grp = [r for r in rows if r[0] == g]
        if not grp:
            continue
        grp.sort(key=lambda r: -(r[3] if r[3] is not None else -1))
        d.text((12, y), title, font=fb, fill=tcol)
        y += 22
        for _, name, present, share in grp:
            d.rectangle([14, y + 2, 28, y + 15], fill=COLORS.get(name, (128, 128, 128)), outline=(0, 0, 0))
            for k in range(3):
                x = 40 + k * 18
                if present[k]:
                    d.ellipse([x, y + 2, x + 13, y + 15], fill=dotcols[k], outline=(0, 0, 0))
                else:
                    d.ellipse([x, y + 2, x + 13, y + 15], outline=(90, 90, 90), width=1)
            rare = name in RARE
            label = pretty(name) + (" (underground)" if share is None else "")
            d.text((100, y), label + (" ★ rare" if rare else ""), font=f13, fill=(255, 215, 0) if rare else (230, 230, 230))
            if share is not None:
                d.text((340, y), f"{share * 100:4.1f}%", font=f13, fill=(200, 200, 200))
            y += row_h
        y += 6
    return L, y


def biome_image(grid, names):
    pal = np.array([COLORS.get(n, (128, 128, 128)) for n in names] + [(0, 0, 0)] * (256 - len(names)), dtype=np.uint8)
    return Image.fromarray(pal[grid], "RGB")


def render_map(dump, out, summary, names, wide):
    cx, cz = summary["x"], summary["z"]
    if wide == 3000:
        n, step, half = 750, 4, 1500
        grid = np.fromfile(os.path.join(dump, "detail_biome_750.u8"), dtype=np.uint8).reshape(n, n)
    else:
        n, step, half = 500, 16, 4000
        grid = np.fromfile(os.path.join(dump, "context_biome_500.u8"), dtype=np.uint8).reshape(n, n)
    size = 1000
    img = biome_image(grid, names).resize((size, size), Image.NEAREST)
    d = ImageDraw.Draw(img)
    to_px = lambda x, z: ((x - (cx - half)) / (2 * half) * size, (z - (cz - half)) / (2 * half) * size)
    draw_stages(d, to_px, cx, cz)
    # Draw rarer markers last so villages do not hide them.
    order = ["village", "pillager_outpost", "igloo", "swamp_hut", "trail_ruins", "desert_pyramid", "jungle_pyramid", "monument",
             "mansion", "trial_chambers", "ancient_city", "stronghold"]
    for t in order:
        shape, fill = MARKERS[t][1], MARKERS[t][2]
        for s in summary["structures"]:
            if s[0] != t:
                continue
            x, y = to_px(s[1], s[2])
            if 0 <= x < size and 0 <= y < size:
                marker(d, x, y, shape, fill, 6 if wide == 3000 else 4)
    rows = biome_rows(grid, names, step, cx, cz, cx - half, cz - half, summary)
    ocean = summary["oceanStart"] * 100
    extra = [f"ocean in start: {ocean:.0f}%   valleys in stage 2: {summary['valleyStage2'] * 100:.0f}% of land"]
    note = f"map {2 * half} blocks wide · {step} blocks per pixel" if wide == 3000 else f"context map {2 * half} blocks wide"
    L, used = legend(size, summary, rows, extra, note)
    H = max(size, used + 10)
    canvas = Image.new("RGB", (size + L.width, H), BG)
    canvas.paste(img, (0, 0))
    canvas.paste(L.crop((0, 0, L.width, H)) if used + 10 <= L.height else L, (size, 0))
    if used + 10 > L.height:
        L2, _ = legend(H, summary, rows, extra, note)
        canvas.paste(L2, (size, 0))
    canvas.save(os.path.join(out, f"{summary['seed']}_map_{wide}.png"), optimize=True)


def render_height(dump, out, summary):
    cx, cz = summary["x"], summary["z"]
    n = 375
    raw = np.fromfile(os.path.join(dump, "detail_height_375.i16"), dtype=">i2").reshape(n, n).astype(np.float32)
    size = 1000
    h = np.array(Image.fromarray(raw).resize((size, size), Image.BILINEAR))
    scale = 3000 / size  # blocks per pixel
    gy, gx = np.gradient(h, scale)
    az, alt = np.radians(315), np.radians(40)
    slope = np.arctan(np.hypot(gx, gy) * 1.5)
    aspect = np.arctan2(-gx, gy)
    shade = np.sin(alt) * np.cos(slope) + np.cos(alt) * np.sin(slope) * np.cos(az - aspect)
    shade = np.clip(shade, 0, 1)
    # Elevation colours.
    stops = [(40, (20, 60, 140)), (63, (40, 100, 190)), (64, (70, 140, 70)), (90, (120, 170, 80)), (130, (190, 180, 110)),
             (180, (150, 120, 90)), (230, (140, 140, 140)), (300, (245, 245, 245))]
    xs = [s[0] for s in stops]
    rgb = np.zeros((size, size, 3), np.float32)
    for c in range(3):
        rgb[..., c] = np.interp(h, xs, [s[1][c] for s in stops])
    water = h <= 63.5
    lit = rgb * (0.35 + 0.75 * shade[..., None])
    lit[water] = rgb[water] * 0.9
    img = Image.fromarray(np.clip(lit, 0, 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(img)
    to_px = lambda x, z: ((x - (cx - 1500)) / 3000 * size, (z - (cz - 1500)) / 3000 * size)
    # Valley outlines from the 16-block analysis grid.
    g = json.load(open(os.path.join(dump, "grid.json")))
    vn = g["analysisN"]
    v = np.fromfile(os.path.join(dump, f"valley_{vn}.u8"), dtype=np.uint8).reshape(vn, vn)
    ox, oz, st = g["analysisOrigin"][0], g["analysisOrigin"][1], g["analysisStep"]
    vimg = Image.fromarray(v * 255).resize((int(vn * st / scale), int(vn * st / scale)), Image.NEAREST)
    big = Image.new("L", (size, size), 0)
    px, pz = to_px(ox, oz)
    big.paste(vimg, (int(round(px)), int(round(pz))))
    m = np.array(big) > 0
    edge = m & ~(np.roll(m, 1, 0) & np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1))
    arr = np.array(img)
    tint = arr[m].astype(np.float32) * 0.75 + np.array([255, 60, 200]) * 0.25
    arr[m] = tint.astype(np.uint8)
    arr[edge] = (255, 40, 200)
    img = Image.fromarray(arr)
    d = ImageDraw.Draw(img)
    draw_stages(d, to_px, cx, cz)
    f = font(15, True)
    regions = summary["valleyRegions"]
    for k, r in enumerate(regions[:6]):
        x, y = to_px(r[1], r[2])
        outlined_text(d, (x - 6, y - 9), str(k + 1), f, (255, 255, 255))
    L = Image.new("RGB", (400, size), BG)
    ld = ImageDraw.Draw(L)
    y = 10
    ld.text((12, y), f"seed {summary['seed']}", font=font(20, True), fill=(255, 255, 255))
    y += 28
    ld.text((12, y), f"border centre / spawn: {summary['x']}, {summary['z']}", font=font(14), fill=(220, 220, 220))
    y += 20
    ld.text((12, y), "hillshaded terrain, 3000 blocks wide (real heights)", font=font(13), fill=(180, 180, 180))
    y += 40
    for i in range(0, 260, 2):
        hh = 40 + i
        c = [int(np.interp(hh, xs, [s[1][k] for s in stops])) for k in range(3)]
        ld.line([20, y + 250 - i, 50, y + 250 - i], fill=tuple(c), width=2)
    for hh in (63, 100, 150, 200, 250, 300):
        yy = y + 250 - (hh - 40)
        ld.text((58, yy - 8), f"y={hh}", font=font(12), fill=(220, 220, 220))
    ld.rectangle([150, y + 4, 166, y + 18], fill=(200, 100, 190), outline=(255, 40, 200), width=2)
    ld.text((174, y + 3), "valley", font=font(13), fill=(230, 230, 230))
    ld.text((150, y + 24), "≥40 blocks below the highest\nground within 256 blocks on\ntwo opposite sides", font=font(12), fill=(180, 180, 180))
    y += 275
    ld.text((12, y), "valley share of land:", font=font(15, True), fill=(255, 255, 255))
    y += 22
    for label, key in (("start", "valleyStart"), ("stage 2", "valleyStage2"), ("stage 3", "valleyStage3")):
        ld.text((20, y), f"{label}: {summary[key] * 100:.0f}%", font=font(14), fill=(230, 230, 230))
        y += 19
    y += 8
    ld.text((12, y), "biggest valleys (inside stage 3):", font=font(15, True), fill=(255, 255, 255))
    y += 22
    names = ["start", "stage 2", "stage 3"]
    for k, r in enumerate(regions[:6]):
        ld.text((20, y), f"{k + 1}. ~{int(r[0] ** 0.5)}x{int(r[0] ** 0.5)} blocks at {r[1]}, {r[2]} ({names[r[3]]})", font=font(13), fill=(230, 230, 230))
        y += 19
    canvas = Image.new("RGB", (size + 400, size), BG)
    canvas.paste(img, (0, 0))
    canvas.paste(L, (size, 0))
    canvas.save(os.path.join(out, f"{summary['seed']}_height_3000.png"), optimize=True)


def main():
    dump, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    summary = json.load(open(os.path.join(dump, "summary.json")))
    names = [l.strip() for l in open(os.path.join(dump, "biomes.txt")) if l.strip()]
    render_map(dump, out, summary, names, 3000)
    render_map(dump, out, summary, names, 8000)
    render_height(dump, out, summary)


if __name__ == "__main__":
    main()
