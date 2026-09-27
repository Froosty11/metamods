package se.metacraft.config.parser.result;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.MetadataMap;
import se.metacraft.config.parser.metadata.Remainder;

import java.util.Optional;

public record MapCodecResult(MapCodec<?> mapCodecInternal, MetadataMap metadata, AbstractCodecResult underlying, PVector<AbstractCodecResult> components) implements AbstractCodecResult {

	public static MapCodecResult createWithComponents(MapCodec<?> codec, MetadataMap metadata, PVector<AbstractCodecResult> components) {
		return new MapCodecResult(codec, metadata, AbstractCodecResult.EMPTY, components);
	}

	public static MapCodecResult createMapped(MapCodec<?> codec, MetadataMap metadata, AbstractCodecResult underlying) {
		return new MapCodecResult(codec, metadata, underlying, TreePVector.empty());
	}

	@Override
	public Codec<?> codec() {
		return mapCodecInternal.codec();
	}

	@Override
	public Optional<MapCodec<?>> mapCodec() {
		return Optional.of(mapCodecInternal);
	}
}
