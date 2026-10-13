"""Imports the artist's War Drummer (a Blockbench modded-entity .bbmodel) as src/main/resources/model/nether_rifts/
war_drummer.bbmodel, fitted to how BIL plays it. Run from the module: python3 tools/import_drummer.py <artist file>

- The texture is padded to 128 x 64 (the spare columns stay empty), its uv size set to match: BIL scales UVs by a
  texture's uv size, which Blockbench's modded-entity format ignores.
- The beat's leg tracks (a still stance) are swapped for the walk's, so it marches as it drums: BIL gives a
  .bbmodel's animations every bone, so the walk can't play under it.
- The flat bones are nested so they move together: ears in the head, the head, arms and jacket in the body,
  sleeves and pants in their limbs. A bone authored beside its new parent had the parent's motion copied into its
  own keys; that comes off so nothing moves twice. For the head only its position comes off (its rotation stays
  relative, so it rides the body's lean); sleeves and pants just follow.
- Empty marker groups for held and head items (item_right, item_left, item_head) are added where missing.
- Armour pieces are added where missing: vanilla's piglin armour cubes, each in a group inside the drummer bone it
  covers, textured iron in the file (a second texture). For every vanilla armour material the import also writes item
  models of those cubes, as BIL builds a bone's model, onto vanilla's own armour textures (assets/nether_rifts/
  {models/item,items}/war_drummer/armour/), and an atlas file that puts those textures where item models can reach them.
  mob/ArmourPieces.java shows them. The textures and the materials come from the Minecraft jar (MC_JAR, else loom's).
Keyframes must be linear (the artist's are), so taking a parent's track off a child is exact at every frame."""
import base64
import glob
import io
import json
import os
import shutil
import sys
import uuid
import zipfile
from pathlib import Path

from PIL import Image

MODULE = Path(__file__).resolve().parent.parent
OUT = MODULE / "src/main/resources/model/nether_rifts/war_drummer.bbmodel"
ASSETS = MODULE / "src/main/resources/assets"
WIDTH, HEIGHT = 128, 64
MC_VERSION = "26.3"

# (child, parent, what comes off the child's keys)
RIG = [("left_ear", "head", ()), ("right_ear", "head", ()),
       ("head", "body", ("position",)),
       ("jacket", "body", ("rotation", "position")),
       ("left_arm", "body", ("rotation", "position")), ("right_arm", "body", ("rotation", "position")),
       ("left_sleeve", "left_arm", None), ("right_sleeve", "right_arm", None),
       ("left_pants", "left_leg", None), ("right_pants", "right_leg", None)]
BEAT_FREE = {"left_leg", "right_leg", "left_pants", "right_pants"}


def pad_texture(d):
	tex = d["textures"][0]
	img = Image.open(io.BytesIO(base64.b64decode(tex["source"].split(",", 1)[1]))).convert("RGBA")
	assert img.width <= WIDTH and img.height <= HEIGHT, img.size
	wide = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
	wide.paste(img, (0, 0))
	buf = io.BytesIO()
	wide.save(buf, "PNG", optimize=True)
	tex["source"] = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
	tex["width"] = tex["uv_width"] = WIDTH
	tex["height"] = tex["uv_height"] = HEIGHT
	d["resolution"] = {"width": WIDTH, "height": HEIGHT}


def find(nodes, u):
	for i, n in enumerate(nodes):
		if isinstance(n, dict):
			if n["uuid"] == u:
				return nodes, i
			found = find(n["children"], u)
			if found:
				return found
	return None


def series(animator, channel):
	return sorted(((k["time"], [float(k["data_points"][0][c]) for c in "xyz"]) for k in (animator or {}).get("keyframes", [])
		if k["channel"] == channel), key=lambda p: p[0])


def at(s, t):
	if not s:
		return [0.0, 0.0, 0.0]
	if t <= s[0][0]:
		return s[0][1]
	if t >= s[-1][0]:
		return s[-1][1]
	for (t0, a), (t1, b) in zip(s, s[1:]):
		if t0 <= t <= t1:
			f = (t - t0) / (t1 - t0) if t1 > t0 else 0
			return [x + (y - x) * f for x, y in zip(a, b)]


def number(v):
	v = round(v, 4)
	return str(int(v)) if v == int(v) else str(v)


def nest(d, child, parent, off):
	uid = {g["name"]: g["uuid"] for g in d["groups"]}
	for a in d["animations"]:
		for k in (k for an in a["animators"].values() for k in an.get("keyframes", [])):
			assert k.get("interpolation", "linear") == "linear", f"{a['name']}: {k.get('interpolation')} keys"
		anims = a["animators"]
		if off is None:
			anims.pop(uid[child], None)
			continue
		c, p = anims.get(uid[child]), anims.get(uid[parent])
		assert not series(p, "scale"), f"{parent} scales in {a['name']}"
		keep = [k for k in (c or {}).get("keyframes", []) if k["channel"] not in off]
		for channel in off:
			cs, ps = series(c, channel), series(p, channel)
			for t in sorted({t for t, _ in cs} | {t for t, _ in ps}):
				v = [x - y for x, y in zip(at(cs, t), at(ps, t))]
				keep.append({"channel": channel, "data_points": [{"x": number(v[0]), "y": number(v[1]), "z": number(v[2])}],
					"uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"{a['name']}/{child}/{channel}/{t}")), "time": t, "color": -1,
					"interpolation": "linear"})
		if keep or c is not None:
			anims.setdefault(uid[child], {"name": child, "type": "bone"})["keyframes"] = keep
	lst, i = find(d["outliner"], uid[child])
	node = lst.pop(i)
	plst, j = find(d["outliner"], uid[parent])
	plst[j]["children"].append(node)


# Where HeldItems draws the mob's gear (empty groups; mob/HeldItems.java): the bottom-centre of each fist, the
# centre of the head. Added where the artist's file has none.
MARKERS = [("item_right", "right_arm", (6, 11, 0)), ("item_left", "left_arm", (-6, 11, 0)), ("item_head", "head", (0, 26, 0))]


def add_markers(d):
	names = {g["name"] for g in d["groups"]}
	template = next(g for g in d["groups"] if g["name"] == "head")
	for name, parent, origin in MARKERS:
		if name in names:
			continue
		group = dict(template, name=name, uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, f"war_drummer/{name}")), origin=list(origin),
			rotation=[0, 0, 0], children=[])
		d["groups"].append(group)
		plst, j = find(d["outliner"], next(g["uuid"] for g in d["groups"] if g["name"] == parent))
		plst[j]["children"].append({"uuid": group["uuid"], "isOpen": False, "children": []})


# Vanilla's piglin armour (AbstractPiglinModel.createArmorMeshSet: HumanoidModel's armour meshes without ears; its
# deformations from LayerDefinitions): helmet, chestplate and boots grown 1.02, the hat 0.5 more, leggings 0.5 and their
# legs 0.1 less. Boxes are vanilla's (x, y, z, w, h, d) about the part's pivot, y down, on a 64 x 32 texture; the left
# limbs are mirrored. (group, drummer bone, layer, [(box, grow, uv, mirror)], anchor)
# The drummer's body and limbs are 11 px to vanilla's 12, and the cubes keep vanilla's size: a piece lines up with its
# bone at the end its texture draws, and overhangs 1 px at the other. "pivot" keeps vanilla's place about the joint (the
# helmet, chestplate, arms and leggings' legs hang from it); "bottom" sits the cube on the bottom of the bone's cubes (the
# boots on the ground, the leggings' waist on the hips: their textures draw only the lower half).
LEG = (-2, 0, -2, 4, 12, 4)
BODY = (-4, 0, -2, 8, 12, 4)
ARMOUR = [
	("armor_head", "head", "humanoid", [((-4, -8, -4, 8, 8, 8), 1.02, (0, 0), False), ((-4, -8, -4, 8, 8, 8), 1.52, (32, 0), False)], "pivot"),
	("armor_chest", "body", "humanoid", [(BODY, 1.02, (16, 16), False)], "pivot"),
	("armor_chest_right", "right_arm", "humanoid", [((-3, -2, -2, 4, 12, 4), 1.02, (40, 16), False)], "pivot"),
	("armor_chest_left", "left_arm", "humanoid", [((-1, -2, -2, 4, 12, 4), 1.02, (40, 16), True)], "pivot"),
	("armor_legs", "body", "humanoid_leggings", [(BODY, 0.5, (16, 16), False)], "bottom"),
	("armor_legs_right", "right_leg", "humanoid_leggings", [(LEG, 0.4, (0, 16), False)], "pivot"),
	("armor_legs_left", "left_leg", "humanoid_leggings", [(LEG, 0.4, (0, 16), True)], "pivot"),
	("armor_feet_right", "right_leg", "humanoid", [(LEG, 0.92, (0, 16), False)], "bottom"),
	("armor_feet_left", "left_leg", "humanoid", [(LEG, 0.92, (0, 16), True)], "bottom"),
]
ARMOUR_W, ARMOUR_H = 64, 32
# a second layer (leather's overlay) is drawn this much further out than the first, so the two never fight
LAYER_GAP = 0.05
MODELS = ASSETS / "nether_rifts/models/item/war_drummer/armour"
ITEMS = ASSETS / "nether_rifts/items/war_drummer/armour"


def mc_jar():
	jar = os.environ.get("MC_JAR") or next(iter(sorted(glob.glob(os.path.expanduser(
		f"~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/{MC_VERSION}/*.jar")))), None)
	assert jar, "no Minecraft jar: set MC_JAR"
	return zipfile.ZipFile(jar)


def box_faces(uv, size, mirror, scale=(1, 1)):
	"""Blockbench's box UV (js/outliner/cube.js updateUV), so the file matches what Blockbench would make of it."""
	w, h, d = size
	faces = [("east", [0, d], [d, h]), ("west", [d + w, d], [d, h]), ("up", [d + w, d], [-w, -d]),
		("down", [d + 2 * w, 0], [-w, d]), ("south", [2 * d + w, d], [w, h]), ("north", [d, d], [w, h])]
	if mirror:
		faces = [(n, [f[0] + s[0], f[1]], [-s[0], s[1]]) for n, f, s in faces]
		(e, ef, es), (wn, wf, ws) = faces[0], faces[1]
		faces[0], faces[1] = (e, wf, ws), (wn, ef, es)
	return {n: [(f[0] + uv[0]) * scale[0], (f[1] + uv[1]) * scale[1], (f[0] + s[0] + uv[0]) * scale[0], (f[1] + s[1] + uv[1]) * scale[1]]
		for n, f, s in faces}


def armour_cubes(d):
	"""Each piece's cubes in the file's space (x mirrored and y up from vanilla's), with its group's origin."""
	groups = {g["name"]: g for g in d["groups"]}
	elements = {e["uuid"]: e for e in d["elements"]}
	pieces = []
	for name, bone, layer, cubes, anchor in ARMOUR:
		pivot = groups[bone]["origin"]
		node = find(d["outliner"], groups[bone]["uuid"])
		lst, i = node
		bottom = min(elements[c]["from"][1] for c in lst[i]["children"] if isinstance(c, str))
		out = []
		for (x, y, z, w, h, dd), grow, uv, mirror in cubes:
			lo = [pivot[0] - (x + w), pivot[1] - (y + h), pivot[2] + z]
			if anchor == "bottom":
				lo[1] = bottom
			out.append((lo, [lo[0] + w, lo[1] + h, lo[2] + dd], grow, uv, mirror, (w, h, dd)))
		pieces.append((name, bone, layer, list(pivot), out))
	return pieces


def add_armour(d, jar):
	if any(g["name"] == ARMOUR[0][0] for g in d["groups"]):
		return
	# the file shows iron: vanilla's humanoid layer, and its leggings layer beside it
	tex = Image.new("RGBA", (2 * ARMOUR_W, ARMOUR_H), (0, 0, 0, 0))
	for i, layer in enumerate(("humanoid", "humanoid_leggings")):
		tex.paste(Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/entity/equipment/{layer}/iron.png"))).convert("RGBA"), (i * ARMOUR_W, 0))
	buf = io.BytesIO()
	tex.save(buf, "PNG", optimize=True)
	first = d["textures"][0]
	index = len(d["textures"])
	d["textures"].append(dict(first, name="armour_iron.png", relative_path="armour_iron.png", id=str(index),
		width=2 * ARMOUR_W, height=ARMOUR_H, uv_width=2 * ARMOUR_W, uv_height=ARMOUR_H, use_as_default=False,
		uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, "war_drummer/armour_iron")), source="data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()))
	d["textures"][index].pop("sync_to_project", None)
	template = next(g for g in d["groups"] if g["name"] == "head")
	for name, bone, layer, pivot, cubes in armour_cubes(d):
		children = []
		for n, (lo, hi, grow, uv, mirror, size) in enumerate(cubes):
			offset = [uv[0] + (ARMOUR_W if layer == "humanoid_leggings" else 0), uv[1]]
			cube = {"name": name, "box_uv": True, "render_order": "default", "locked": False, "export": True, "scope": 0,
				"allow_mirror_modeling": True, "from": lo, "to": hi, "autouv": 0, "color": 0, "inflate": grow, "origin": pivot,
				"uv_offset": offset, "faces": {f: {"uv": uv4, "texture": index} for f, uv4 in box_faces(offset, size, mirror).items()},
				"type": "cube", "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"war_drummer/{name}/{n}"))}
			if mirror:
				cube["mirror_uv"] = True
			d["elements"].append(cube)
			children.append(cube["uuid"])
		group = dict(template, name=name, uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, f"war_drummer/{name}")), origin=pivot,
			rotation=[0, 0, 0], children=[])
		d["groups"].append(group)
		plst, j = find(d["outliner"], next(g["uuid"] for g in d["groups"] if g["name"] == bone))
		plst[j]["children"].append({"uuid": group["uuid"], "isOpen": False, "children": children})


def number_list(v):
	return [round(x, 4) for x in v]


def write_armour_assets(d, jar):
	"""Per vanilla material, an item model of each piece as BIL builds a bone's (its cubes about the group's origin, moved
	by 8; BIL scales a bone only past 24 px), on vanilla's textures for that material's layers: layer i is texture #i
	and tint i (tint 0 the dye, the others custom model data colours). ArmourPieces colours them."""
	shutil.rmtree(MODELS, ignore_errors=True)
	shutil.rmtree(ITEMS, ignore_errors=True)
	pieces = armour_cubes(d)
	materials = 0
	for entry in sorted(n for n in jar.namelist() if n.startswith("assets/minecraft/equipment/") and n.endswith(".json")):
		material = Path(entry).stem
		layers = json.loads(jar.read(entry))["layers"]
		for name, bone, layer, pivot, cubes in pieces:
			if layer not in layers:
				continue
			# BIL shrinks a bone whose cubes (with its origin) span more than 24 px, and these models would no longer match
			lo_all = [min(0, *(c[0][a] - c[2] - pivot[a] for c in cubes)) for a in range(3)]
			hi_all = [max(0, *(c[1][a] + c[2] + (len(layers[layer]) - 1) * LAYER_GAP - pivot[a] for c in cubes)) for a in range(3)]
			assert max(h - l for l, h in zip(lo_all, hi_all)) <= 24, name
			textures, elements = {}, []
			for i, info in enumerate(layers[layer]):
				ns, path = info["texture"].split(":")
				textures[str(i)] = f"{ns}:entity/equipment/{layer}/{path}"
				for lo, hi, grow, uv, mirror, size in cubes:
					g = grow + i * LAYER_GAP
					faces = box_faces(uv, size, mirror, (16 / ARMOUR_W, 16 / ARMOUR_H))
					elements.append({"from": number_list([x - g - p + 8 for x, p in zip(lo, pivot)]),
						"to": number_list([x + g - p + 8 for x, p in zip(hi, pivot)]),
						"faces": {f: {"texture": f"#{i}", "tintindex": i, "uv": number_list(uv4)} for f, uv4 in faces.items()}})
			textures["particle"] = textures["0"]
			model = {"textures": textures, "elements": elements, "display": {"head": {"rotation": [0, 180, 0]}}}
			tints = [{"type": "minecraft:dye", "default": -1}] + [{"type": "minecraft:custom_model_data", "index": i - 1, "default": -1}
				for i in range(1, len(layers[layer]))]
			model_path = MODELS / material / f"{name}.json"
			model_path.parent.mkdir(parents=True, exist_ok=True)
			model_path.write_text(json.dumps(model, indent="\t") + "\n")
			item_path = ITEMS / material / f"{name}.json"
			item_path.parent.mkdir(parents=True, exist_ok=True)
			item_path.write_text(json.dumps({"model": {"type": "minecraft:model",
				"model": f"nether_rifts:item/war_drummer/armour/{material}/{name}", "tints": tints}}, indent="\t") + "\n")
		materials += any(p[2] in layers for p in pieces)
	# item models read the item and block atlases only: vanilla's armour textures join the item atlas (the client adds
	# every pack's atlas sources together, and Polymer merges ours with other mods')
	atlas = ASSETS / "minecraft/atlases/items.json"
	atlas.parent.mkdir(parents=True, exist_ok=True)
	atlas.write_text(json.dumps({"sources": [{"type": "minecraft:directory", "source": f"entity/equipment/{layer}",
		"prefix": f"entity/equipment/{layer}/"} for layer in ("humanoid", "humanoid_leggings")]}, indent="\t") + "\n")
	print("armour models for", materials, "materials")


def main(src):
	d = json.loads(Path(src).read_text())
	d["name"] = "war_drummer"
	pad_texture(d)
	# BIL gives a .bbmodel's animations every bone (the top one playing poses the whole model), so the walk can't
	# play under the beat: the beat takes the walk's legs instead, both being 1 s loops
	beat = next(a for a in d["animations"] if a["name"] == "beat")
	walk = next(a for a in d["animations"] if a["name"] == "walk")
	assert float(beat["length"]) == float(walk["length"]), (beat["length"], walk["length"])
	beat["animators"] = {k: an for k, an in beat["animators"].items() if an.get("name") not in BEAT_FREE}
	beat["animators"].update({k: json.loads(json.dumps(an)) for k, an in walk["animators"].items() if an.get("name") in BEAT_FREE})
	uid = {g["name"]: g["uuid"] for g in d["groups"]}
	for child, parent, off in RIG:
		lst, i = find(d["outliner"], uid[child])
		plst, j = find(d["outliner"], uid[parent])
		if lst is plst[j]["children"]:
			continue  # the artist nested it already: its keys are relative as they stand
		nest(d, child, parent, off)
	add_markers(d)
	jar = mc_jar()
	add_armour(d, jar)
	write_armour_assets(d, jar)
	OUT.write_text(json.dumps(d, indent="\t") + "\n")
	print("wrote", OUT)


if __name__ == "__main__":
	main(sys.argv[1])
