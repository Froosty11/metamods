"""Writes nether-rifts' Blockbench models (Generic Model, format 5.0) for BIL to load.
Run from the module: python3 tools/gen_models.py
Each model is groups (bones) of cubes, one square texture packed and painted here, and animations.
Models face north: -Z is the front (BIL turns them around). 16 units = 1 block, feet at y = 0.
Re-running gives byte-identical files; edit this script, not the .bbmodel files.
They open in Blockbench (File > Open) for a look; to keep a hand edit, port it back here."""
import base64
import json
import random
import struct
import uuid
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/model/nether_rifts"
NS = uuid.UUID("2f0c0a5e-8d7b-4c1e-9b65-6e657468727a")

PALETTE = {
	"skin": (217, 149, 140), "skin_dark": (184, 116, 108), "snout": (240, 181, 171),
	"gold": (244, 197, 66), "leather": (107, 74, 43), "cloth": (96, 64, 44),
	"drum": (139, 30, 30), "bone": (239, 230, 207), "banner": (150, 28, 34),
	"wood": (58, 42, 30), "lens": (127, 212, 255), "charge": (255, 122, 26),
	"strap": (94, 64, 38), "saddle": (139, 30, 30),
}
SHADE = {"up": 1.08, "down": 0.72, "north": 1.0, "south": 0.9, "east": 0.84, "west": 0.84}


def uid(*parts):
	return str(uuid.uuid5(NS, "/".join(parts)))


def png(w, h, rows):
	raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)
	def chunk(kind, data):
		return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
	return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
		+ chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


class Model:
	def __init__(self, name, size=128):
		self.name, self.size = name, size
		self.groups, self.elements, self.animations = [], [], []
		self.children, self.roots = {}, []
		self.paint = []
		self._x = self._y = self._row = 0

	def group(self, name, origin, parent=None):
		self.groups.append({"name": name, "uuid": uid(self.name, "g", name), "export": True,
			"origin": list(origin), "rotation": [0, 0, 0]})
		self.children[name] = []
		(self.children[parent].append(("g", name)) if parent else self.roots.append(name))
		return name

	def cube(self, group, name, frm, to, material, decorate=None):
		cid = uid(self.name, "c", group, name)
		dx, dy, dz = (to[i] - frm[i] for i in range(3))
		sizes = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
		faces = {}
		for face, (w, h) in sizes.items():
			rect = self._alloc(w, h)
			faces[face] = {"uv": list(rect), "texture": 0}
			self.paint.append((rect, material, face, decorate))
		self.elements.append({"name": name, "export": True, "from": list(frm), "to": list(to), "autouv": 0,
			"origin": list(frm), "faces": faces, "type": "cube", "uuid": cid})
		self.children[group].append(("c", cid))

	def _alloc(self, w, h):
		# A face gets at most 16x16 texels (stretched over bigger faces) and at least 1x1.
		tw, th = max(1, min(16, round(w))), max(1, min(16, round(h)))
		if self._x + tw > self.size:
			self._x, self._y, self._row = 0, self._y + self._row, 0
		if self._y + th > self.size:
			raise ValueError(f"{self.name}: the {self.size}px texture is full")
		rect = (self._x, self._y, self._x + tw, self._y + th)
		self._x += tw
		self._row = max(self._row, th)
		return rect

	def animation(self, name, length, loop, tracks):
		"""tracks: {group: {channel: [(time, (x, y, z)), ...]}}; channel is rotation, position or scale."""
		animators = {}
		for group, channels in tracks.items():
			guid = uid(self.name, "g", group)
			keyframes = []
			for channel, points in channels.items():
				for t, (x, y, z) in points:
					keyframes.append({"channel": channel, "data_points": [{"x": str(x), "y": str(y), "z": str(z)}],
						"uuid": uid(self.name, "k", name, group, channel, str(t)), "time": t, "interpolation": "linear"})
			animators[guid] = {"name": group, "type": "bone", "keyframes": keyframes}
		self.animations.append({"uuid": uid(self.name, "a", name), "name": name, "loop": loop,
			"length": length, "animators": animators})

	def _outliner(self, name):
		kids = [ref if kind == "c" else self._outliner(ref) for kind, ref in self.children[name]]
		return {"uuid": uid(self.name, "g", name), "isOpen": False, "children": kids}

	def _texture(self):
		rnd = random.Random(self.name)
		rows = [[(0, 0, 0, 0)] * self.size for _ in range(self.size)]
		for (x0, y0, x1, y1), material, face, decorate in self.paint:
			base = PALETTE[material]
			for y in range(y0, y1):
				for x in range(x0, x1):
					noise = 1 + (rnd.random() - 0.5) * 0.12
					border = 0.82 if (x1 - x0 > 2 and y1 - y0 > 2) and (x in (x0, x1 - 1) or y in (y0, y1 - 1)) else 1.0
					rows[y][x] = tuple(max(0, min(255, int(c * SHADE[face] * noise * border))) for c in base) + (255,)
			if decorate:
				decorate(face, rows, (x0, y0, x1, y1))
		return png(self.size, self.size, rows)

	def write(self):
		data = {"meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
			"name": self.name, "model_identifier": self.name,
			"resolution": {"width": self.size, "height": self.size},
			"elements": self.elements, "groups": self.groups,
			"outliner": [self._outliner(n) for n in self.roots],
			"textures": [{"name": f"{self.name}.png", "id": "0", "width": self.size, "height": self.size,
				"uv_width": self.size, "uv_height": self.size, "particle": False, "frame_time": 1,
				"uuid": uid(self.name, "t"),
				"source": "data:image/png;base64," + base64.b64encode(self._texture()).decode()}],
			"animations": self.animations}
		OUT.mkdir(parents=True, exist_ok=True)
		path = OUT / f"{self.name}.bbmodel"
		path.write_text(json.dumps(data, indent="\t") + "\n")
		print("wrote", path)


def piglin_face(face, rows, rect):
	"""Eyes and tusks on a piglin skull's front."""
	if face != "north":
		return
	x0, y0, x1, y1 = rect
	w, h = x1 - x0, y1 - y0
	ey = y0 + max(1, h * 3 // 8)
	for ex in (x0 + max(1, w // 5), x1 - 1 - max(1, w // 5)):
		rows[ey][ex] = (250, 250, 250, 255)
		rows[ey][ex + (1 if ex < x0 + w // 2 else -1)] = (30, 20, 20, 255)
	ty = y1 - 2
	for tx in (x0 + w // 3, x1 - 1 - w // 3):
		rows[ty][tx] = (255, 248, 224, 255)


def snout_emblem(face, rows, rect):
	"""A gold piglin snout on the banner."""
	if face not in ("north", "south"):
		return
	x0, y0, x1, y1 = rect
	cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
	for x in range(cx - 2, cx + 2):
		for y in range(cy - 1, cy + 1):
			rows[y][x] = (244, 197, 66, 255)
	rows[cy][cx - 1] = rows[cy][cx] = (150, 28, 34, 255)


def head(m, parent, y):
	"""A piglin head on a neck at height y: skull, snout, ears. Returns the head group's name."""
	m.group("head", (0, y, 0), parent=parent)
	m.cube("head", "skull", (-5, y, -4), (5, y + 8, 4), "skin", decorate=piglin_face)
	m.cube("head", "snout", (-2, y + 1, -5), (2, y + 4, -4), "snout")
	m.cube("head", "ear_left", (-6, y + 2, -1), (-5, y + 7, 2), "skin_dark")
	m.cube("head", "ear_right", (5, y + 2, -1), (6, y + 7, 2), "skin_dark")
	return "head"


def war_drummer():
	m = Model("war_drummer")
	m.group("body", (0, 12, 0))
	m.cube("body", "torso", (-4, 12, -2), (4, 24, 2), "skin")
	m.cube("body", "belt", (-4.5, 12, -2.5), (4.5, 14, 2.5), "leather")
	head(m, "body", 24)
	m.cube("head", "helm", (-5.5, 31, -4.5), (5.5, 34, 4.5), "gold")
	m.cube("head", "horn_left", (-7.5, 32, -1), (-5.5, 37, 1), "bone")
	m.cube("head", "horn_right", (5.5, 32, -1), (7.5, 37, 1), "bone")
	# The model faces -Z, so its right hand is +X ("left" is -X). Checked against a client screenshot: BIL's
	# 180 degree turn leaves the entity's right on the model's +X. Positive X rotation swings an arm forward.
	for side, s in (("left", -1), ("right", 1)):
		g = m.group(f"arm_{side}", (6 * s, 22, 0), parent="body")
		lo, hi = sorted((4 * s, 8 * s))
		m.cube(g, "arm", (lo, 13, -2), (hi, 24, 2), "skin")
		m.cube(g, "band", (lo, 21, -2.5), (hi, 23, 2.5), "gold")
		m.cube(g, "stick", (6 * s - 0.5, 12, -9), (6 * s + 0.5, 13, -2), "bone")
		g = m.group(f"leg_{side}", (2 * s, 12, 0), parent="body")
		lo, hi = sorted((0, 4 * s))
		m.cube(g, "leg", (lo, 0, -2), (hi, 12, 2), "leather")
		m.cube(g, "boot", (lo - 0.5, 0, -2.5), (hi + 0.5, 2, 2.5), "gold")
	m.group("drum", (0, 18, -6), parent="body")
	m.cube("drum", "shell", (-5, 14, -9), (5, 22, -3), "drum")
	m.cube("drum", "rim_top", (-5.5, 21, -9.5), (5.5, 22.5, -2.5), "gold")
	m.cube("drum", "rim_bottom", (-5.5, 13.5, -9.5), (5.5, 15, -2.5), "gold")
	m.group("banner", (0, 24, 3), parent="body")
	m.cube("banner", "pole", (-0.5, 14, 2.5), (0.5, 40, 3.5), "wood")
	m.cube("banner", "flag", (0.5, 29, 3), (9.5, 39, 3.5), "banner", decorate=snout_emblem)

	still = (0, 0, 0)
	m.animation("idle", 2.0, "loop", {
		"arm_right": {"rotation": [(0, still), (0.5, (35, 0, 0)), (1.0, still), (2.0, still)]},
		"arm_left": {"rotation": [(0, still), (1.0, still), (1.5, (35, 0, 0)), (2.0, still)]},
		"body": {"position": [(0, still), (0.5, (0, 0.5, 0)), (1.0, still), (1.5, (0, 0.5, 0)), (2.0, still)]},
		"banner": {"rotation": [(0, (0, 0, -4)), (1.0, (0, 0, 4)), (2.0, (0, 0, -4))]},
	})
	m.animation("beat", 0.25, "once", {
		"arm_right": {"rotation": [(0, (30, 0, 0)), (0.1, (55, 0, 0)), (0.25, still)]},
		"arm_left": {"rotation": [(0, (30, 0, 0)), (0.1, (55, 0, 0)), (0.25, still)]},
		"drum": {"scale": [(0, (1, 1, 1)), (0.1, (1.06, 1.06, 1.06)), (0.25, (1, 1, 1))]},
	})
	m.animation("big_beat", 0.5, "once", {
		"arm_right": {"rotation": [(0, still), (0.2, (120, 0, 0)), (0.3, (-10, 0, 0)), (0.5, still)]},
		"arm_left": {"rotation": [(0, still), (0.2, (120, 0, 0)), (0.3, (-10, 0, 0)), (0.5, still)]},
		"drum": {"scale": [(0, (1, 1, 1)), (0.3, (1.15, 1.15, 1.15)), (0.5, (1, 1, 1))]},
		"body": {"position": [(0, still), (0.3, (0, -1, 0)), (0.5, still)]},
		"head": {"rotation": [(0, still), (0.3, (-15, 0, 0)), (0.5, still)]},
	})
	m.write()


def ghast_bombardier():
	"""A seated piglin rider: goggles, a satchel of fire charges, a charge in the raised right hand."""
	# Same axes as war_drummer: the entity's right is +X ("left" bones sit at -X), and positive X rotation
	# swings a limb forward. The satchel hangs on the left hip, away from the throwing hand.
	m = Model("ghast_bombardier")
	m.group("body", (0, 6, 0))
	m.cube("body", "torso", (-4, 4, -2), (4, 14, 2), "cloth")
	m.cube("body", "satchel", (-7, 5, -1.5), (-4, 10, 1.5), "leather")
	m.cube("body", "satchel_charge", (-6.5, 10, -1), (-4.5, 12, 1), "charge")
	head(m, "body", 14)
	m.cube("head", "goggle_band", (-5.5, 18, -4.5), (5.5, 20, 4.5), "leather")
	m.cube("head", "lens_left", (-4, 18, -5), (-1, 20, -4.5), "lens")
	m.cube("head", "lens_right", (1, 18, -5), (4, 20, -4.5), "lens")
	m.group("arm_right", (5, 13, 0), parent="body")
	m.cube("arm_right", "arm", (4, 4, -2), (8, 14, 2), "skin")
	m.cube("arm_right", "charge", (4.5, 1, -1.5), (7.5, 4, 1.5), "charge")
	m.group("arm_left", (-5, 13, 0), parent="body")
	m.cube("arm_left", "arm", (-8, 4, -2), (-4, 14, 2), "skin")
	m.group("legs", (0, 4, 0), parent="body")
	m.cube("legs", "leg_left", (-3.5, 0, -8), (-0.5, 4, 0), "leather")
	m.cube("legs", "leg_right", (0.5, 0, -8), (3.5, 4, 0), "leather")

	still = (0, 0, 0)
	m.animation("idle", 1.6, "loop", {
		"head": {"rotation": [(0, (0, -10, 0)), (0.8, (0, 10, 0)), (1.6, (0, -10, 0))]},
		"body": {"position": [(0, still), (0.8, (0, 0.3, 0)), (1.6, still)]},
	})
	m.animation("drop_bomb", 0.4, "once", {
		"arm_right": {"rotation": [(0, still), (0.15, (150, 0, 0)), (0.3, (20, 0, 0)), (0.4, still)]},
	})
	m.write()


def ghast_harness():
	"""Straps, buckles, side crates and a saddle around a vanilla ghast's body (ghast-entity space)."""
	half, bottom, top = 36.5, -7, 65
	m = Model("ghast_harness")
	m.group("harness", (0, 29, 0))
	for side, x in (("left", -14), ("right", 12)):
		m.cube("harness", f"strap_front_{side}", (x, bottom, -half - 1), (x + 2, top, -half), "strap")
		m.cube("harness", f"strap_back_{side}", (x, bottom, half), (x + 2, top, half + 1), "strap")
		m.cube("harness", f"strap_top_{side}", (x, top, -half - 1), (x + 2, top + 1, half + 1), "strap")
		m.cube("harness", f"strap_under_{side}", (x, bottom - 1, -half - 1), (x + 2, bottom, half + 1), "strap")
		m.cube("harness", f"buckle_{side}", (x - 1, 26, -half - 1.5), (x + 3, 30, -half - 1), "gold")
	for side, x0, x1 in (("east", half, half + 6), ("west", -half - 6, -half)):
		m.cube("harness", f"crate_{side}", (x0, 24, -6), (x1, 34, 6), "wood")
		m.cube("harness", f"crate_charges_{side}", (x0 + 1, 34, -4), (x1 - 1, 37, 4), "charge")
	m.group("saddle", (0, 66, 0))
	m.cube("saddle", "seat", (-10, top + 1, -8), (10, top + 3, 10), "saddle")
	m.cube("saddle", "back", (-10, top + 3, 8), (10, top + 7, 10), "saddle")
	m.cube("saddle", "trim", (-10.5, top + 1, -8.5), (10.5, top + 1.5, 10.5), "gold")

	m.animation("idle", 2.0, "loop", {
		"harness": {"position": [(0, (0, 0, 0)), (1.0, (0, 0.4, 0)), (2.0, (0, 0, 0))]},
		"saddle": {"position": [(0, (0, 0, 0)), (1.0, (0, 0.4, 0)), (2.0, (0, 0, 0))]},
	})
	m.write()


MODELS = [war_drummer, ghast_bombardier, ghast_harness]

if __name__ == "__main__":
	for build in MODELS:
		build()
