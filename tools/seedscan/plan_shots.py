#!/usr/bin/env python3
"""Plans screenshot camera positions from a final dump.

python3 -I plan_shots.py <dump-dir> <world-dir>  >> shots.txt
Lines: seed worldDir name x y z yaw pitch (Minecraft yaw: 0 = south, 90 = west, 180 = north, -90 = east).
"""
import json
import math
import os
import sys

import numpy as np


def yaw_to(dx, dz):
    return math.degrees(math.atan2(-dx, dz))


def main():
    dump, world = sys.argv[1], os.path.abspath(sys.argv[2])
    s = json.load(open(os.path.join(dump, "summary.json")))
    seed, cx, cz = s["seed"], s["x"], s["z"]
    n = 375
    h = np.fromfile(os.path.join(dump, "detail_height_375.i16"), dtype=">i2").reshape(n, n).astype(int)
    xs = cx - 1500 + 4 + 8 * np.arange(n)
    zs = cz - 1500 + 4 + 8 * np.arange(n)
    X, Z = np.meshgrid(xs, zs)
    cheb = np.maximum(np.abs(X - cx), np.abs(Z - cz))

    def height_at(x, z):
        i = int(round((x - (cx - 1500 + 4)) / 8))
        j = int(round((z - (cz - 1500 + 4)) / 8))
        return h[min(max(j, 0), n - 1), min(max(i, 0), n - 1)]

    out = []
    near = np.hypot(X - cx, Z - cz) <= 250
    top = int(h[near].max()) + 60
    for name, yaw in (("aerial_north", 180), ("aerial_east", -90), ("aerial_south", 0), ("aerial_west", 90)):
        out.append((name, cx + 0.5, top, cz + 0.5, yaw, 30))

    # Ground shots in the biggest valleys inside the start or stage 2.
    g = json.load(open(os.path.join(dump, "grid.json")))
    vn, st = g["analysisN"], g["analysisStep"]
    ox, oz = g["analysisOrigin"]
    v = np.fromfile(os.path.join(dump, f"valley_{vn}.u8"), dtype=np.uint8).reshape(vn, vn)
    k = 0
    for r in s["valleyRegions"]:
        if r[3] > 1 or k == 3:
            continue
        # Lowest valley cell within 96 blocks of the region centroid.
        best = None
        for j in range(vn):
            for i in range(vn):
                if not v[j, i]:
                    continue
                x, z = ox + st / 2 + st * i, oz + st / 2 + st * j
                if math.hypot(x - r[1], z - r[2]) > 96 or abs(x - cx) > 1450 or abs(z - cz) > 1450:
                    continue
                hh = height_at(x, z)
                if best is None or hh < best[0]:
                    best = (hh, x, z)
        if best is None:
            continue
        hh, x, z = best
        # Look towards the highest ground within 300 blocks (the valley wall hiding the view).
        d = np.hypot(X - x, Z - z)
        ring = (d > 60) & (d <= 300)
        jj, ii = np.unravel_index(np.argmax(np.where(ring, h, -999)), h.shape)
        tx, tz = xs[ii], zs[jj]
        k += 1
        out.append((f"valley{k}", x + 0.5, hh + 1, z + 0.5, round(yaw_to(tx - x, tz - z), 1), -6))

    # Most striking terrain in stage 2: the highest point, seen from 350 blocks towards the centre.
    inner = cheb <= 750
    jj, ii = np.unravel_index(np.argmax(np.where(inner, h, -999)), h.shape)
    px, pz, ph = xs[ii], zs[jj], int(h[jj, ii])
    dx, dz = cx - px, cz - pz
    L = math.hypot(dx, dz)
    if L < 50:
        dx, dz, L = 0, 1, 1
    camx, camz = px + dx / L * 350, pz + dz / L * 350
    camy = max(height_at(camx, camz) + 30, ph - 40)
    pitch = -math.degrees(math.atan2((ph - 30) - camy, 350))
    out.append(("peak", round(camx, 1), camy, round(camz, 1), round(yaw_to(px - camx, pz - camz), 1), round(pitch, 1)))

    for name, x, y, z, yaw, pitch in out:
        print(seed, world, name, x, y, z, yaw, pitch)


if __name__ == "__main__":
    main()
