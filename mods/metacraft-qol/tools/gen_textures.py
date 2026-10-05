"""Draws the void anchor's placeholder textures. Run from the module: python3 tools/gen_textures.py

Everything is drawn from a fixed seed, so a rerun gives the same files. Replace the PNGs with real
art whenever; nothing else depends on this script.
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/metacraft/textures"
BLOCK = ROOT / "block"
ITEM = ROOT / "item"

OBSIDIAN = [(20, 10, 34), (28, 14, 46), (36, 18, 60), (46, 22, 76)]
CRYING = [(120, 40, 200), (150, 70, 230)]
RIM = [(92, 56, 120), (110, 70, 140), (78, 46, 104)]
VOID = (6, 2, 12)
PIP_OFF = (40, 24, 56)
PIP_ON = [(200, 130, 255), (240, 200, 255)]


def obsidian(rng, size=16, crying=0.04):
	img = Image.new("RGBA", (size, size))
	for y in range(size):
		for x in range(size):
			c = rng.choice(OBSIDIAN)
			if rng.random() < crying:
				c = rng.choice(CRYING)
			img.putpixel((x, y), c + (255,))
	return img


def side(charge):
	rng = random.Random(11)
	img = obsidian(rng)
	# A darker band across the middle holds four pips, lit left to right.
	for x in range(1, 15):
		for y in range(6, 11):
			img.putpixel((x, y), (14, 6, 24, 255))
	for i, x0 in enumerate((2, 5, 9, 12)):
		lit = i < charge
		for dx in range(2):
			for dy in range(3):
				c = (PIP_ON[(dx + dy) % 2] if lit else PIP_OFF)
				img.putpixel((x0 + dx, 7 + dy), c + (255,))
	return img


def top():
	rng = random.Random(23)
	img = Image.new("RGBA", (16, 16))
	for y in range(16):
		for x in range(16):
			d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
			if d < 4.6:
				c = VOID
				if rng.random() < 0.06:
					c = (220, 200, 255)
			elif d < 5.6:
				c = (90, 30, 150)
			else:
				c = rng.choice(RIM)
			img.putpixel((x, y), c + (255,))
	return img


def rift(frames=16, size=32):
	"""The painted rift: a purple swirl in a soft disc, as a vertical strip of animation frames.

	It is purple only, on purpose: the item shader adds the teal, so teal on screen shows the shader ran.
	"""
	import math
	img = Image.new("RGBA", (size, size * frames))
	c = (size - 1) / 2
	for f in range(frames):
		turn = 2 * math.pi * f / frames
		for y in range(size):
			for x in range(size):
				dx, dy = x - c, y - c
				r = math.hypot(dx, dy) / c
				if r > 1:
					continue
				a = math.atan2(dy, dx)
				band = 0.5 + 0.5 * math.sin(3 * a + 7 * r - turn * 3)
				core = max(0.0, 1 - r * 1.6)
				red = int(40 + 110 * band * r + 30 * core)
				blue = int(70 + 160 * band * r + 60 * core)
				green = int(10 + 20 * band * r)
				rim = max(0.0, (r - 0.82) / 0.18)
				red, green, blue = (int(v + (230 - v) * rim * 0.6) for v in (red, green, blue))
				alpha = int(255 * min(1.0, (1 - r) * 6))
				img.putpixel((x, f * size + y), (min(red, 255), min(green, 120), min(blue, 255), alpha))
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
	BLOCK.mkdir(parents=True, exist_ok=True)
	for charge in range(5):
		side(charge).save(BLOCK / f"void_anchor_side_{charge}.png")
	top().save(BLOCK / "void_anchor_top.png")
	obsidian(random.Random(5), crying=0.0).save(BLOCK / "void_anchor_bottom.png")
	ITEM.mkdir(parents=True, exist_ok=True)
	rift().save(ITEM / "rift.png")
	muffler().save(ITEM / "muffler.png")


if __name__ == "__main__":
	main()
