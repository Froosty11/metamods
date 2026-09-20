package metacraft.ovvar;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonGrammar;
import blue.endless.jankson.api.SyntaxError;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/**
 * A config file that is JSON with comments, through Jankson. Reading takes JSON5 (so also plain
 * JSON, and an admin's {@code //} notes), turns it into a plain JSON tree and decodes it through
 * the config's codec, which is where every value is checked. Writing takes the plain tree with
 * every key present, puts each key's help in a comment line above it, keeps whatever comment the
 * file already had on a key that has one, and prints it with quoted keys and no trailing commas,
 * so the file still reads as JSON to anything that can skip comments.
 */
public final class ConfigFile {
	private static final Jankson JANKSON = Jankson.builder().build();
	private static final JsonGrammar GRAMMAR = JsonGrammar.builder()
			.withComments(true).printWhitespace(true).printTrailingCommas(false).printUnquotedKeys(false).bareSpecialNumerics(false).build();

	private ConfigFile() {}

	/** The file decoded through the codec. A file that does not parse or a value the codec refuses is an error, with the file named. */
	public static <T> T read(Path path, MapCodec<T> codec) {
		JsonObject json = plain(load(path));
		DataResult<T> result = codec.codec().parse(JsonOps.INSTANCE, json);
		return result.getOrThrow(message -> new IllegalStateException(path.getFileName() + ": " + message));
	}

	/**
	 * Writes {@code json} (every key present) with {@code help} as the comment over each leaf, by
	 * its dotted key, and {@code about} over each block (and at the top of the file, for ""); a
	 * comment the file already carries on a key is kept over the help.
	 */
	public static void write(Path path, JsonObject json, Function<String, @Nullable String> help, Function<String, @Nullable String> about) {
		blue.endless.jankson.JsonObject existing = Files.exists(path) ? tryLoad(path) : null;
		blue.endless.jankson.JsonObject out = commented(json, "", existing, help, about);
		// No comment above the opening brace: Jankson reads one there as the first key's, and it
		// would then be kept and written again over every save.
		String text = out.toJson(GRAMMAR);
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, text + "\n");
		} catch (IOException e) {
			throw new UncheckedIOException("could not write " + path, e);
		}
	}

	private static blue.endless.jankson.JsonObject commented(
			JsonObject block, String prefix, blue.endless.jankson.@Nullable JsonObject existing,
			Function<String, @Nullable String> help, Function<String, @Nullable String> about
	) {
		blue.endless.jankson.JsonObject out = new blue.endless.jankson.JsonObject();
		for (Map.Entry<String, JsonElement> e : block.entrySet()) {
			if (e.getKey().startsWith("_")) continue;
			String key = prefix + e.getKey();
			String kept = existing == null ? null : existing.getComment(e.getKey());
			blue.endless.jankson.JsonElement value;
			if (e.getValue().isJsonObject()) {
				blue.endless.jankson.JsonObject was = existing != null && existing.get(e.getKey()) instanceof blue.endless.jankson.JsonObject o ? o : null;
				value = commented(e.getValue().getAsJsonObject(), key + ".", was, help, about);
			} else {
				value = toJankson(e.getValue());
			}
			String comment = kept != null && !kept.isBlank() ? kept : e.getValue().isJsonObject() ? about.apply(key) : help.apply(key);
			out.put(e.getKey(), value, comment);
		}
		return out;
	}

	private static blue.endless.jankson.JsonElement toJankson(JsonElement element) {
		if (element.isJsonNull()) return blue.endless.jankson.JsonNull.INSTANCE;
		if (element.isJsonArray()) {
			blue.endless.jankson.JsonArray array = new blue.endless.jankson.JsonArray();
			for (JsonElement item : element.getAsJsonArray()) array.add(toJankson(item));
			return array;
		}
		if (element.isJsonObject()) {
			blue.endless.jankson.JsonObject object = new blue.endless.jankson.JsonObject();
			for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) object.put(e.getKey(), toJankson(e.getValue()));
			return object;
		}
		var p = element.getAsJsonPrimitive();
		if (p.isBoolean()) return new blue.endless.jankson.JsonPrimitive(p.getAsBoolean());
		if (p.isNumber()) {
			Number n = p.getAsNumber();
			double d = n.doubleValue();
			return new blue.endless.jankson.JsonPrimitive(d == Math.rint(d) && !p.getAsString().contains(".") ? (Number) n.longValue() : (Number) d);
		}
		return new blue.endless.jankson.JsonPrimitive(p.getAsString());
	}

	/** Jankson's tree as Gson's, comments dropped, for the codec. */
	private static JsonObject plain(blue.endless.jankson.JsonObject json) {
		return JsonParser.parseString(json.toJson(false, false)).getAsJsonObject();
	}

	private static blue.endless.jankson.JsonObject load(Path path) {
		try {
			return JANKSON.load(path.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException("could not read " + path, e);
		} catch (SyntaxError e) {
			throw new IllegalStateException(path.getFileName() + ": " + e.getCompleteMessage());
		}
	}

	private static blue.endless.jankson.@Nullable JsonObject tryLoad(Path path) {
		try {
			return load(path);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
