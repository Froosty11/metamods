package metacraft.ovvar.compat.danse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.tomalbrc.danse.api.BodyLayerModels;
import de.tomalbrc.danse.util.MinecraftSkinParser.BodyPart;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.pack.EquipmentJson;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The item models a gesturing stand-in wears an ovve with (see {@link DanseLayers}): one item
 * definition per piece and part, {@code ovvar:danse/<piece>/<part>}, a composite of
 * <ol>
 *   <li>the base cloth, chosen by {@code custom_model_data} string 0 (the base texture's name,
 *       {@code data/top}, {@code data/bottom_nercabbad}, …);</li>
 *   <li>per cell that lands on this part, bottom layer first, the patch chosen by string
 *       {@code 1 + Spot.cells(piece).indexOf(cell)} (its id, or empty for none).</li>
 * </ol>
 * Every model is a {@link BodyLayerModels#shell} over ovvar's own equipment texture — the same
 * 256×128 layer a real client's armour renderer draws — reached through an {@code items} atlas
 * source rather than copied. Left limbs take the base cloth's asymmetric art from the mirror strip
 * ({@link Spot#MIRROR_SHIFT} texels up), where datagen keeps it for the shader; patch textures for
 * left cells already sit pre-mirrored on the right limb's rectangle, which the shell reads mirrored.
 */
public final class DanseModels {
	private DanseModels() {}

	private static final Gson GSON = new GsonBuilder().create();

	private static final List<BodyPart> TOP_PARTS = List.of(BodyPart.BODY, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM);
	private static final List<BodyPart> BOTTOM_PARTS = List.of(BodyPart.BODY, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG);

	/** The top is a humanoid layer (Danse's outer pass), the bottom a leggings layer (inner). */
	public static boolean draws(Piece piece, BodyPart part) {
		return parts(piece).contains(part);
	}

	public static Identifier itemModel(Piece piece, BodyPart part) {
		return Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "danse/" + piece.id + "/" + part.getName());
	}

	/** {@code data/top}: the base texture's name without namespace or layer folder. */
	public static String baseKey(Chapter chapter, Piece piece, boolean nercabbad) {
		return EquipmentJson.baseTexture(chapter, piece, nercabbad).substring(Ovvar.MOD_ID.length() + 1);
	}

	public static List<String> strings(Chapter chapter, Piece piece, boolean nercabbad, List<Placement> placements) {
		List<Spot> cells = Spot.cells(piece);
		List<String> out = new ArrayList<>(1 + cells.size());
		out.add(baseKey(chapter, piece, nercabbad));
		for (int i = 0; i < cells.size(); i++) out.add("");
		for (Placement placement : placements) {
			int at = cells.indexOf(placement.spot());
			if (at >= 0) out.set(1 + at, placement.patch().id());
		}
		return out;
	}

	/** Everything this layer adds to the pack. */
	public static void write(BiConsumer<String, byte[]> out) {
		out.accept("assets/minecraft/atlases/items.json", bytes(atlas()));
		for (Piece piece : Piece.values()) {
			for (BodyPart part : parts(piece)) writePart(piece, part, out);
		}
	}

	private static void writePart(Piece piece, BodyPart part, BiConsumer<String, byte[]> out) {
		String prefix = "danse/" + piece.id + "/" + part.getName() + "/";
		JsonArray models = new JsonArray();

		JsonArray baseCases = new JsonArray();
		for (Chapter chapter : Chapter.values()) {
			for (boolean[] variant : EquipmentJson.variants(chapter, piece)) {
				String key = baseKey(chapter, piece, variant[0]);
				if (!exists(piece, key)) continue;
				String model = prefix + "base/" + key;
				out.accept(modelPath(model), bytes(BodyLayerModels.shell(part, textureId(piece, key),
						isLeft(part) ? -Spot.MIRROR_SHIFT : 0, 0f)));
				baseCases.add(when(key, model));
			}
		}
		if (!baseCases.isEmpty()) models.add(select(0, baseCases));

		List<Spot> cells = Spot.cells(piece);
		List<Spot> here = cells.stream().filter(spot -> lands(spot, part))
				.sorted(Comparator.comparingInt(Spot::layer)).toList();
		for (Spot spot : here) {
			JsonArray cases = new JsonArray();
			for (Patches.Patch patch : Patches.all()) {
				if (!patch.fits(spot)) continue;
				String texture = textureFor(new Placement(spot, patch), part);
				if (!exists(piece, texture)) continue;
				String model = prefix + spot.id() + "/" + patch.id();
				out.accept(modelPath(model), bytes(BodyLayerModels.shell(part, textureId(piece, texture),
						0, 0.01f * (1 + spot.layer()))));
				cases.add(when(patch.id(), model));
			}
			if (!cases.isEmpty()) models.add(select(1 + cells.indexOf(spot), cases));
		}

		JsonObject composite = new JsonObject();
		composite.addProperty("type", "minecraft:composite");
		composite.add("models", models);
		JsonObject root = new JsonObject();
		root.add("model", composite);
		out.accept("assets/" + Ovvar.MOD_ID + "/items/" + itemModel(piece, part).getPath() + ".json", bytes(GSON.toJson(root)));
	}

	/** Does this cell land on this part? The seat is one patch cut across both legs. */
	private static boolean lands(Spot spot, BodyPart part) {
		return switch (spot.side) {
			case BODY -> part == BodyPart.BODY;
			case RIGHT -> part == BodyPart.RIGHT_ARM || part == BodyPart.RIGHT_LEG;
			case LEFT -> part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG;
			case SEAT -> part == BodyPart.RIGHT_LEG || part == BodyPart.LEFT_LEG;
		};
	}

	/** The placement's texture on this part; the seat has one per leg, right first. */
	private static String textureFor(Placement placement, BodyPart part) {
		List<String> textures = EquipmentJson.textures(placement);
		return textures.size() == 1 ? textures.getFirst() : textures.get(part == BodyPart.RIGHT_LEG ? 0 : 1);
	}

	private static List<BodyPart> parts(Piece piece) {
		return piece == Piece.TOP ? TOP_PARTS : BOTTOM_PARTS;
	}

	private static boolean isLeft(BodyPart part) {
		return part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG;
	}

	private static Identifier textureId(Piece piece, String texture) {
		return Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "entity/equipment/" + piece.layer + "/" + texture);
	}

	/** Generated by datagen and on our classpath? (A retired patch, say, has no texture.) */
	private static boolean exists(Piece piece, String texture) {
		return DanseModels.class.getResource("/assets/" + Ovvar.MOD_ID + "/textures/entity/equipment/"
				+ piece.layer + "/" + texture + ".png") != null;
	}

	private static String modelPath(String model) {
		return "assets/" + Ovvar.MOD_ID + "/models/" + model + ".json";
	}

	private static JsonObject select(int index, JsonArray cases) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "minecraft:select");
		o.addProperty("property", "minecraft:custom_model_data");
		o.addProperty("index", index);
		o.add("cases", cases);
		JsonObject fallback = new JsonObject();
		fallback.addProperty("type", "minecraft:empty");
		o.add("fallback", fallback);
		return o;
	}

	private static JsonObject when(String value, String model) {
		JsonObject m = new JsonObject();
		m.addProperty("type", "minecraft:model");
		m.addProperty("model", Ovvar.MOD_ID + ":" + model);
		JsonObject c = new JsonObject();
		c.addProperty("when", value);
		c.add("model", m);
		return c;
	}

	/** Item models sample the items atlas first, then blocks (26.3 MaterialBaker); Polymer merges atlas files. */
	private static String atlas() {
		JsonArray sources = new JsonArray();
		for (Piece piece : Piece.values()) {
			JsonObject s = new JsonObject();
			s.addProperty("type", "minecraft:directory");
			s.addProperty("source", "entity/equipment/" + piece.layer);
			s.addProperty("prefix", "entity/equipment/" + piece.layer + "/");
			sources.add(s);
		}
		JsonObject root = new JsonObject();
		root.add("sources", sources);
		return GSON.toJson(root);
	}

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}
}
