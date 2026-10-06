package se.metacraft.config.parser.metadata;

import com.mojang.serialization.*;
import net.minecraft.core.HolderLookup;
import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config.parser.result.AbstractCodecResult;
import se.metacraft.config.parser.MetadataKey;

import java.util.function.Function;
import java.util.function.Supplier;

public record Container<T extends Container.ContainerData>(ContainerType<T> type, T data) implements Metadata {

	public static <T extends ContainerData> Container<?> simple(ContainerType<T> type) {
		if (type.clazz().isInstance(ContainerData.EMPTY)) {
			return new Container<>(type, type.clazz().cast(ContainerData.EMPTY));
		}
		throw new IllegalArgumentException(type + " requires data");
	}

	@Override
	public MetadataKey<?> key() {
		return MetadataKey.CONTAINER;
	}

	public interface ContainerData {
		ContainerData EMPTY = new ContainerData() {};
	}

	public record DispatchedEither(
		Function<Object, ? extends DataResult<Object>> keyByValue,
		Function<Object, ? extends DataResult<? extends MapDecoder<Object>>> decoderByKey,
		Function<Object, ? extends DataResult<? extends MapEncoder<Object>>> encoderByValue
	) implements ContainerData {

		public static <K, V> DispatchedEither create(
			Function<? super V, ? extends DataResult<? extends K>> keyByValue,
			Function<? super K, ? extends DataResult<? extends MapDecoder<? extends V>>> decoderByKey,
			Function<? super V, ? extends DataResult<? extends MapEncoder<V>>> encoderByValue
		) {
			//noinspection unchecked
			return new DispatchedEither(
				(Function<Object, ? extends DataResult<Object>>) keyByValue,
				(Function<Object, ? extends DataResult<? extends MapDecoder<Object>>>) decoderByKey,
				(Function<Object, ? extends DataResult<? extends MapEncoder<Object>>>) encoderByValue
			);
		}

	}

	public record DispatchedMap(Function<Object, ? extends Codec<Object>> valueCodecFunction) implements ContainerData {
		public static <K, V> DispatchedMap create(Function<? extends K, ? extends Codec<? extends V>> valueCodecFunction) {
			//noinspection unchecked
			return new DispatchedMap((Function<Object, ? extends Codec<Object>>) valueCodecFunction);
		}
	}

	public record Recursive(Supplier<AbstractCodecResult> wrapped) implements ContainerData {
		public static <T> Recursive create(Supplier<Codec<T>> wrapped, HolderLookup.Provider lookup) {
			return new Recursive(() -> CodecParser.parse(wrapped.get(), lookup));
		}
		public static <T> Recursive createMap(Supplier<MapCodec<T>> wrapped, HolderLookup.Provider lookup) {
			return new Recursive(() -> CodecParser.parse(wrapped.get(), lookup));
		}
	}
}
