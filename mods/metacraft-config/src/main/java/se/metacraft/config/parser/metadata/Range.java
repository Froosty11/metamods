package se.metacraft.config.parser.metadata;

import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;

public record Range(Number min, Number max) implements Metadata {
	public static Range range(Number lhs, Number rhs) {
		Number min = lhs.doubleValue() < rhs.doubleValue() ? lhs : rhs;
		Number max = lhs.equals(min) ? rhs : lhs;
		return new Range(min, max);
	}

	public double difference() {
		return max.doubleValue() - min.doubleValue();
	}

	@Override
	public MetadataKey<Range> key() {
		return MetadataKey.RANGE;
	}
}
