package se.metacraft.ovvar.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.content.item.OvveFeetItem;
import se.metacraft.ovvar.content.item.OvveItem;
import se.metacraft.ovvar.content.item.OvveTopItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Named variants: a chapter's ovve or frack whose custom name is exactly a variant's name (case and
 * spaces and all, and no style: what an anvil writes) is drawn in the variant's cloth instead of the
 * chapter's, worn and as its icon ({@link Cloth}). The list is data, {@value #RESOURCE} on the
 * classpath, which datagen and the server both read:
 *
 * <pre>{ "&lt;chapter id&gt;": [ { "name": "&lt;exact name&gt;", "texture": "&lt;id&gt;" } ] }</pre>
 *
 * The art is {@code art/ovvar/variants/<chapter>/<id>.png} (a 64×64 skin overlay, like a chapter's),
 * {@code <id>-nercabbad.png} for a chapter that rolls down and {@code <id>-icon.png} (16×16). A file
 * named {@code _<file>} is AI-drawn and used until a plain-named one replaces it ({@link #artFile},
 * kultur's ArtFiles rule). A mob's garment never wears a variant.
 */
public final class Variants {
	private Variants() {}

	public static final String RESOURCE = "/art/" + Ovvar.MOD_ID + "/variants.json";
	/** Where a chapter's variant art lives, under {@code art/ovvar/}: {@code variants/<chapter>/}. */
	public static final String ART_DIR = "variants/";
	private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

	/** One variant: its chapter, the exact name that calls it up, and its id (art files, cloth id). */
	public record Variant(Chapter chapter, String name, String id) {
		@Override
		public String toString() {
			return chapter.id + ":" + id;
		}
	}

	private static final List<Variant> ALL = load();

	public static List<Variant> all() {
		return ALL;
	}

	/** A chapter's variants, in the file's order. */
	public static List<Variant> of(Chapter chapter) {
		return ALL.stream().filter(v -> v.chapter() == chapter).toList();
	}

	public static @Nullable Variant byId(Chapter chapter, String id) {
		for (Variant v : ALL) if (v.chapter() == chapter && v.id().equals(id)) return v;
		return null;
	}

	/** The variant a custom name calls up on this chapter's garment: an exact, unstyled match, or null. */
	public static @Nullable Variant named(Chapter chapter, @Nullable Component name) {
		if (name == null) return null;
		for (Variant v : ALL) if (v.chapter() == chapter && Component.literal(v.name()).equals(name)) return v;
		return null;
	}

	/**
	 * The variant a garment stack is drawn in, or null for the chapter's own cloth: an ovve or frack by
	 * its custom name, its companion top and cuffs by the {@link ModComponents#VARIANT} their ovve gave
	 * them. A mob's garment never has one.
	 */
	public static @Nullable Variant of(ItemStack stack) {
		if (MobOvvar.is(stack)) return null;
		if (stack.getItem() instanceof OvveItem item) return named(item.chapter, stack.get(DataComponents.CUSTOM_NAME));
		String id = stack.get(ModComponents.VARIANT);
		if (id == null) return null;
		if (stack.getItem() instanceof OvveTopItem item) return byId(item.chapter, id);
		if (stack.getItem() instanceof OvveFeetItem item) return byId(item.chapter, id);
		return null;
	}

	/** The variant's inventory icon model: {@code ovvar:item/variant/<chapter>/<id>}. */
	public static Identifier iconModel(Variant v) {
		return Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "item/variant/" + v.chapter().id + "/" + v.id());
	}

	/**
	 * Which file is the art for {@code name} ({@code spaceships}, {@code spaceships-nercabbad}, ...): a plain
	 * {@code <name>.png} wins over the AI-drawn {@code _<name>.png}; null if neither exists.
	 */
	public static @Nullable String artFile(String name, Predicate<String> exists) {
		String plain = name + ".png", drawn = "_" + name + ".png";
		if (exists.test(plain)) {
			if (exists.test(drawn)) Ovvar.LOGGER.info("[ovvar] variant art {} is ignored: {} replaces it", drawn, plain);
			return plain;
		}
		return exists.test(drawn) ? drawn : null;
	}

	/** The art resource (without {@code .png}, under {@code art/ovvar/}) for one of a variant's files, or null. */
	public static @Nullable String art(Variant v, String suffix) {
		String dir = ART_DIR + v.chapter().id + "/";
		String file = artFile(v.id() + suffix, f -> Variants.class.getResource("/art/" + Ovvar.MOD_ID + "/" + dir + f) != null);
		return file == null ? null : dir + file.substring(0, file.length() - ".png".length());
	}

	// ---- reading the list

	private static List<Variant> load() {
		try (InputStream in = Variants.class.getResourceAsStream(RESOURCE)) {
			if (in == null) return List.of();
			return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException("cannot read " + RESOURCE, e);
		}
	}

	/** The list from the file's text; a wrong entry is an {@link IllegalStateException} naming it (startup and datagen fail). */
	public static List<Variant> parse(String json) {
		List<Variant> out = new ArrayList<>();
		JsonObject root;
		try {
			root = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IllegalStateException(RESOURCE + " is not a JSON object", e);
		}
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			Chapter chapter = Chapter.byId(entry.getKey());
			if (chapter == null) throw new IllegalStateException(RESOURCE + ": no chapter " + entry.getKey());
			Set<String> names = new HashSet<>(), ids = new HashSet<>();
			for (JsonElement element : entry.getValue().getAsJsonArray()) {
				JsonObject o = element.getAsJsonObject();
				String name = string(o, "name", chapter), id = string(o, "texture", chapter);
				if (!ID.matcher(id).matches()) throw new IllegalStateException(RESOURCE + ": " + chapter.id + " texture '" + id + "' is not [a-z0-9_]");
				if (!names.add(name)) throw new IllegalStateException(RESOURCE + ": " + chapter.id + " names a variant '" + name + "' twice");
				if (!ids.add(id)) throw new IllegalStateException(RESOURCE + ": " + chapter.id + " uses texture '" + id + "' twice");
				out.add(new Variant(chapter, name, id));
			}
		}
		return List.copyOf(out);
	}

	private static String string(JsonObject o, String key, Chapter chapter) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || e.getAsString().isEmpty()) throw new IllegalStateException(RESOURCE + ": a " + chapter.id + " variant has no " + key);
		return e.getAsString();
	}
}
