package se.metacraft.ovvar;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import se.metacraft.config.util.CommentCodec;

import java.util.Arrays;
import java.util.Optional;

/** How ovvar's config files are written: through metacraft-config, every key with its comment. */
public final class ConfigFields {
	private ConfigFields() {}

	/**
	 * A key that reads as {@code defaultValue} when missing but is always written, so a server's file
	 * shows every setting with its comment. (optionalFieldOf(key, default) would leave out every key
	 * still at its default.) A value it can't read is an error that names the key.
	 */
	public static <T> MapCodec<T> field(Codec<T> codec, String name, T defaultValue, String comment, String... more) {
		MapCodec<T> field = named(codec, name).optionalFieldOf(name).xmap(present -> present.orElse(defaultValue), Optional::of);
		return CommentCodec.comment(field, comment, more);
	}

	/** {@code codec}, its decode errors prefixed with {@code name} (a range error alone doesn't say which key). */
	private static <T> Codec<T> named(Codec<T> codec, String name) {
		return Codec.of(codec, new Decoder<T>() {
			@Override
			public <U> DataResult<Pair<T, U>> decode(DynamicOps<U> ops, U input) {
				return codec.decode(ops, input).mapError(message -> name + ": " + message);
			}
		});
	}

	/** A required key with its comment. */
	public static <T> MapCodec<T> required(MapCodec<T> field, String comment, String... more) {
		return CommentCodec.comment(field, comment, more);
	}

	static String[] lines(String... parts) {
		return Arrays.copyOf(parts, parts.length);
	}
}
