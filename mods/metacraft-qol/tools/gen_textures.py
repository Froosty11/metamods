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


def rift(frames=8, size=64):
	"""The rift's sprite: a torn, slanted lens of space. Its alpha is the shape the shader reads —
	1 inside, about 0.95 to 0.55 across the edge, fading to 0 in a halo — and its colours are the
	painted rift a client without the shader sees: dark space and a few stars inside, a magenta and
	teal edge. The edge crackles from frame to frame; the outline itself stays put.
	"""
	import math
	img = Image.new("RGBA", (size, size * frames))
	rng = random.Random(41)
	stars = [(rng.uniform(-0.6, 0.6), rng.uniform(-0.3, 0.3), rng.uniform(0.5, 1.0)) for _ in range(14)]
	tilt = math.radians(32)

	def boundary(theta, f):
		# a lens, lumpy in a fixed way, with a little per-frame crackle
		wobble = 0.10 * math.sin(3 * theta + 1.0) + 0.06 * math.sin(5 * theta + 2.3) + 0.04 * math.sin(9 * theta + 0.4)
		crackle = 0.035 * math.sin(17 * theta + f * 2.1) * math.sin(11 * theta - f * 1.3)
		return 1.0 + wobble + crackle

	for f in range(frames):
		for y in range(size):
			for x in range(size):
				u = (x + 0.5) / size * 2 - 1
				v = (y + 0.5) / size * 2 - 1
				# into the lens' own frame: tilted, wide and thin
				lu = u * math.cos(tilt) + v * math.sin(tilt)
				lv = -u * math.sin(tilt) + v * math.cos(tilt)
				r = math.hypot(lu / 0.86, lv / 0.42)
				theta = math.atan2(lv / 0.42, lu / 0.86)
				s = r / boundary(theta, f)          # 1 at the edge
				if s < 0.80:
					a = 1.0
				elif s < 1.0:
					a = 0.95 - (s - 0.80) / 0.20 * 0.40
				elif s < 1.30:
					a = 0.45 * (1 - (s - 1.0) / 0.30) ** 1.5
				else:
					continue
				if a >= 0.999:
					c = (14, 4, 30)
					for sx, sy, br in stars:
						if (lu - sx) ** 2 + (lv - sy) ** 2 < 0.0016:
							c = (int(200 * br + 40), int(170 * br + 30), 255)
				elif a > 0.5:
					mix = 0.5 + 0.5 * math.sin(theta * 3 + f * 0.8)
					c = (int(230 - 150 * mix), int(80 + 160 * mix), int(250 - 30 * mix))
				else:
					c = (150, 70, 220)
				img.putpixel((x, f * size + y), c + (max(1, int(round(a * 255))),))
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
