package nu.metacraft.lib.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import nu.metacraft.lib.METAcraftLib;

import java.io.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.HolderLookup;

public class JsonHelper {

	public static <T> Optional<T> load(Path configPath, Codec<T> codec) {
		return load(configPath, codec, ops -> ops);
	}

	public static <T> boolean save(Path configPath, Codec<T> codec, T object) {
		return save(configPath, codec, object, ops -> ops);
	}

	public static <T> Optional<T> load(Path configPath, Codec<T> codec, HolderLookup.Provider lookup) {
		return load(configPath, codec, lookup::createSerializationContext);
	}

	public static <T> boolean save(Path configPath, Codec<T> codec, T object, HolderLookup.Provider lookup) {
		return save(configPath, codec, object, lookup::createSerializationContext);
	}

	public static <T> Optional<T> load(Path configPath, Codec<T> codec, UnaryOperator<DynamicOps<JsonElement>> opsFixer) {
		File file = configPath.toFile();
		if (!file.exists()) return Optional.empty();
		try (var reader = new BufferedReader(new FileReader(file))) { //The BufferedReader is to boost performance.
			JsonElement element = JsonParser.parseReader(reader);
			return codec.parse(opsFixer.apply(JsonOps.INSTANCE), element).resultOrPartial(METAcraftLib.LOGGER::error);
		} catch (JsonSyntaxException e) {
			METAcraftLib.LOGGER.error("Syntax error when parsing \"" + configPath + "\": " + e.getMessage());
		} catch (JsonIOException | IOException e) {
			METAcraftLib.LOGGER.error(e);
		}
		return Optional.empty();
	}

	/**
	 * The file read with {@code codec}: empty when there is no file, otherwise the raw
	 * {@link DataResult}. On error, the result may still carry a partial value (e.g. a map codec
	 * keeping the entries that did parse); callers decide what to do with that.
	 */
	public static <T> Optional<DataResult<T>> read(Path configPath, Codec<T> codec, UnaryOperator<DynamicOps<JsonElement>> opsFixer) {
		File file = configPath.toFile();
		if (!file.exists()) return Optional.empty();
		try (var reader = new BufferedReader(new FileReader(file))) {
			JsonElement element = JsonParser.parseReader(reader);
			return Optional.of(codec.parse(opsFixer.apply(JsonOps.INSTANCE), element));
		} catch (JsonParseException e) {
			return Optional.of(DataResult.error(() -> "not valid JSON: " + e.getMessage()));
		} catch (IOException e) {
			return Optional.of(DataResult.error(() -> "cannot read the file: " + e.getMessage()));
		}
	}

	/** @return whether the file was written. */
	public static <T> boolean save(Path configPath, Codec<T> codec, T object, UnaryOperator<DynamicOps<JsonElement>> opsFixer) {
		Optional<JsonElement> data = codec.encodeStart(opsFixer.apply(JsonOps.INSTANCE), object).resultOrPartial(METAcraftLib.LOGGER::error);
		if (data.isEmpty()) return false;
		File file = configPath.toFile();
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException err) {
				err.printStackTrace();
				return false;
			}
		}
		//The BufferedWriter is to boost performance.
		try (var writer = new JsonWriter(new BufferedWriter(new FileWriter(file)))) {
			writer.setIndent("\t");
			Streams.write(data.get(), writer);
		} catch (IOException error) {
			error.printStackTrace();
			return false;
		}
		return true;
	}

}
