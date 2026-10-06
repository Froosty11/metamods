package se.metacraft.config.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import se.metacraft.config.comments.CodecExtension;
import se.metacraft.config.comments.MapCodecExtension;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommentCodec {

	public static <T> Codec<T> comment(Codec<T> codec, Component comment) {
		//noinspection unchecked
		return ((CodecExtension<T>) codec).metacraft$comment(comment);
	}

	public static <T> MapCodec<T> comment(MapCodec<T> codec, Component comment) {
		//noinspection unchecked
		return ((MapCodecExtension<T>) codec).metacraft$comment(comment);
	}

	public static <T> Codec<T> comment(Codec<T> codec, String comment) {
		return comment(codec, Component.literal(comment));
	}

	public static <T> MapCodec<T> comment(MapCodec<T> codec, String comment) {
		return comment(codec, Component.literal(comment));
	}

	private static String combine(String first, String... rest) {
		return Stream.concat(Stream.of(first), Stream.of(rest)).collect(Collectors.joining("\n"));
	}

	public static <T> Codec<T> comment(Codec<T> codec, String comment, String... extra) {
		return comment(codec, combine(comment, extra));
	}

	public static <T> MapCodec<T> comment(MapCodec<T> codec, String comment, String... extra) {
		return comment(codec, combine(comment, extra));
	}

}
