package metacraft.ovvar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import metacraft.ovvar.store.DesignStoreConfig;
import metacraft.ovvar.store.StashConfig;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

import java.io.StringReader;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The config as keys, for {@code /ovvar config}: every leaf of {@code config/ovvar.json5} by its
 * dotted path ({@code stitches}, {@code stash.withdraw}, {@code designs.jdbc.url}), read and set
 * through {@link OvvarConfig#CODEC} itself. A set encodes the config to JSON, replaces the one
 * leaf and decodes it back, so a value out of range, a name no enum has or a string where a list
 * goes is refused with the codec's own message, and nothing half-typed ever reaches the config.
 *
 * <p>Help comes from the blocks' {@code HELP} maps, which is what the file's {@code _help}
 * entries are written from, so a key explains itself the same way in chat and in the file.
 */
public final class ConfigKeys {
	/** Keys that are never shown or set from chat. */
	public static final Set<String> SECRET = Set.of("designs.jdbc.password");

	private static final Map<String, Map<String, String>> HELP = Map.of(
			"", OvvarConfig.HELP,
			"server", ServerConfig.HELP,
			"designs", DesignStoreConfig.HELP,
			"designs.jdbc", DesignStoreConfig.Jdbc.HELP,
			"stash", StashConfig.HELP
	);

	private ConfigKeys() {}

	/**
	 * The config as the file has it, with every key present. The block codecs leave a value out
	 * of the file when it equals its default (that is what makes the file short), so the encoded
	 * JSON is filled in from the records themselves: each record component is a key, snake-cased,
	 * and its value is written the way the codec would write it.
	 */
	public static JsonObject json(OvvarConfig config) {
		JsonObject encoded = OvvarConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, config).getOrThrow().getAsJsonObject();
		return filled(encoded, config);
	}

	/** The block in the record's own order: what the codec wrote where it wrote something, the record's value otherwise. */
	private static JsonObject filled(JsonObject encoded, Record record) {
		JsonObject out = new JsonObject();
		for (RecordComponent component : record.getClass().getRecordComponents()) {
			String key = snake(component.getName());
			Object value;
			try {
				value = component.getAccessor().invoke(record);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(key, e);
			}
			JsonElement written = encoded.get(key);
			if (value instanceof Record nested) {
				out.add(key, filled(written != null && written.isJsonObject() ? written.getAsJsonObject() : new JsonObject(), nested));
			} else {
				out.add(key, written != null ? written : toJson(value));
			}
		}
		return out;
	}

	private static JsonElement toJson(Object value) {
		return switch (value) {
			case Boolean b -> new JsonPrimitive(b);
			case Number n -> new JsonPrimitive(n);
			case String s -> new JsonPrimitive(s);
			case GameType g -> new JsonPrimitive(g.getName());
			case StringRepresentable r -> new JsonPrimitive(r.getSerializedName());
			case Enum<?> e -> new JsonPrimitive(e.name().toLowerCase(Locale.ROOT));
			case Collection<?> c -> {
				JsonArray array = new JsonArray();
				for (Object o : c) array.add(toJson(o));
				yield array;
			}
			default -> new JsonPrimitive(String.valueOf(value));
		};
	}

	private static String snake(String camel) {
		return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
	}

	/** Every leaf's dotted key, in file order, help entries left out. */
	public static List<String> keys(OvvarConfig config) {
		List<String> keys = new ArrayList<>();
		collect(json(config), "", keys);
		return keys;
	}

	private static void collect(JsonObject block, String prefix, List<String> into) {
		for (Map.Entry<String, JsonElement> e : block.entrySet()) {
			if (e.getKey().startsWith("_")) continue;
			String key = prefix + e.getKey();
			if (e.getValue().isJsonObject()) collect(e.getValue().getAsJsonObject(), key + ".", into);
			else into.add(key);
		}
	}

	/** The leaf's value, or null if the key names nothing or names a block. */
	public static @Nullable JsonElement get(OvvarConfig config, String key) {
		JsonElement at = json(config);
		for (String part : key.split("\\.")) {
			if (!at.isJsonObject() || !at.getAsJsonObject().has(part) || part.startsWith("_")) return null;
			at = at.getAsJsonObject().get(part);
		}
		return at.isJsonObject() ? null : at;
	}

	/** The help for the key (the comment above it in the file), or null. */
	public static @Nullable String help(String key) {
		int dot = key.lastIndexOf('.');
		Map<String, String> block = HELP.get(dot < 0 ? "" : key.substring(0, dot));
		return block == null ? null : block.get(key.substring(dot + 1));
	}

	/** What a block is about (the comment above the block in the file; the file's own for ""), or null. */
	public static @Nullable String about(String block) {
		Map<String, String> help = HELP.get(block);
		return help == null ? null : help.get("_about");
	}

	/**
	 * The config with one leaf replaced by {@code value}, read as JSON ({@code true}, {@code 9},
	 * {@code "a name"}, {@code ["survival"]}); a bare word is a string. Refused, with the reason,
	 * for an unknown key, a block, a secret, or a value the codec will not take.
	 */
	public static DataResult<OvvarConfig> with(OvvarConfig config, String key, String value) {
		if (SECRET.contains(key)) return DataResult.error(() -> key + " is not set from chat; put it in the file or its _env variable");
		JsonObject root = json(config);
		String[] parts = key.split("\\.");
		JsonObject block = root;
		for (int i = 0; i < parts.length - 1; i++) {
			JsonElement next = block.get(parts[i]);
			if (next == null || !next.isJsonObject() || parts[i].startsWith("_")) return DataResult.error(() -> "no such key: " + key);
			block = next.getAsJsonObject();
		}
		String leaf = parts[parts.length - 1];
		JsonElement old = block.get(leaf);
		if (old == null || old.isJsonObject() || leaf.startsWith("_")) return DataResult.error(() -> "no such key: " + key);
		block.add(leaf, parse(value));
		return OvvarConfig.CODEC.codec().parse(JsonOps.INSTANCE, root);
	}

	/** JSON if the whole text reads as one JSON value, else the text as a string ({@code Skogen} → {@code "Skogen"}). */
	static JsonElement parse(String value) {
		try {
			JsonReader reader = new JsonReader(new StringReader(value));
			reader.setStrictness(Strictness.LENIENT);
			JsonElement parsed = JsonParser.parseReader(reader);
			return reader.peek() == JsonToken.END_DOCUMENT ? parsed : new JsonPrimitive(value);
		} catch (Exception e) {
			return new JsonPrimitive(value);
		}
	}
}
