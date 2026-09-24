package nu.metacraft.config.source;

import com.google.gson.*;
import nu.metacraft.lib.config.describe.OptionKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * PolyDecorations' feature switches ({@code config/polydecorations.json}, the Froosty11 fork's
 * format: a {@code features} object of booleans). Every feature is registered at startup, so every
 * change applies after a restart. The file is read each time, so hand edits show up at once.
 */
public final class PolyDecorationsSource implements ConfigSource {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final Map<String, Boolean> atStart;

	private PolyDecorationsSource(Path file, Map<String, Boolean> atStart) {
		this.file = file;
		this.atStart = atStart;
	}

	public static Optional<PolyDecorationsSource> detect(Path file) {
		return read(file).map(json -> new PolyDecorationsSource(file, features(json)));
	}

	private static Optional<JsonObject> read(Path file) {
		try {
			if (!Files.exists(file)) return Optional.empty();
			JsonElement json = JsonParser.parseString(Files.readString(file));
			if (!json.isJsonObject() || !json.getAsJsonObject().has("features") || !json.getAsJsonObject().get("features").isJsonObject()) {
				return Optional.empty();
			}
			return Optional.of(json.getAsJsonObject());
		} catch (IOException | JsonParseException e) {
			return Optional.empty();
		}
	}

	private static Map<String, Boolean> features(JsonObject json) {
		Map<String, Boolean> features = new LinkedHashMap<>();
		for (var entry : json.getAsJsonObject("features").entrySet()) {
			if (entry.getKey().isEmpty()) continue;   // nothing to label, and no feature has no name
			if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isBoolean()) {
				features.put(entry.getKey(), entry.getValue().getAsBoolean());
			}
		}
		return features;
	}

	private String text() {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			return "";
		}
	}

	@Override public String id() { return "polydecorations"; }
	@Override public String name() { return "PolyDecorations"; }
	@Override public String description() { return "Which PolyDecorations features exist. Applies after a restart."; }
	@Override public int hash() { return text().hashCode(); }

	@Override
	public Optional<String> loadError() {
		return read(file).isPresent() ? Optional.empty() : Optional.of("the file is missing, or is not the fork's format");
	}

	@Override
	public List<String> pendingRestart() {
		Map<String, Boolean> now = read(file).map(PolyDecorationsSource::features).orElse(atStart);
		List<String> pending = new ArrayList<>();
		now.forEach((feature, on) -> {
			if (!on.equals(atStart.get(feature))) pending.add(label(feature) + (on ? " on" : " off"));
		});
		return pending;
	}

	@Override
	public Page page(List<String> path) {
		if (!path.isEmpty()) throw new IllegalArgumentException("PolyDecorations has one page");
		Map<String, Boolean> now = read(file).map(PolyDecorationsSource::features).orElse(atStart);
		List<Field> fields = new ArrayList<>();
		now.forEach((feature, on) -> fields.add(new Field(feature, label(feature), OptionKind.BOOLEAN, on.toString(),
				0, 1, 0, false, List.of(), true, true)));
		return new Page(List.of(), "PolyDecorations", fields, List.of());
	}

	@Override
	public EditOutcome apply(List<String> path, Map<String, String> values, int expectedHash) {
		if (!path.isEmpty()) return new EditOutcome.Refused("PolyDecorations has one page");
		if (expectedHash != hash()) return new EditOutcome.Stale();
		// A missing file is fine (nothing written yet); an existing file we cannot parse is not — do
		// not treat it as a blank slate and overwrite whatever is actually wrong with it.
		if (Files.exists(file) && read(file).isEmpty()) {
			return new EditOutcome.Refused("cannot read " + file + ": it is not the fork's format, or is broken");
		}
		JsonObject json = read(file).orElseGet(() -> {
			JsonObject fresh = new JsonObject();
			fresh.add("features", new JsonObject());
			return fresh;
		});
		JsonObject features = json.getAsJsonObject("features");
		for (var entry : values.entrySet()) {
			if (!features.has(entry.getKey())) return new EditOutcome.Refused("PolyDecorations has no feature " + entry.getKey());
			String value = entry.getValue().trim();
			if (!value.equals("true") && !value.equals("false")) return new EditOutcome.Refused(entry.getKey() + ": \"" + value + "\" is not true or false");
			features.addProperty(entry.getKey(), Boolean.parseBoolean(value));
		}
		try {
			Files.writeString(file, GSON.toJson(json));
		} catch (IOException e) {
			return new EditOutcome.Refused("cannot write " + file + ": " + e.getMessage());
		}
		return new EditOutcome.Saved();
	}

	@Override
	public EditOutcome reset(List<String> path, int expectedHash) {
		if (!path.isEmpty()) return new EditOutcome.Refused("PolyDecorations has one page");
		Map<String, String> allOn = new LinkedHashMap<>();
		page(path).fields().forEach(field -> allOn.put(field.key(), "true"));
		return apply(path, allOn, expectedHash);   // the fork's default is every feature on
	}

	private static String label(String feature) {
		String words = feature.replace('_', ' ');
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}
}
