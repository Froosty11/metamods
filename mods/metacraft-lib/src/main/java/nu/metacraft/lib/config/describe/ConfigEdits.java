package nu.metacraft.lib.config.describe;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.*;

/**
 * Edits a described config the way the screen does: the current value is written out, the typed
 * text is put into that JSON, and the result is read back with the config's own codec, then its
 * {@code validate()}. So an edit can only produce what the file could hold.
 */
public final class ConfigEdits {
	private ConfigEdits() {}

	public static <R extends Record> DataResult<R> apply(
			ConfigSpec<R> spec, Codec<R> codec, R current, List<String> page, Map<String, String> values,
			DynamicOps<JsonElement> ops
	) {
		ConfigSpec<?> pageSpec;
		try {
			pageSpec = pageSpec(spec, page);
		} catch (IllegalArgumentException e) {
			return DataResult.error(e::getMessage);
		}
		JsonObject root = codec.encodeStart(ops, current).getOrThrow().getAsJsonObject();
		JsonObject object = navigate(root, page);
		for (Map.Entry<String, String> entry : values.entrySet()) {
			Optional<OptionSpec> option = pageSpec.option(entry.getKey());
			if (option.isEmpty() || !option.get().editable()) {
				return DataResult.error(() -> entry.getKey() + ": not an option that can be edited here");
			}
			Optional<String> problem = put(object, option.get(), entry.getValue());
			if (problem.isPresent()) {
				String message = option.get().key() + " (" + option.get().description() + "): " + problem.get();
				return DataResult.error(() -> message);
			}
		}
		DataResult<R> read = readBack(spec, codec, root, ops);
		if (read.error().isPresent()) return read;
		Optional<String> lost = lostEdit(spec, pageSpec, codec, read.getOrThrow(), page, object, values.keySet(), ops);
		if (lost.isPresent()) return DataResult.error(lost::get);
		return read;
	}

	/**
	 * An edited key the codec does not store under that name (a file key renamed in the codec but
	 * not in the {@code @Option}): the value read back, written out again, lacks what the edit put.
	 * A key left out at the value in {@code DEFAULT} ({@code optionalFieldOf(key, default)}) is fine
	 * when the edit asked for exactly that value.
	 */
	private static <R extends Record> Optional<String> lostEdit(
			ConfigSpec<R> spec, ConfigSpec<?> pageSpec, Codec<R> codec, R value, List<String> page,
			JsonObject edited, Set<String> keys, DynamicOps<JsonElement> ops
	) {
		JsonElement again = codec.encodeStart(ops, value).getOrThrow();
		Record pageValue = value;
		ConfigSpec<?> at = spec;
		for (String key : page) {
			OptionSpec section = at.option(key).orElseThrow();
			pageValue = (Record) section.read(pageValue);
			at = section.section();
			again = again != null && again.isJsonObject() ? again.getAsJsonObject().get(key) : null;
		}
		JsonObject written = again != null && again.isJsonObject() ? again.getAsJsonObject() : new JsonObject();
		for (String key : keys) {
			OptionSpec option = pageSpec.option(key).orElseThrow();
			JsonElement wanted = edited.get(key), stored = written.get(key);
			if (sameJson(wanted, stored)) continue;
			if (stored == null && leftOutAtDefault(pageSpec, option, pageValue, wanted)) continue;
			return Optional.of(option.key() + " (" + option.description() + "): the mod's config file does not store this option under \""
					+ option.key() + "\"; nothing was saved. Edit the file instead.");
		}
		return Optional.empty();
	}

	private static boolean leftOutAtDefault(ConfigSpec<?> pageSpec, OptionSpec option, Record pageValue, JsonElement wanted) {
		Object fallback = option.read(pageSpec.defaults());
		if (!Objects.equals(option.read(pageValue), fallback)) return false;
		JsonObject asTyped = new JsonObject();   // the default as the screen would have put it
		put(asTyped, option, text(option, fallback));
		return sameJson(wanted, asTyped.get(option.key()));
	}

	/** JSON equality with numbers compared by value, so 3 and 3.0 (or a float's widening) match. */
	private static boolean sameJson(JsonElement a, JsonElement b) {
		if (a == null || b == null) return a == b;
		if (a.isJsonPrimitive() && b.isJsonPrimitive() && a.getAsJsonPrimitive().isNumber() && b.getAsJsonPrimitive().isNumber()) {
			double x = a.getAsDouble(), y = b.getAsDouble();
			return x == y || (float) x == (float) y;
		}
		if (a.isJsonArray() && b.isJsonArray()) {
			JsonArray p = a.getAsJsonArray(), q = b.getAsJsonArray();
			if (p.size() != q.size()) return false;
			for (int i = 0; i < p.size(); i++) if (!sameJson(p.get(i), q.get(i))) return false;
			return true;
		}
		if (a.isJsonObject() && b.isJsonObject()) {
			JsonObject p = a.getAsJsonObject(), q = b.getAsJsonObject();
			if (!p.keySet().equals(q.keySet())) return false;
			for (String k : p.keySet()) if (!sameJson(p.get(k), q.get(k))) return false;
			return true;
		}
		return a.equals(b);
	}

	public static <R extends Record> DataResult<R> reset(
			ConfigSpec<R> spec, Codec<R> codec, R current, List<String> page, DynamicOps<JsonElement> ops
	) {
		if (page.isEmpty()) {
			return DataResult.success(spec.defaults());
		}
		R withDefaults;
		try {
			pageSpec(spec, page);   // refuses a path that is not a page
			withDefaults = spec.type().cast(withSectionDefault(spec, current, page));
		} catch (IllegalArgumentException e) {
			return DataResult.error(e::getMessage);
		}
		JsonObject root = codec.encodeStart(ops, withDefaults).getOrThrow().getAsJsonObject();
		return readBack(spec, codec, root, ops);
	}

	/** {@code value} with the section at {@code page} replaced by that section's {@code DEFAULT}. */
	private static Record withSectionDefault(ConfigSpec<?> spec, Record value, List<String> page) {
		if (page.isEmpty()) return spec.defaults();
		OptionSpec option = spec.option(page.getFirst()).orElseThrow();
		Object[] values = spec.options().stream().map(o -> o.read(value)).toArray();
		int index = spec.options().indexOf(option);
		values[index] = withSectionDefault(option.section(), (Record) values[index], page.subList(1, page.size()));
		return spec.construct(values);
	}

	private static <R extends Record> DataResult<R> readBack(ConfigSpec<R> spec, Codec<R> codec, JsonObject root, DynamicOps<JsonElement> ops) {
		DataResult<R> read = codec.parse(ops, root);
		if (read.error().isPresent()) {
			String message = read.error().get().message();
			return DataResult.error(() -> message);
		}
		R value = read.getOrThrow();
		Optional<String> invalid = spec.validate(value);
		if (invalid.isPresent()) return DataResult.error(invalid::get);
		return DataResult.success(value);
	}

	public static ConfigSpec<?> pageSpec(ConfigSpec<?> top, List<String> page) {
		ConfigSpec<?> spec = top;
		for (String key : page) {
			OptionSpec option = spec.option(key).orElseThrow(() -> new IllegalArgumentException("no page " + String.join("/", page)));
			if (option.kind() != OptionKind.SECTION) throw new IllegalArgumentException(key + " is not a page");
			spec = option.section();
		}
		return spec;
	}

	private static JsonObject navigate(JsonObject root, List<String> page) {
		JsonObject object = root;
		for (String key : page) {
			if (!object.has(key) || !object.get(key).isJsonObject()) object.add(key, new JsonObject());
			object = object.getAsJsonObject(key);
		}
		return object;
	}

	/** Puts the typed text into the JSON; a message when it is not a value of the option's kind. */
	private static Optional<String> put(JsonObject object, OptionSpec option, String typed) {
		String text = option.kind() == OptionKind.TEXT ? typed : typed.trim();
		if (text.isEmpty() && option.optional()) {
			object.remove(option.key());
			return Optional.empty();
		}
		switch (option.kind()) {
			case BOOLEAN -> {
				if (!text.equals("true") && !text.equals("false")) return Optional.of("\"" + text + "\" is not true or false");
				object.addProperty(option.key(), Boolean.parseBoolean(text));
			}
			case WHOLE -> {
				if (text.isEmpty()) return Optional.of("needs a value");
				long value;
				try {
					value = Long.parseLong(text);
				} catch (NumberFormatException e) {
					return Optional.of("\"" + text + "\" is not a whole number");
				}
				// Codec.INT would silently wrap a long that does not fit.
				Class<?> type = option.valueType();
				if (type == int.class || type == Integer.class) {
					if (value > Integer.MAX_VALUE) return Optional.of(text + " is too large (at most " + Integer.MAX_VALUE + ")");
					if (value < Integer.MIN_VALUE) return Optional.of(text + " is too small (at least " + Integer.MIN_VALUE + ")");
				}
				if (value < option.min() || value > option.max()) return Optional.of(value + " is not in " + option.rangeText());
				object.addProperty(option.key(), value);
			}
			case DECIMAL -> {
				if (text.isEmpty()) return Optional.of("needs a value");
				try {
					double value = Double.parseDouble(text);
					if (Double.isNaN(value) || Double.isInfinite(value)) return Optional.of("\"" + text + "\" is not a number");
					if (value < option.min() || value > option.max()) {
						return Optional.of(OptionSpec.number(value) + " is not in " + option.rangeText());
					}
					object.addProperty(option.key(), value);
				} catch (NumberFormatException e) {
					return Optional.of(text.contains(",")
							? "\"" + text + "\" is not a number; use a point for decimals, like 1.5"
							: "\"" + text + "\" is not a number");
				}
			}
			case TEXT -> object.addProperty(option.key(), text);
			case IDENTIFIER -> {
				Identifier id = Identifier.tryParse(text);
				if (id == null || text.isEmpty()) return Optional.of("\"" + text + "\" is not an id like minecraft:stone");
				object.addProperty(option.key(), id.toString());
			}
			case CHOICE -> {
				if (!option.choices().contains(text)) return Optional.of("\"" + text + "\" is not one of " + String.join(", ", option.choices()));
				object.addProperty(option.key(), text);
			}
			case TEXT_LIST, IDENTIFIER_LIST -> {
				JsonArray array = new JsonArray();
				for (String line : text.split("\n")) {
					String entry = line.trim();
					if (entry.isEmpty()) continue;
					if (option.kind() == OptionKind.IDENTIFIER_LIST) {
						Identifier id = Identifier.tryParse(entry);
						if (id == null) return Optional.of("\"" + entry + "\" is not an id like minecraft:stone");
						entry = id.toString();
					}
					array.add(entry);
				}
				object.add(option.key(), array);
			}
			case SECTION, READ_ONLY -> {
				return Optional.of("cannot be edited here");
			}
		}
		return Optional.empty();
	}

	/** The text an input starts with: what {@link #apply} would read back as the same value. */
	public static String text(OptionSpec option, Object value) {
		if (value instanceof Optional<?> optional) {
			if (optional.isEmpty()) return "";
			value = optional.get();
		}
		return switch (option.kind()) {
			case WHOLE -> Long.toString(((Number) value).longValue());
			case DECIMAL -> OptionSpec.number(((Number) value).doubleValue());
			case CHOICE -> ((StringRepresentable) value).getSerializedName();
			case TEXT_LIST, IDENTIFIER_LIST -> String.join("\n", ((List<?>) value).stream().map(Object::toString).toList());
			default -> String.valueOf(value);
		};
	}
}
