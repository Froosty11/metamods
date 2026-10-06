package se.metacraft.config.parser;

import se.metacraft.config.parser.metadata.*;

public interface MetadataKey<T> {
	MetadataKey<Container<?>> CONTAINER = new MetadataKey<>() {};
	MetadataKey<Remainder> REMAINDER = new MetadataKey<>() {};
	MetadataKey<Range> RANGE = new MetadataKey<>() {};
	MetadataKey<Entries> ENTRIES = new MetadataKey<>() {};
	MetadataKey<DefaultValue> DEFAULT_VALUE = new MetadataKey<>() {};
	MetadataKey<NamedField> NAMED_FIELD = new MetadataKey<>() {};
	MetadataKey<Comments> COMMENTS = new MetadataKey<>() {};
}
