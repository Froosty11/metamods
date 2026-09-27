package se.metacraft.config.parser.metadata;

import org.pcollections.PVector;
import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;

public record Remainder(PVector<Object> parameters) implements Metadata {

	@Override
	public MetadataKey<Remainder> key() {
		return MetadataKey.REMAINDER;
	}

}
