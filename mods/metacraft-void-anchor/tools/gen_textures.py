"""Draws the void anchor's placeholder textures. Run from the module: python3 tools/gen_textures.py

Everything is drawn from a fixed seed, so a rerun gives the same files. Replace the PNGs with real
art whenever; nothing else depends on this script.
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/metacraft/textures"
BLOCK = ROOT / "block"

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


def main():
	BLOCK.mkdir(parents=True, exist_ok=True)
	for charge in range(5):
		side(charge).save(BLOCK / f"void_anchor_side_{charge}.png")
	top().save(BLOCK / "void_anchor_top.png")
	obsidian(random.Random(5), crying=0.0).save(BLOCK / "void_anchor_bottom.png")


if __name__ == "__main__":
	main()
