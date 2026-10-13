"""Writes nether-rifts' Blockbench models (Generic Model, format 5.0) for BIL to load.
Run from the module: python3 tools/gen_models.py
Each model is groups (bones) of cubes, one square texture atlas, and animations.
The mobs are built on vanilla geometry (tools/vanilla/, Blockbench's Java entity templates) wearing the vanilla
textures, read from the Minecraft jar in the Gradle cache. Props are painted from vanilla block textures.
Models face north: -Z is the front (BIL turns them around). 16 units = 1 block, feet at y = 0.
+X is the entity's right (checked in game), so the templates are mirrored in x on import.
Re-running gives byte-identical files; edit this script, not the .bbmodel files.
They open in Blockbench (File > Open) for a look; to keep a hand edit, port it back here."""
import base64
import json
import math
import random
import struct
import uuid
import zipfile
import zlib
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
OUT = TOOLS.parent / "src/main/resources/model/nether_rifts"
VANILLA = TOOLS / "vanilla"
JARS = Path.home() / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf"
NS = uuid.UUID("2f0c0a5e-8d7b-4c1e-9b65-6e657468727a")

# Flat colours, for a material with no block texture.
PALETTE = {"gold": (244, 197, 66)}
SHADE = {"up": 1.08, "down": 0.72, "north": 1.0, "south": 0.9, "east": 0.84, "west": 0.84}
# Materials named after a block texture that isn't block/<name>.png.
BLOCK_TEXTURE = {"magma_block": "block/magma.png"}


def uid(*parts):
	return str(uuid.uuid5(NS, "/".join(parts)))


def png(w, h, rows):
	raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)
	def chunk(kind, data):
		return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
	return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
		+ chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def read_png(data):
	"""Decodes a non-interlaced PNG of up to 8 bits a sample (grey, RGB, palette, grey+alpha, RGBA; all five
	filters) into rows of (r, g, b, a). Minecraft's textures are mostly 1-8 bit palette images."""
	assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
	pos, idat, palette, trns = 8, b"", [], b""
	while pos < len(data):
		length, kind = struct.unpack(">I4s", data[pos:pos + 8])
		body = data[pos + 8:pos + 8 + length]
		if kind == b"IHDR":
			w, h, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", body)
			assert depth <= 8 and interlace == 0, "only non-interlaced PNGs of up to 8 bits a sample"
		elif kind == b"PLTE":
			palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
		elif kind == b"tRNS":
			trns = body
		elif kind == b"IDAT":
			idat += body
		pos += 12 + length
	channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
	stride = (w * depth * channels + 7) // 8
	bpp = max(1, depth * channels // 8)
	raw = zlib.decompress(idat)
	rows, prev = [], bytearray(stride)
	for y in range(h):
		f = raw[y * (stride + 1)]
		line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
		for i in range(stride):
			a = line[i - bpp] if i >= bpp else 0
			b = prev[i]
			c = prev[i - bpp] if i >= bpp else 0
			if f == 1:
				line[i] = (line[i] + a) & 255
			elif f == 2:
				line[i] = (line[i] + b) & 255
			elif f == 3:
				line[i] = (line[i] + (a + b) // 2) & 255
			elif f == 4:
				p = a + b - c
				pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
				line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
		prev = line
		if depth < 8:
			per = 8 // depth
			samples = [(line[x // per] >> (8 - depth * (x % per + 1))) & ((1 << depth) - 1) for x in range(w)]
		else:
			samples = line
		row = []
		for x in range(w):
			if colour == 3:
				i = samples[x]
				row.append(palette[i] + (trns[i] if i < len(trns) else 255,))
			elif colour == 0:
				v = samples[x] * 255 // ((1 << depth) - 1)
				row.append((v, v, v, 255))
			elif colour == 4:
				row.append((samples[2 * x],) * 3 + (samples[2 * x + 1],))
			elif colour == 2:
				row.append(tuple(samples[3 * x:3 * x + 3]) + (255,))
			else:
				row.append(tuple(samples[4 * x:4 * x + 4]))
		rows.append(row)
	return w, h, rows


def minecraft_jar():
	jars = sorted(JARS.glob("*/minecraft-merged-deobf-*.jar"))
	if not jars:
		raise SystemExit(f"no Minecraft jar under {JARS}: build the project once so Loom fetches it")
	return next((j for j in jars if j.parent.name == "26.3"), jars[-1])


_TEXTURES = {}


def texture_from_jar(path):
	"""A texture under assets/minecraft/textures/ (e.g. "entity/piglin/piglin.png"): (width, height, rows)."""
	if path not in _TEXTURES:
		with zipfile.ZipFile(minecraft_jar()) as jar:
			_TEXTURES[path] = read_png(jar.read("assets/minecraft/textures/" + path))
	return _TEXTURES[path]


def stick_tile():
	"""The stick item's 3-pixel-wide diagonal, straightened and stretched over a 16x16 tile: a stick's wood, for a cube."""
	_, _, rows = texture_from_jar("item/stick.png")
	strip = [[px for px in row if px[3]] for row in rows]
	strip = [row for row in strip if len(row) == 3]
	return [[strip[y * len(strip) // 16][x * 3 // 16] for x in range(16)] for y in range(16)]


def block_tile(material):
	"""The material's block texture: its first 16x16 frame, made opaque (stained glass reads as solid lenses)."""
	if material == "stick":
		return stick_tile()
	path = BLOCK_TEXTURE.get(material, f"block/{material}.png")
	w, h, rows = texture_from_jar(path)
	return [[(r, g, b, 255) for r, g, b, _ in row[:w]] for row in rows[:w]]


def has_texture(material):
	return material not in PALETTE


class Model:
	def __init__(self, name, size=128):
		self.name, self.size = name, size
		self.groups, self.elements, self.animations = [], [], []
		self.children, self.roots = {}, []
		self.atlas = [[(0, 0, 0, 0)] * size for _ in range(size)]
		self.reserved = []
		self.used = []
		self.painted = {}
		self.shift = (0, 0, 0)
		self._x = self._y = self._row = 0

	def _at(self, p):
		return [p[i] + self.shift[i] for i in range(3)]

	def group(self, name, origin, parent=None, rotation=(0, 0, 0)):
		self.groups.append({"name": name, "uuid": uid(self.name, "g", name), "export": True,
			"origin": self._at(origin), "rotation": list(rotation)})
		self.children[name] = []
		(self.children[parent].append(("g", name)) if parent else self.roots.append(name))
		return name

	def element(self, group, name, frm, to, faces, inflate=0):
		# two templates merged into one group can both have a cube of this name: keep their ids apart
		taken = {ref for kind, ref in self.children[group] if kind == "c"}
		base, n = name, 1
		while uid(self.name, "c", group, name) in taken:
			n += 1
			name = f"{base}_{n}"
		cid = uid(self.name, "c", group, name)
		element = {"name": name, "export": True, "from": self._at(frm), "to": self._at(to), "autouv": 0,
			"origin": self._at(frm), "faces": faces, "type": "cube", "uuid": cid}
		if inflate:
			element["inflate"] = inflate
		self.elements.append(element)
		self.children[group].append(("c", cid))

	def sheet(self, rows, origin):
		"""Copies a texture into the atlas at origin and keeps prop faces off it (sheet before painting props)."""
		ox, oy = origin
		box = (ox, oy, ox + len(rows[0]), oy + len(rows))
		hit = next((r for r in self.used if r[0] < box[2] and box[0] < r[2] and r[1] < box[3] and box[1] < r[3]), None)
		assert hit is None, f"{self.name}: a sheet at {origin} would paint over the prop face at {hit}"
		for y, row in enumerate(rows):
			self.atlas[oy + y][ox:ox + len(row)] = row
		self.reserved.append((ox, oy, ox + len(rows[0]), oy + len(rows)))

	def cube(self, group, name, frm, to, material, decorate=None, inflate=0, only=None):
		"""A prop cube; each face is painted from the material's block texture (or flat colour). material is one
		name for every face or {face: name}, with "side" standing for the four sides; only keeps just those faces."""
		dx, dy, dz = (to[i] - frm[i] for i in range(3))
		sizes = {"north": (dx, dy), "east": (dz, dy), "south": (dx, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
		def of(face):
			if isinstance(material, str):
				return material
			return material.get(face, material.get("side"))
		faces = {face: {"uv": list(self._face(of(face), face, w, h, decorate)), "texture": 0} for face, (w, h) in sizes.items()
			if only is None or face in only}
		self.element(group, name, frm, to, faces, inflate)

	def plane(self, group, name, frm, to, faces):
		"""A flat element (zero thickness on one axis) showing only the given faces, {face: atlas rect}."""
		self.element(group, name, frm, to, {face: {"uv": list(rect), "texture": 0} for face, rect in faces.items()})

	def _face(self, material, face, w, h, decorate):
		"""A face's texture rect: one texel a unit (16 at most a side, 1 at least), shared between faces that look alike."""
		tw, th = max(1, min(16, round(w))), max(1, min(16, round(h)))
		key = (material, tw, th) if decorate is None and has_texture(material) else None
		if key in self.painted:
			return self.painted[key]
		rect = self._alloc(tw, th)
		self._paint(rect, material, face)
		if decorate:
			decorate(face, self.atlas, rect)
		if key:
			self.painted[key] = rect
		return rect

	def _alloc(self, tw, th):
		while True:
			if self._x + tw > self.size:
				self._x, self._y, self._row = 0, self._y + self._row, 0
			if self._y + th > self.size:
				raise ValueError(f"{self.name}: the {self.size}px texture is full")
			rect = (self._x, self._y, self._x + tw, self._y + th)
			hit = next((r for r in self.reserved if rect[0] < r[2] and r[0] < rect[2] and rect[1] < r[3] and r[1] < rect[3]), None)
			if hit is None:
				break
			# step past it, and let the next row start below it
			self._x = hit[2]
			self._row = max(self._row, hit[3] - self._y)
		self._x += tw
		self._row = max(self._row, th)
		self.used.append(rect)
		return rect

	def _paint(self, rect, material, face):
		x0, y0, x1, y1 = rect
		tw, th = x1 - x0, y1 - y0
		if has_texture(material):
			# The 16x16 tile, sampled nearest-neighbour across the rect.
			tile = block_tile(material)
			for j in range(th):
				for i in range(tw):
					self.atlas[y0 + j][x0 + i] = tile[j * 16 // th][i * 16 // tw]
			return
		rnd = random.Random(f"{self.name}/{rect}")
		base = PALETTE[material]
		for y in range(y0, y1):
			for x in range(x0, x1):
				noise = 1 + (rnd.random() - 0.5) * 0.12
				border = 0.82 if (tw > 2 and th > 2) and (x in (x0, x1 - 1) or y in (y0, y1 - 1)) else 1.0
				self.atlas[y][x] = tuple(max(0, min(255, int(c * SHADE[face] * noise * border))) for c in base) + (255,)

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

	def write(self):
		data = {"meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
			"name": self.name, "model_identifier": self.name,
			"resolution": {"width": self.size, "height": self.size},
			"elements": self.elements, "groups": self.groups,
			"outliner": [self._outliner(n) for n in self.roots],
			"textures": [{"name": f"{self.name}.png", "id": "0", "width": self.size, "height": self.size,
				"uv_width": self.size, "uv_height": self.size, "particle": False, "frame_time": 1,
				"uuid": uid(self.name, "t"),
				"source": "data:image/png;base64," + base64.b64encode(png(self.size, self.size, self.atlas)).decode()}],
			"animations": self.animations}
		OUT.mkdir(parents=True, exist_ok=True)
		path = OUT / f"{self.name}.bbmodel"
		path.write_text(json.dumps(data, indent="\t") + "\n")
		print("wrote", path)


def box_uv(u, v, w, h, d):
	"""Minecraft's box-UV layout, per face, for a cube of size (w, h, d) at texture offset (u, v), as Blockbench lays
	it out (east is the strip at u, up and down are flipped). In the mirrored model the template's west face is our
	east, which is where vanilla's ModelPart puts the same strip, so the faces read as they do in game."""
	return {
		"north": (u + d, v + d, u + d + w, v + d + h),
		"east": (u, v + d, u + d, v + d + h),
		"south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
		"west": (u + d + w, v + d, u + 2 * d + w, v + d + h),
		"up": (u + d + w, v + d, u + d, v),
		"down": (u + d + 2 * w, v, u + d + w, v + d),
	}


def import_java_entity(model, template_json, texture_origin, scale=1.0, rename={}, skip=set(), texture=None,
		parents={}, rotations={}, inflate=None):
	"""Adds a Blockbench Java-entity template's bones as groups and its cubes as elements, box-UV mapped onto the
	template's vanilla texture (or `texture`), which is copied into the atlas at texture_origin.
	- The template is mirrored in x (x -> -x, rotation (-x, -y, z), as Blockbench imports it) so the right arm lands
	  on +X, the entity's right in game.
	- rename maps template bone names to ours; two bones renamed alike merge into one group (the first one's pivot).
	- parents overrides a bone's parent and rotations its rest rotation (both by our name).
	- scale multiplies every coordinate; inflate maps a cube's inflate (default: scaled with the rest).
	- Model.shift moves everything afterwards."""
	template = json.loads((VANILLA / template_json).read_text())
	width, _, rows = texture_from_jar(texture or template["external_textures"][0])
	k = width / template["texturewidth"]
	model.sheet(rows, texture_origin)
	ox, oy = texture_origin
	for bone in template["bones"]:
		if bone["name"] in skip:
			continue
		name = rename.get(bone["name"], bone["name"])
		if name not in model.children:
			parent = parents.get(name, rename.get(bone.get("parent"), bone.get("parent")))
			px, py, pz = bone["pivot"]
			rx, ry, rz = bone.get("rotation", (0, 0, 0))
			model.group(name, (-px * scale, py * scale, pz * scale), parent=parent,
				rotation=rotations.get(name, (-rx, -ry, rz)))
		for i, c in enumerate(bone.get("cubes", [])):
			(x, y, z), (w, h, d), (u, v) = c["origin"], c["size"], c["uv"]
			faces = {face: {"uv": [ox + k * r[0], oy + k * r[1], ox + k * r[2], oy + k * r[3]], "texture": 0}
				for face, r in box_uv(u, v, w, h, d).items()}
			grow = c.get("inflate", 0)
			grow = inflate(grow) if inflate else grow * scale
			model.element(name, f"{bone['name'].lower()}_{i}", (-(x + w) * scale, y * scale, z * scale),
				(-x * scale, (y + h) * scale, (z + d) * scale), faces, round(grow, 4))




def mirrored(frm, to):
	"""The same cube on the other side of x = 0."""
	return (-to[0], frm[1], frm[2]), (-frm[0], to[1], to[2])


def wave(length, amplitude, phase=0.0, steps=8):
	"""Keyframes of a sine over one loop: amplitude (x, y, z) scaled by sin, phase in loops."""
	points = []
	for i in range(steps + 1):
		t = length * i / steps
		k = math.sin(2 * math.pi * (i / steps + phase))
		points.append((round(t, 4), tuple(round(a * k, 3) for a in amplitude)))
	return points


def tinted(dst, src, tint):
	"""src's white mask, tinted by a dye colour, laid over dst (as the game draws a banner layer)."""
	a = src[3] / 255
	return tuple(round(src[i] * tint[i] / 255 * a + dst[i] * (1 - a)) for i in range(3)) + (255,)


# Dye colours as the game tints banner layers with them.
BLACK_DYE = (0x1D, 0x1D, 0x21)
YELLOW_DYE = (0xFE, 0xD8, 0x3D)


def piglin_banner():
	"""The 20x40 front of a black banner with vanilla's piglin snout pattern in gold (yellow dye): the banner's cloth,
	then the base layer and the pattern, each a white mask tinted by its dye."""
	_, _, cloth = texture_from_jar("entity/banner/banner_base.png")
	_, _, base = texture_from_jar("entity/banner/base.png")
	_, _, snout = texture_from_jar("entity/banner/piglin.png")
	return [[tinted(tinted(cloth[y][x], base[y][x], BLACK_DYE), snout[y][x], YELLOW_DYE) for x in range(1, 21)]
		for y in range(1, 41)]


def bombardier_kit(m, head, body, arm):
	"""Goggles, a satchel of fire charges on the back of the left hip, a charge in the right hand (piglin space).
	The Bombardier on foot is a vanilla piglin wearing the bombardier_helmet item (goggles; its model
	is assets/nether_rifts/models/item/bombardier_helmet.json, made to match these goggles)."""
	m.cube(head, "goggle_band", (-5.25, 28, -4.25), (5.25, 30, 4.25), "brown_wool")
	m.cube(head, "lens_right", (1, 28, -4.75), (4, 30, -4.25), "light_blue_stained_glass")
	m.cube(head, "lens_left", (-4, 28, -4.75), (-1, 30, -4.25), "light_blue_stained_glass")
	m.cube(body, "satchel", (-7, 13, 2.25), (-3, 18, 5.25), "brown_wool")
	m.cube(body, "satchel_charge", (-6, 18, 3), (-4, 20, 5), "magma_block")
	m.cube(arm, "charge", (4.5, 9, -1.5), (7.5, 12, 1.5), "magma_block")


# The ghast's 16-unit body at 4x fills its 4-block box (vanilla draws it at 4.5x, overhanging the box).
GHAST_SCALE = 4
# The deck hangs under the longest tentacle (13 px, down to 48 units under the body). Its underside, 56 units
# (3.5 blocks) under the ghast's feet, is WarGhast.DECK_DROP.
DECK_TOP, DECK_BOTTOM = -52, -56
# The crew stand in a row at the deck's front, in the gaps between the front tentacles (x -29..-21, -9..-1, 11..19),
# just ahead of them (z -24): crossbowmen on the left and right, the Bombardier in the middle. WarGhast.CREW matches.
CREW_Z = -28
CREW = {"crossbow_left": -15, "bomber": 5, "crossbow_right": 25.5}
# The crossbowmen hold theirs aimed forward and down, at whatever is under the ghast.
CROSSBOW_HOLD = (60, 0, 0)
TNT = {"side": "tnt_side", "up": "tnt_top", "down": "tnt_bottom"}
CHAIN_SHEET = (96, 64)


def chain(m, group, name, x, z, top, bottom, size=2):
	"""A vanilla chain hanging from top to bottom at size times vanilla's: two crossed planes of the chain texture
	(as the chain block draws it), one 16-pixel tile every 16 * size units."""
	cx, cy = CHAIN_SHEET
	half, tile = 1.5 * size, 16 * size
	y, i = top, 0
	while y > bottom:
		low = max(bottom, y - tile)
		v = 16 * (y - low) / tile
		m.plane(group, f"{name}_{i}_a", (x - half, low, z), (x + half, y, z),
			{"north": (cx, cy, cx + 3, cy + v), "south": (cx, cy, cx + 3, cy + v)})
		m.plane(group, f"{name}_{i}_b", (x, low, z - half), (x, y, z + half),
			{"east": (cx + 3, cy, cx + 6, cy + v), "west": (cx + 3, cy, cx + 6, cy + v)})
		y, i = low, i + 1


def war_ghast():
	"""A vanilla ghast in the happy ghast's red harness, a wooden deck slung under it on chains with a crew of three
	piglins (the Bombardier and two crossbowmen), and explosives: TNT on the deck, TNT and a magma bomb dangling
	from the harness. All one model, so nothing drifts apart as it turns."""
	m = Model("war_ghast", size=256)
	# every sheet goes in before any prop face is painted, so none lands on a prop's
	m.sheet(texture_from_jar("entity/piglin/piglin.png")[2], (0, 64))
	import_java_entity(m, "ghast.json", (0, 0), scale=GHAST_SCALE,
		rename={f"tentacles_{i}": f"tentacle_{i}" for i in range(9)})
	# The harness's box sits half a unit up in its template (y 0.5..16.5): bring it down onto the body. Vanilla draws
	# it flush with the body; it keeps half a unit off the skin here (no z-fighting, still hugging). The goggles would
	# hide the face, so they're left off.
	m.shift = (0, -0.5 * GHAST_SCALE, 0)
	import_java_entity(m, "happy_ghast_harness.json", (128, 0), scale=GHAST_SCALE, skip={"goggles"},
		texture="entity/equipment/happy_ghast_body/red_harness.png",
		inflate=lambda grow: round((grow - 0.5) * GHAST_SCALE + 0.5, 4))
	m.shift = (0, 0, 0)
	# The shooting face (red eyes, open mouth): a plane just inside the body's front, which the shoot animation brings
	# out in front of it.
	_, _, shooting = texture_from_jar("entity/ghast/ghast_shooting.png")
	m.sheet([row[32:64] for row in shooting[32:64]], (64, 64))
	m.group("face", (0, 32, -31.5), parent="body")
	m.plane("face", "shooting_face", (-32, 0, -31.5), (32, 64, -31.5), {"north": (64, 64, 96, 96)})
	_, _, links = texture_from_jar("block/iron_chain.png")
	m.sheet(links, CHAIN_SHEET)

	# The deck: crimson planks in 16-unit boards with a low rim, hung from four chains that run up the body's sides
	# under the harness; a TNT cargo on its back corners, clear of the tentacles.
	m.group("deck", (0, 0, 0), parent="body")
	for i in range(4):
		for j in range(4):
			x, z = -32 + 16 * i, -32 + 16 * j
			m.cube("deck", f"board_{i}_{j}", (x, DECK_BOTTOM, z), (x + 16, DECK_TOP, z + 16), "crimson_planks")
	rim = DECK_TOP + 3
	m.cube("deck", "rim_front", (-32, DECK_TOP, -32), (32, rim, -30.5), "stripped_crimson_stem")
	m.cube("deck", "rim_back", (-32, DECK_TOP, 30.5), (32, rim, 32), "stripped_crimson_stem")
	m.cube("deck", "rim_right", (30.5, DECK_TOP, -30.5), (32, rim, 30.5), "stripped_crimson_stem")
	m.cube("deck", "rim_left", (-32, DECK_TOP, -30.5), (-30.5, rim, 30.5), "stripped_crimson_stem")
	for sx in (1, -1):
		for sz in (1, -1):
			tag = f"{'right' if sx > 0 else 'left'}_{'back' if sz > 0 else 'front'}"
			chain(m, "deck", f"chain_{tag}", 33 * sx, 20 * sz, 22, rim)
			m.cube("deck", f"bracket_{tag}", (33 * sx - 2.5, DECK_TOP, 20 * sz - 2.5), (33 * sx + 2.5, rim + 1, 20 * sz + 2.5), "gold_block")
	m.cube("deck", "tnt_left", (-19, DECK_TOP, 22.5), (-11, DECK_TOP + 8, 30.5), TNT)
	m.cube("deck", "tnt_right", (21, DECK_TOP, 22.5), (29, DECK_TOP + 8, 30.5), TNT)

	# Explosives on chains from the harness, one each side between the deck's chains, swinging on their own.
	for side, sx, material in (("left", -1, TNT), ("right", 1, "magma_block")):
		m.group(f"bomb_{side}", (34 * sx, 22, 0), parent="body")
		chain(m, f"bomb_{side}", "chain", 34 * sx, 0, 22, -6)
		m.cube(f"bomb_{side}", "bomb", (34 * sx - 4, -14, -4), (34 * sx + 4, -6, 4), material)

	# The crew: vanilla piglins standing on the deck, a few bones each (body, arms that move, the ears' tilt).
	crew_bones = []
	for name, x in CREW.items():
		m.shift = (x, DECK_TOP, CREW_Z)
		bomber = name == "bomber"
		arm = f"{name}_arm" if bomber else f"{name}_arms"
		import_java_entity(m, "piglin.json", (0, 64), skip={"leftItem"},
			rename={"Body": name, "head": name, "RightLeg": name, "LeftLeg": name, "RightArm": arm,
				"LeftArm": name if bomber else arm, "leftear": f"{name}_ear_left", "rightear": f"{name}_ear_right"},
			parents={name: "deck", arm: name}, rotations={} if bomber else {arm: CROSSBOW_HOLD})
		if bomber:
			bombardier_kit(m, name, name, arm)
		else:
			# a crossbow across both fists: the stock runs on ahead of the hands (down the arms), the bow at its end
			m.cube(arm, "crossbow_grip", (-6, 11, -1), (6, 12.5, 1), "spruce_planks")
			m.cube(arm, "crossbow_stock", (-1, 4, -1), (1, 12.5, 1), "spruce_planks")
			m.cube(arm, "crossbow_bow", (-6, 4, -1.5), (6, 5.5, 0), "dark_oak_log")
			m.cube(arm, "crossbow_bolt", (-0.5, 2.5, -0.5), (0.5, 4, 0.5), "iron_block")
		crew_bones += [name, arm, f"{name}_ear_left", f"{name}_ear_right"]
	m.shift = (0, 0, 0)

	still = (0, 0, 0)
	idle = {
		"body": {"position": [(0, still), (1.5, (0, 1.5, 0)), (3.0, still)]},
		"deck": {"rotation": [(0, (1, 0, -1.5)), (1.5, (-1, 0, 1.5)), (3.0, (1, 0, -1.5))]},
		"bomb_left": {"rotation": wave(3.0, (6, 0, 2))},
		"bomb_right": {"rotation": wave(3.0, (6, 0, -2), 0.5)},
		"bomber": {"position": [(0, still), (0.75, (0, 0.3, 0)), (1.5, still), (2.25, (0, 0.3, 0)), (3.0, still)]},
		"crossbow_left_arms": {"rotation": wave(3.0, (3, 6, 0), 0.25)},
		"crossbow_right_arms": {"rotation": wave(3.0, (3, -6, 0), 0.6)},
	}
	# the tentacles sway out of step with each other
	for i in range(9):
		idle[f"tentacle_{i}"] = {"rotation": [(t, (x, 0, z)) for (t, (x, _, _)), (_, (_, _, z))
			in zip(wave(3.0, (9, 0, 0), i * 0.37), wave(3.0, (0, 0, 6), i * 0.21 + 0.25))]}
	m.animation("idle", 3.0, "loop", idle)
	# Positive position z moves a bone back, so the face comes out with negative z.
	out = (0, 0, -0.9)
	m.animation("shoot", 1.0, "once", {"face": {"position": [(0, out), (0.95, out), (1.0, still)]}})
	m.animation("drop_bomb", 0.4, "once", {
		"bomber_arm": {"rotation": [(0, still), (0.15, (150, 0, 0)), (0.3, (20, 0, 0)), (0.4, still)]},
	})
	# Once the ghast dies its crew jump off as real mobs: this shrinks their figures away for the death animation.
	gone = (0.01, 0.01, 0.01)
	m.animation("crew_gone", 0.1, "loop", {bone: {"scale": [(0, gone), (0.1, gone)]} for bone in crew_bones})
	m.write()


# war_drummer.bbmodel is hand-made (the artist's model; on import its 104x64 texture was padded to 128x64 and the beat's
# leg tracks dropped so the walk can play under it; its flat bones regrouped: ears in the head, the head in the body (the body's offsets
# taken off its position keys, which copied them; it rides the body's lean), arms and jacket in
# the body with the body's motion taken off their keyframes, sleeves and pants in their limbs): not generated here.
MODELS = [war_ghast]

if __name__ == "__main__":
	for build in MODELS:
		build()
