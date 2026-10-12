"""Draws the nether rift's display sprites (what a client without the shader sees) and their models.
Run from the module: python3 tools/gen_textures.py"""
import json, math, random, struct, zlib
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/nether_rifts"
BODY_TINT = -66054   # 0xFFFEFDFA: the body marker at stage 1; Rift re-dyes it per stage
EDGE_TINT = -66055   # 0xFFFEFDF9

def png(path, w, h, pixels):
	raw = b"".join(b"\x00" + bytes(c for px in pixels[y * w:(y + 1) * w] for c in px) for y in range(h))
	def chunk(kind, data):
		return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
		+ chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def write_json(path, data):
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(data, indent="\t") + "\n")

def panel():
	"""16×64: four 16×16 frames of a purple-crimson swirl (the fallback when the shader is replaced)."""
	rnd, px = random.Random(5), []
	for frame in range(4):
		for y in range(16):
			for x in range(16):
				s = 0.5 + 0.5 * math.sin(x * 0.7 + y * 0.45 + frame * 1.6) * math.cos(y * 0.6 - x * 0.3 + frame)
				r, g, b = int(60 + 140 * s), int(10 + 20 * s), int(90 + 120 * (1 - s) * 0.8 + 40 * s)
				if rnd.random() < 0.03:
					r, g, b = 255, 150, 60
				px.append((r, g, b, 235))
	return px

def edge():
	"""16×16: bright along the middle row, fading out to both sides."""
	px = []
	for y in range(16):
		a = max(0.0, 1.0 - abs(y - 7.5) / 8.0) ** 1.5
		for _ in range(16):
			px.append((255, 140, 240, int(40 + 215 * a)))
	return px

def quad(texture):
	return {"textures": {"rift": f"nether_rifts:item/{texture}", "particle": f"nether_rifts:item/{texture}"},
		"elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "faces": {
			"north": {"uv": [0, 0, 16, 16], "texture": "#rift", "tintindex": 0},
			"south": {"uv": [0, 0, 16, 16], "texture": "#rift", "tintindex": 0}}}]}

def main():
	png(ASSETS / "textures/item/rift_panel.png", 16, 64, panel())
	write_json(ASSETS / "textures/item/rift_panel.png.mcmeta", {"animation": {"frametime": 4}})
	png(ASSETS / "textures/item/rift_edge.png", 16, 16, edge())
	for name, tint in (("rift_panel", BODY_TINT), ("rift_edge", EDGE_TINT)):
		write_json(ASSETS / "models/item" / f"{name}.json", quad(name))
		write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model",
			"model": f"nether_rifts:item/{name}", "tints": [{"type": "minecraft:dye", "default": tint}]}})
	print("wrote", ASSETS)

main()
