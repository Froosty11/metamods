package se.metacraft.config.parser.result;

import com.mojang.serialization.Codec;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.MetadataMap;
import se.metacraft.config.parser.metadata.Remainder;

public record CodecResult(Codec<?> codec, MetadataMap metadata, AbstractCodecResult underlying, PVector<AbstractCodecResult> components) implements AbstractCodecResult {

	public static CodecResult createWithComponents(Codec<?> codec, MetadataMap metadata, PVector<AbstractCodecResult> components) {
		return new CodecResult(codec, metadata, AbstractCodecResult.EMPTY, components);
	}

	public static CodecResult createMapped(Codec<?> codec, MetadataMap metadata, AbstractCodecResult underlying) {
		return new CodecResult(codec, metadata, underlying, TreePVector.empty());
	}
}
