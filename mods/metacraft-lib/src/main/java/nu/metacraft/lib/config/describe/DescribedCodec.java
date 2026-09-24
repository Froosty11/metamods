package nu.metacraft.lib.config.describe;

import com.mojang.serialization.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.*;
import java.util.stream.Stream;

/**
 * The file format of a described record, walking its components (so there is no field limit):
 * snake_case keys, a missing key meaning the value in {@code DEFAULT}, an empty {@code Optional}
 * left out, and a {@code _help} object with every description, ignored on read.
 */
public final class DescribedCodec<R extends Record> extends MapCodec<R> {
	public static final String HELP = "_help";

	private final ConfigSpec<R> spec;
	private final List<Codec<Object>> codecs = new ArrayList<>();
	private final Map<String, String> help = new LinkedHashMap<>();

	DescribedCodec(ConfigSpec<R> spec) {
		this.spec = spec;
		if (!spec.description().isEmpty()) help.put("_about", spec.description());
		for (OptionSpec option : spec.options()) {
			if (option.kind() == OptionKind.READ_ONLY) {
				throw new ConfigSpecException(option.name() + " has a type the generated format cannot write ("
						+ option.component().getGenericType().getTypeName() + "); give the config a hand-written codec");
			}
			codecs.add(valueCodec(option));
			help.put(option.key(), option.description());
		}
	}

	@Override
	public <T> Stream<T> keys(DynamicOps<T> ops) {
		return Stream.concat(Stream.of(HELP), spec.options().stream().map(OptionSpec::key)).map(ops::createString);
	}

	@Override
	public <T> DataResult<R> decode(DynamicOps<T> ops, MapLike<T> input) {
		Object[] values = new Object[spec.options().size()];
		for (int i = 0; i < values.length; i++) {
			OptionSpec option = spec.options().get(i);
			T raw = input.get(option.key());
			if (raw == null) {
				values[i] = option.read(spec.defaults());
				continue;
			}
			DataResult<Object> read = codecs.get(i).parse(ops, raw);
			if (read.error().isPresent()) {
				String message = read.error().get().message();
				return DataResult.error(() -> option.key() + ": " + message);
			}
			Object value = read.getOrThrow();
			values[i] = option.optional() ? Optional.of(value) : value;
		}
		try {
			return DataResult.success(spec.construct(values));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> spec.name() + ": " + e.getMessage());
		}
	}

	@Override
	public <T> RecordBuilder<T> encode(R input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
		prefix.add(HELP, Codec.unboundedMap(Codec.STRING, Codec.STRING).encodeStart(ops, help));
		for (int i = 0; i < spec.options().size(); i++) {
			OptionSpec option = spec.options().get(i);
			Object value = option.read(input);
			if (option.optional()) {
				Optional<?> present = (Optional<?>) value;
				if (present.isEmpty()) continue;
				value = present.get();
			}
			prefix.add(option.key(), codecs.get(i).encodeStart(ops, value));
		}
		return prefix;
	}

	/** The codec of one option's value (inside its {@code Optional}), ranges enforced. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static Codec<Object> valueCodec(OptionSpec option) {
		Class<?> type = option.valueType();
		Codec<?> codec = switch (option.kind()) {
			case BOOLEAN -> Codec.BOOL;
			case WHOLE -> (type == long.class || type == Long.class)
					? Codec.LONG.validate(v -> inRange(option, v))
					: Codec.INT.validate(v -> inRange(option, v));
			case DECIMAL -> (type == float.class || type == Float.class)
					? Codec.FLOAT.validate(v -> inRange(option, v))
					: Codec.DOUBLE.validate(v -> inRange(option, v));
			case TEXT -> Codec.STRING;
			case IDENTIFIER -> Identifier.CODEC;
			case CHOICE -> StringRepresentable.fromEnum((java.util.function.Supplier) () -> type.getEnumConstants());
			case TEXT_LIST -> Codec.STRING.listOf();
			case IDENTIFIER_LIST -> Identifier.CODEC.listOf();
			case SECTION -> ConfigSpec.of((Class) type).codec().codec();
			case READ_ONLY -> throw new ConfigSpecException(option.name() + " cannot be written");
		};
		return (Codec<Object>) codec;
	}

	private static <N extends Number> DataResult<N> inRange(OptionSpec option, N value) {
		double v = value.doubleValue();
		if (Double.isNaN(v) || v < option.min() || v > option.max()) {
			return DataResult.error(() -> OptionSpec.number(v) + " is not in " + option.rangeText());
		}
		return DataResult.success(value);
	}
}
