package se.metacraft.config.parser.metadata;

import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;

public record DefaultValue(Object value) implements Metadata {
	@Override
	public MetadataKey<DefaultValue> key() {
		return MetadataKey.DEFAULT_VALUE;
	}
}
