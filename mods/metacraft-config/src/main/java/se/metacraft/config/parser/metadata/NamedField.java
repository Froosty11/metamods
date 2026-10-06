package se.metacraft.config.parser.metadata;

import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;

public record NamedField(String name, boolean required) implements Metadata {
	@Override
	public MetadataKey<NamedField> key() {
		return MetadataKey.NAMED_FIELD;
	}
}
