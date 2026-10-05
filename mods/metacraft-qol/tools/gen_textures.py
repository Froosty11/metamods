"""Draws the void anchor's placeholder textures. Run from the module: python3 tools/gen_textures.py

Everything is drawn from a fixed seed, so a rerun gives the same files. Replace the PNGs with real
art whenever; nothing else depends on this script.
"""
import random
from pathlib import Path

from PIL import Image

import anchor_art

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/metacraft/textures"
BLOCK = ROOT / "block"
ITEM = ROOT / "item"

OBSIDIAN = [(20, 10, 34), (28, 14, 46), (36, 18, 60), (46, 22, 76)]
CRYING = [(120, 40, 200), (150, 70, 230)]
RIM = [(92, 56, 120), (110, 70, 140), (78, 46, 104)]
VOID = (6, 2, 12)
PIP_OFF = (40, 24, 56)
PIP_ON = [(200, 130, 255), (240, 200, 255)]


def rift(frames=4, size=64):
	"""The rift's sprite: a crack in space. A jagged main split runs along the sprite's x axis (the
	display stretches along x first, then widens it), widest in the middle, with hairline branches
	off it. Its alpha is the map the shader reads: 1 inside the split (the view into the void),
	about 0.95 to 0.55 on the edges and along the hairlines (the glowing cracks), under 0.45 in the
	halo. Its colours are the painted crack a client without the shader sees. The frames only
	flicker the glow; the cracks stay put.
	"""
	import math
	rng = random.Random(53)

	def jagged(p0, p1, depth, rough):
		"""A line from p0 to p1, split and nudged sideways `depth` times."""
		pts = [p0, p1]
		for _ in range(depth):
			out = [pts[0]]
			for q0, q1 in zip(pts, pts[1:]):
				mx, my = (q0[0] + q1[0]) / 2, (q0[1] + q1[1]) / 2
				dx, dy = q1[0] - q0[0], q1[1] - q0[1]
				length = math.hypot(dx, dy)
				nudge = rng.uniform(-rough, rough) * length
				out += [(mx - dy / length * nudge, my + dx / length * nudge), q1]
			pts = out
		return pts

	main = jagged((-0.94, 0.03), (0.94, -0.04), 4, 0.42)
	branches = []
	for k in range(10):
		# spread the branches along the split, alternating sides, leaning outward
		i = 2 + (k * (len(main) - 4)) // 10 + rng.randrange(0, 2)
		ox, oy = main[min(i, len(main) - 3)]
		side = 1 if k % 2 else -1
		lean = 1 if ox > 0 else -1
		angle = rng.uniform(0.45, 1.15)
		length = rng.uniform(0.22, 0.55)
		end = (ox + math.cos(angle) * length * lean, oy + math.sin(angle) * length * side)
		branch = jagged((ox, oy), end, 3, 0.45)
		branches.append(branch)
		if rng.random() < 0.6:
			j = len(branch) // 2
			bx, by = branch[j]
			fork = rng.uniform(0.4, 0.8) * side
			twig_len = length * rng.uniform(0.35, 0.6)
			twig = (bx + math.cos(angle + fork) * twig_len * lean, by + math.sin(angle + fork) * twig_len * side)
			branches.append(jagged((bx, by), twig, 2, 0.45))

	def nearest(pts, x, y):
		"""Distance to a polyline, and how far along it (0..1) the nearest point is."""
		best, along, run = 9.0, 0.0, 0.0
		total = sum(math.hypot(q1[0] - q0[0], q1[1] - q0[1]) for q0, q1 in zip(pts, pts[1:]))
		for q0, q1 in zip(pts, pts[1:]):
			dx, dy = q1[0] - q0[0], q1[1] - q0[1]
			seg = math.hypot(dx, dy)
			t = max(0.0, min(1.0, ((x - q0[0]) * dx + (y - q0[1]) * dy) / (seg * seg)))
			d = math.hypot(x - q0[0] - t * dx, y - q0[1] - t * dy)
			if d < best:
				best, along = d, (run + t * seg) / total
			run += seg
		return best, along

	img = Image.new("RGBA", (size, size * frames))
	px = 2.0 / size
	stars = [(rng.uniform(-0.5, 0.5), rng.uniform(-0.06, 0.06)) for _ in range(6)]
	for y in range(size):
		for x in range(size):
			u = (x + 0.5) / size * 2 - 1
			v = (y + 0.5) / size * 2 - 1
			d, along = nearest(main, u, v)
			width = 0.075 * max(0.0, math.sin(math.pi * along)) ** 0.7         # widest mid-way, closed at the tips
			edge = d - width
			hair = min((nearest(b, u, v)[0] for b in branches), default=9.0)
			if edge < 0:
				a = 1.0
			elif edge < 1.6 * px:
				a = 0.95 - edge / (1.6 * px) * 0.4
			elif hair < 0.7 * px:
				a = 0.82
			else:
				glow = min(edge, hair)
				a = 0.45 * math.exp(-glow / 0.05) if glow < 0.25 else 0.0
				if a < 0.02:
					continue
			for f in range(frames):
				flicker = 1.0 + 0.12 * math.sin(f * 1.9 + u * 7)
				if a >= 0.999:
					c = (10, 4, 24)
					for sx, sy in stars:
						if (u - sx) ** 2 + (v - sy) ** 2 < 0.0009:
							c = (210, 190, 255)
				elif a > 0.5:
					c = (255, int(min(255, 200 * flicker)), 255)
				else:
					t = min(1.0, a / 0.45)
					c = (int(150 + 60 * t), int(60 + 40 * t), int(220 + 30 * t))
				alpha = max(1, min(255, int(round(a * 255 * (flicker if a < 0.5 else 1.0)))))
				img.putpixel((x, f * size + y), c + (alpha,))
	return img


def muffler():
	"""A puff of grey-white wool bound with string, an amethyst shard tucked in it."""
	img = Image.new("RGBA", (16, 16))
	rng = random.Random(31)
	wool = [(232, 232, 228), (214, 214, 210), (196, 196, 194), (244, 244, 240)]
	for y in range(16):
		for x in range(16):
			d = ((x - 7.5) / 6.2) ** 2 + ((y - 8.5) / 5.4) ** 2
			if d <= 1.0:
				shade = rng.choice(wool)
				if d > 0.7:
					shade = (176, 176, 174)
				img.putpixel((x, y), shade + (255,))
	for x in range(2, 14):
		img.putpixel((x, 9), (120, 96, 70, 255))
	for (x, y), c in {(7, 4): (186, 128, 255), (8, 4): (150, 90, 230), (7, 5): (150, 90, 230), (8, 5): (110, 60, 190),
			(8, 3): (220, 180, 255), (9, 4): (110, 60, 190)}.items():
		img.putpixel((x, y), c + (255,))
	return img


def main():
	anchor_art.draw_all(BLOCK)
	ITEM.mkdir(parents=True, exist_ok=True)
	rift().save(ITEM / "rift.png")
	muffler().save(ITEM / "muffler.png")

if __name__ == "__main__":
	main()
