package nu.metacraft.lib.config.describe;

import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A config record described by {@link Config} and {@link Option}: its options, its {@code DEFAULT},
 * its optional {@code public Optional<String> validate()}. Read once per class; a description that
 * cannot work throws {@link ConfigSpecException} naming the record and component.
 */
public final class ConfigSpec<R extends Record> {
	private static final Map<Class<?>, ConfigSpec<?>> SPECS = new ConcurrentHashMap<>();

	private final Class<R> type;
	private final String name;
	private final String description;
	private final List<OptionSpec> options;
	private final R defaults;
	private final @Nullable Method validator;
	private final Constructor<R> constructor;
	private @Nullable DescribedCodec<R> codec;

	@SuppressWarnings("unchecked")
	public static <R extends Record> ConfigSpec<R> of(Class<R> type) {
		ConfigSpec<?> known = SPECS.get(type);
		if (known != null) return (ConfigSpec<R>) known;
		ConfigSpec<R> spec = new ConfigSpec<>(type);
		SPECS.put(type, spec);
		return spec;
	}

	private ConfigSpec(Class<R> type) {
		this.type = type;
		Config config = type.getAnnotation(Config.class);
		if (config == null) throw new ConfigSpecException(type.getSimpleName() + " has no @Config");
		this.name = config.name();
		this.description = config.description();
		RecordComponent[] components = type.getRecordComponents();
		List<OptionSpec> options = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		for (RecordComponent component : components) {
			OptionSpec option = describe(type, component);
			if (!keys.add(option.key())) {
				throw new ConfigSpecException(where(type, component) + ": the key \"" + option.key() + "\" is used twice");
			}
			options.add(option);
		}
		this.options = List.copyOf(options);
		this.defaults = readDefault(type);
		for (OptionSpec option : this.options) checkDefaultInRange(option);
		this.validator = findValidator(type);
		try {
			this.constructor = type.getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
			this.constructor.setAccessible(true);
		} catch (NoSuchMethodException e) {
			throw new ConfigSpecException(type.getSimpleName() + " has no canonical constructor");
		}
	}

	public Class<R> type() { return type; }
	public String name() { return name; }
	public String description() { return description; }
	public List<OptionSpec> options() { return options; }
	public R defaults() { return defaults; }

	public Optional<OptionSpec> option(String key) {
		return options.stream().filter(o -> o.key().equals(key)).findFirst();
	}

	/** The file format generated from the options. Throws if a component has a type it cannot write. */
	public DescribedCodec<R> codec() {
		if (codec == null) codec = new DescribedCodec<>(this);
		return codec;
	}

	/**
	 * For a config with a hand-written codec: every described key must appear when {@code DEFAULT} is
	 * written, or the description and the file have drifted apart.
	 */
	public void checkWrittenBy(com.mojang.serialization.Codec<R> written) {
		var json = written.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, defaults).getOrThrow(
				message -> new ConfigSpecException(name + ": DEFAULT cannot be written: " + message));
		if (!json.isJsonObject()) throw new ConfigSpecException(name + ": the codec does not write an object");
		for (OptionSpec option : options) {
			boolean absentOptional = option.optional() && ((Optional<?>) option.read(defaults)).isEmpty();
			if (!absentOptional && !json.getAsJsonObject().has(option.key())) {
				throw new ConfigSpecException(option.name() + ": the codec writes no \"" + option.key() + "\"");
			}
		}
	}

	/** The record's own rule, and its sections' rules; empty when the value is fine. */
	public Optional<String> validate(R value) {
		for (OptionSpec option : options) {
			if (option.kind() == OptionKind.SECTION) {
				Object section = option.read(value);
				if (section instanceof Optional<?> opt) section = opt.orElse(null);
				if (section != null) {
					Optional<String> error = validateSection(option.section(), (Record) section);
					if (error.isPresent()) return error;
				}
			}
		}
		if (validator == null) return Optional.empty();
		try {
			@SuppressWarnings("unchecked") Optional<String> result = (Optional<String>) validator.invoke(value);
			return result;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(type.getSimpleName() + ".validate() failed", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <S extends Record> Optional<String> validateSection(ConfigSpec<S> spec, Record value) {
		return spec.validate((S) value);
	}

	public R construct(Object[] values) {
		try {
			return constructor.newInstance(values);
		} catch (InvocationTargetException e) {
			throw new IllegalArgumentException(e.getCause().getMessage(), e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot build " + type.getSimpleName(), e);
		}
	}

	private static String where(Class<?> type, RecordComponent component) {
		return type.getSimpleName() + "." + component.getName();
	}

	static String snakeCase(String name) {
		return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
	}

	private static OptionSpec describe(Class<?> type, RecordComponent component) {
		Option option = component.getAnnotation(Option.class);
		if (option == null) throw new ConfigSpecException(where(type, component) + " has no @Option");
		if (option.min() > option.max()) {
			throw new ConfigSpecException(where(type, component) + ": min " + option.min() + " is above max " + option.max());
		}
		if (option.step() < 0) throw new ConfigSpecException(where(type, component) + ": step is negative");
		String key = option.key().isEmpty() ? snakeCase(component.getName()) : option.key();

		Type generic = component.getGenericType();
		boolean optional = false;
		if (generic instanceof ParameterizedType p && p.getRawType() == Optional.class) {
			optional = true;
			generic = p.getActualTypeArguments()[0];
		}
		Class<?> raw = rawClass(generic);
		OptionKind kind = kindOf(generic, raw);
		if (optional && (kind == OptionKind.TEXT_LIST || kind == OptionKind.IDENTIFIER_LIST || kind == OptionKind.SECTION)) {
			kind = OptionKind.READ_ONLY;   // an optional list or section: edit in the file
		}
		List<String> choices = kind == OptionKind.CHOICE
				? Arrays.stream(raw.getEnumConstants()).map(c -> ((StringRepresentable) c).getSerializedName()).toList()
				: List.of();
		@SuppressWarnings({"unchecked", "rawtypes"})
		ConfigSpec<?> section = kind == OptionKind.SECTION ? ConfigSpec.of((Class) raw) : null;
		return new OptionSpec(key, where(type, component), option.description(), kind, raw, optional,
				option.min(), option.max(), option.step(), option.restart(), choices, section, component);
	}

	private static Class<?> rawClass(Type type) {
		if (type instanceof Class<?> c) return c;
		if (type instanceof ParameterizedType p) return (Class<?>) p.getRawType();
		return Object.class;
	}

	private static OptionKind kindOf(Type generic, Class<?> raw) {
		if (raw == boolean.class || raw == Boolean.class) return OptionKind.BOOLEAN;
		if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class) return OptionKind.WHOLE;
		if (raw == double.class || raw == Double.class || raw == float.class || raw == Float.class) return OptionKind.DECIMAL;
		if (raw == String.class) return OptionKind.TEXT;
		if (raw == Identifier.class) return OptionKind.IDENTIFIER;
		if (raw.isEnum() && StringRepresentable.class.isAssignableFrom(raw)) return OptionKind.CHOICE;
		if (raw.isRecord() && raw.isAnnotationPresent(Config.class)) return OptionKind.SECTION;
		if (raw == List.class && generic instanceof ParameterizedType p) {
			Type element = p.getActualTypeArguments()[0];
			if (element == String.class) return OptionKind.TEXT_LIST;
			if (element == Identifier.class) return OptionKind.IDENTIFIER_LIST;
		}
		return OptionKind.READ_ONLY;
	}

	private static <R> R readDefault(Class<R> type) {
		try {
			Field field = type.getDeclaredField("DEFAULT");
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != type) {
				throw new ConfigSpecException(type.getSimpleName() + ".DEFAULT must be static and of type " + type.getSimpleName());
			}
			field.setAccessible(true);
			Object value = field.get(null);
			if (value == null) throw new ConfigSpecException(type.getSimpleName() + ".DEFAULT is null");
			return type.cast(value);
		} catch (NoSuchFieldException e) {
			throw new ConfigSpecException(type.getSimpleName() + " has no static DEFAULT");
		} catch (IllegalAccessException e) {
			throw new ConfigSpecException(type.getSimpleName() + ".DEFAULT cannot be read");
		}
	}

	private void checkDefaultInRange(OptionSpec option) {
		if (option.kind() != OptionKind.WHOLE && option.kind() != OptionKind.DECIMAL) return;
		Object value = option.read(defaults);
		if (value instanceof Optional<?> opt) value = opt.orElse(null);
		if (value == null) return;
		double v = ((Number) value).doubleValue();
		if (v < option.min() || v > option.max()) {
			throw new ConfigSpecException(option.name() + ": DEFAULT's " + OptionSpec.number(v) + " is outside " + option.rangeText());
		}
	}

	private static @Nullable Method findValidator(Class<?> type) {
		try {
			Method method = type.getMethod("validate");
			if (method.getReturnType() != Optional.class || Modifier.isStatic(method.getModifiers())) {
				throw new ConfigSpecException(type.getSimpleName() + ".validate() must be an instance method returning Optional<String>");
			}
			return method;
		} catch (NoSuchMethodException e) {
			return null;
		}
	}
}
