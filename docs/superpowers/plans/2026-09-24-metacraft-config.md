# metacraft-config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Operators edit mod configs in game through a server-side vanilla dialog (`/config`); configs describe their options with annotations on records, via metacraft-lib.

**Architecture:** metacraft-lib gains a `config.describe` package (annotations → `ConfigSpec` → generated codec, text edits applied through the codec) and container changes (load errors kept, `update`, change listeners, a registry of described configs). A new mod `mods/metacraft-config` reads described configs and adapters through one `ConfigSource` interface, turns a source's page into a vanilla `MultiActionDialog`, and handles the dialog's buttons through metacraft-lib's existing custom-message registry. faster-minecarts is converted as the proof; a PolyDecorations adapter covers the fork's feature switches.

**Tech Stack:** Java 25, Minecraft 26.3 (Mojang names, unobfuscated), Fabric Loom, Fabric API, DFU codecs, vanilla dialogs (`net.minecraft.server.dialog`), fabric-permissions-api, JUnit 5 via fabric-loader-junit, Fabric client game tests.

**Spec:** `docs/superpowers/specs/2026-09-24-metacraft-config-design.md`

## Global Constraints

- Branch `metacraft-config` in `.claude/worktrees/metacraft-config`, based on `upstream/dev` 29a038a7. Nothing pushed without the owner's go-ahead.
- Run `./gradlew runDatagen` once before the first build or test in a fresh worktree (generated sources are gitignored; tests crash with exit 255 without them).
- Tabs for indentation; `nu.metacraft.*` packages; match the surrounding code's comment density.
- Configs are **records**; every component of a described record carries `@Option`.
- `@Option` defaults: `min = -Infinity`, `max = Infinity`, `step = 0`, `restart = false`, `key = ""` (snake_case of the component name).
- A number is a slider only when bounded, not optional, and at most **50** steps (`step` defaults to 1 for whole numbers; a decimal without `step` is always typed). Everything else numeric is a text input labelled with its range.
- A file that does not load is never overwritten or backed up; the last good value (or `DEFAULT`) stays in use and the error is kept on the container.
- Permission node `metacraft.config`, default level **3**.
- Existing callers of `ConfigContainer` compile and behave as before; `modify`/`Modifiable` are `@Deprecated`, not removed.
- faster-minecarts' file keys and accepted ranges do not change.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

**One deviation from the spec, deliberate:** change listeners fire for every change, including `restart = true` options; `restart` only marks the option in the screen and in the pending-restart list. (Mods that read an option only at startup ignore later values anyway; suppressing the event would need the container to know the spec.)

## Review Focus

1. Numbers typed with a comma (`1,5`), spaces, or empty: a clear per-option error, never an exception; spaces are trimmed. → Task 3 tests.
2. The file edited by hand (or by another operator) between opening a page and saving: the save is refused as stale, nothing written. → Task 5 test.
3. Saving from the screen while the file has a load error: allowed (the screen is how you fix it without SFTP), writes a valid file and clears the error. → Task 4 test.
4. Saving a nested page leaves every option outside that page exactly as it was. → Task 3 test.
5. A dialog payload with an unknown choice, a missing input, or a non-numeric value where a number is expected (a modified client): refused with a message, never an exception. → Task 9 test.

---

## File Structure

metacraft-lib (`mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/`):
- `describe/Config.java`, `describe/Option.java` — the annotations.
- `describe/OptionKind.java` — what an option is edited as.
- `describe/OptionSpec.java` — one described component.
- `describe/ConfigSpec.java` — a described record: options, defaults, validation, cached per class.
- `describe/ConfigSpecException.java` — registration failures.
- `describe/DescribedCodec.java` — the generated `MapCodec`.
- `describe/ConfigEdits.java` — applies typed text to a page, through the codec.
- `describe/DescribedConfig.java` — a registered, described config backed by a container.
- `describe/ConfigRegistry.java` — every described config.
- `JsonHelper.java` (modify) — `read` returning the parse error.
- `container/ConfigContainerBase.java`, `container/ConfigContainer.java`, `container/impl/BasicConfigContainer.java`, `extensions/Modifiable.java` (modify).

Tests: `mods/metacraft-lib/src/test/java/` — `TestConfigSpec.java`, `TestDescribedCodec.java`, `TestConfigEdits.java`, `TestContainerLoading.java`, `TestDescribedConfig.java`, `fixtures/*.java` (test records).

faster-minecarts: `FasterMinecartsConfig.java` (modify), `FasterMinecarts*.java` (callers, only if needed), `src/test/java/TestFasterMinecartsConfig.java`.

metacraft-config (`mods/metacraft-config/`, package `nu.metacraft.config`):
- `MetacraftConfig.java` — entrypoint: handlers, command, adapters, join notice.
- `source/ConfigSource.java`, `source/Page.java`, `source/Field.java`, `source/Link.java`, `source/EditOutcome.java` — the model the screen draws.
- `source/DescribedSource.java` — `DescribedConfig` → `ConfigSource`.
- `source/Sources.java` — all sources.
- `source/PolyDecorationsSource.java` — the fork's `config/polydecorations.json`.
- `screen/Inputs.java` — `Field` → dialog input.
- `screen/Pages.java` — main page, config page, confirm page as `Dialog`s.
- `screen/Payloads.java` — dialog payload → typed values per field.
- `screen/Actions.java` — the message handlers (open, save, reset) and opening pages for a player.
- `ConfigCommand.java` — `/config`.
- Tests in `src/test/java/`; client screenshots in `src/gametest/`.

---

### Task 1: Annotations and `ConfigSpec`

**Files:**
- Create: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/describe/{Config,Option,OptionKind,OptionSpec,ConfigSpec,ConfigSpecException}.java`
- Test: `mods/metacraft-lib/src/test/java/TestConfigSpec.java`, `mods/metacraft-lib/src/test/java/fixtures/{Sample,SampleSection,BadRange,NoDefault,Unannotated,WithList}.java`

**Interfaces:**
- Produces: `ConfigSpec.of(Class<R>)`, `spec.type()`, `spec.name()`, `spec.description()`, `spec.options()`, `spec.option(String key) : Optional<OptionSpec>`, `spec.defaults() : R`, `spec.validate(R) : Optional<String>`, `spec.construct(Object[]) : R`; `OptionSpec(key, name, description, kind, valueType, optional, min, max, step, restart, choices, section)` with `read(Record)`, `effectiveStep()`, `slider()`, `rangeText()`, `editable()`; `OptionKind {BOOLEAN, WHOLE, DECIMAL, TEXT, IDENTIFIER, CHOICE, TEXT_LIST, IDENTIFIER_LIST, SECTION, READ_ONLY}`; `ConfigSpecException extends RuntimeException`.

- [ ] **Step 1: Write the test fixtures**

`fixtures/SampleSection.java`:
```java
package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "Store", description = "Where things are kept.")
public record SampleSection(
		@Option(description = "Address of the store.") String url,
		@Option(description = "Seconds between syncs.", min = 1, max = 60) int interval
) {
	public static final SampleSection DEFAULT = new SampleSection("", 10);
}
```

`fixtures/Sample.java`:
```java
package fixtures;

import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

import java.util.List;
import java.util.Optional;

@Config(name = "Sample", description = "A config for tests.")
public record Sample(
		@Option(description = "Turned on.") boolean enabled,
		@Option(description = "How many.", min = 1, max = 16) int count,
		@Option(description = "How fast.", min = 0) double speed,
		@Option(description = "Share, in steps of 0.05.", min = 0, max = 1, step = 0.05) double share,
		@Option(description = "Optional limit.", min = 0) Optional<Double> limit,
		@Option(description = "A name.") String title,
		@Option(description = "A block.") Identifier block,
		@Option(description = "Mode.", restart = true) Mode mode,
		@Option(description = "Words, one per line.") List<String> words,
		@Option(description = "The store.") SampleSection store,
		@Option(description = "Renamed in the file.", key = "old-name") int renamed
) {
	public static final Sample DEFAULT = new Sample(true, 6, 60, 0.5, Optional.empty(), "hi",
			Identifier.withDefaultNamespace("stone"), Mode.FAST, List.of("a"), SampleSection.DEFAULT, 3);

	public Optional<String> validate() {
		return title.equals("forbidden") ? Optional.of("That title is not allowed.") : Optional.empty();
	}

	public enum Mode implements StringRepresentable {
		FAST("fast"), SLOW("slow");
		private final String name;
		Mode(String name) { this.name = name; }
		@Override public String getSerializedName() { return name; }
	}
}
```

`fixtures/BadRange.java`:
```java
package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "Bad range")
public record BadRange(@Option(description = "x", min = 5, max = 1) int x) {
	public static final BadRange DEFAULT = new BadRange(3);
}
```

`fixtures/NoDefault.java`:
```java
package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "No default")
public record NoDefault(@Option(description = "x") int x) {}
```

`fixtures/Unannotated.java`:
```java
package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "Unannotated")
public record Unannotated(@Option(description = "x") int x, int y) {
	public static final Unannotated DEFAULT = new Unannotated(1, 2);
}
```

`fixtures/WithList.java` (a component the generated format cannot write):
```java
package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

import java.util.List;
import java.util.Map;

@Config(name = "With map")
public record WithList(@Option(description = "A map.") Map<String, Integer> weights) {
	public static final WithList DEFAULT = new WithList(Map.of());
}
```

- [ ] **Step 2: Write the failing test**

`TestConfigSpec.java`:
```java
import fixtures.*;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestConfigSpec {

	@Test
	public void readsEveryComponent() {
		ConfigSpec<Sample> spec = ConfigSpec.of(Sample.class);
		assertEquals("Sample", spec.name());
		assertEquals(List.of("enabled", "count", "speed", "share", "limit", "title", "block", "mode", "words", "store", "old-name"),
				spec.options().stream().map(OptionSpec::key).toList());
		assertEquals(OptionKind.BOOLEAN, spec.option("enabled").orElseThrow().kind());
		assertEquals(OptionKind.WHOLE, spec.option("count").orElseThrow().kind());
		assertEquals(OptionKind.DECIMAL, spec.option("speed").orElseThrow().kind());
		OptionSpec limit = spec.option("limit").orElseThrow();
		assertEquals(OptionKind.DECIMAL, limit.kind());
		assertTrue(limit.optional());
		assertEquals(OptionKind.TEXT, spec.option("title").orElseThrow().kind());
		assertEquals(OptionKind.IDENTIFIER, spec.option("block").orElseThrow().kind());
		OptionSpec mode = spec.option("mode").orElseThrow();
		assertEquals(OptionKind.CHOICE, mode.kind());
		assertEquals(List.of("fast", "slow"), mode.choices());
		assertTrue(mode.restart());
		assertEquals(OptionKind.TEXT_LIST, spec.option("words").orElseThrow().kind());
		OptionSpec store = spec.option("store").orElseThrow();
		assertEquals(OptionKind.SECTION, store.kind());
		assertEquals("Store", store.section().name());
		assertSame(Sample.DEFAULT, spec.defaults());
	}

	@Test
	public void slidersOnlyForShortRanges() {
		ConfigSpec<Sample> spec = ConfigSpec.of(Sample.class);
		assertTrue(spec.option("count").orElseThrow().slider());   // 1-16, step 1
		assertTrue(spec.option("share").orElseThrow().slider());   // 0-1, step 0.05 = 20 steps
		assertFalse(spec.option("speed").orElseThrow().slider());  // unbounded
		assertFalse(spec.option("limit").orElseThrow().slider());  // optional
		assertEquals("(0 – ∞)", spec.option("speed").orElseThrow().rangeText());
		assertEquals("(1 – 16)", spec.option("count").orElseThrow().rangeText());
	}

	@Test
	public void validatesThroughTheRecord() {
		ConfigSpec<Sample> spec = ConfigSpec.of(Sample.class);
		assertEquals("That title is not allowed.", spec.validate(new Sample(true, 6, 60, 0.5, java.util.Optional.empty(),
				"forbidden", Sample.DEFAULT.block(), Sample.Mode.FAST, List.of(), SampleSection.DEFAULT, 3)).orElseThrow());
		assertTrue(spec.validate(Sample.DEFAULT).isEmpty());
	}

	@Test
	public void refusesBadDescriptions() {
		assertTrue(assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(BadRange.class)).getMessage().contains("BadRange.x"));
		assertTrue(assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(NoDefault.class)).getMessage().contains("DEFAULT"));
		assertTrue(assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(Unannotated.class)).getMessage().contains("Unannotated.y"));
	}

	@Test
	public void unsupportedTypesAreReadOnly() {
		assertEquals(OptionKind.READ_ONLY, ConfigSpec.of(WithList.class).option("weights").orElseThrow().kind());
	}
}
```

- [ ] **Step 3: Run the test to see it fail**

Run: `./gradlew :mods:metacraft-lib:test --tests TestConfigSpec`
Expected: compilation fails, `package nu.metacraft.lib.config.describe does not exist`.

- [ ] **Step 4: Write the annotations and kinds**

`Config.java`:
```java
package nu.metacraft.lib.config.describe;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a config record as described: its components carry {@link Option}, and it declares
 * {@code public static final <Type> DEFAULT}. See {@link ConfigSpec}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config {
	/** Shown as the config's title. */
	String name();
	String description() default "";
}
```

`Option.java`:
```java
package nu.metacraft.lib.config.describe;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** One option of a {@link Config} record: every component of a described record has one. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Option {
	/** Shown as the option's label, and written into the file's {@code _help}. */
	String description();
	double min() default Double.NEGATIVE_INFINITY;
	double max() default Double.POSITIVE_INFINITY;
	/** A slider's step. 0: 1 for whole numbers; a decimal without a step is always typed. */
	double step() default 0;
	/** The option only takes effect after a restart; the screen marks it and lists pending changes. */
	boolean restart() default false;
	/** The key in the file; empty means the component's name in snake_case. */
	String key() default "";
}
```

`OptionKind.java`:
```java
package nu.metacraft.lib.config.describe;

/** What an option is edited as. */
public enum OptionKind {
	BOOLEAN, WHOLE, DECIMAL, TEXT, IDENTIFIER, CHOICE, TEXT_LIST, IDENTIFIER_LIST,
	/** A nested {@link Config} record: its own page. */
	SECTION,
	/** A type the screen cannot edit; shown with "edit in the file". */
	READ_ONLY
}
```

`ConfigSpecException.java`:
```java
package nu.metacraft.lib.config.describe;

/** A described config that cannot be used as described; thrown at registration, naming the component. */
public class ConfigSpecException extends RuntimeException {
	public ConfigSpecException(String message) {
		super(message);
	}
}
```

- [ ] **Step 5: Write `OptionSpec`**

```java
package nu.metacraft.lib.config.describe;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.List;

/**
 * One described component. {@code valueType} is the component's class, or the class inside its
 * {@code Optional}; {@code section} is set for {@link OptionKind#SECTION}.
 */
public record OptionSpec(
		String key, String name, String description, OptionKind kind, Class<?> valueType, boolean optional,
		double min, double max, double step, boolean restart, List<String> choices,
		@Nullable ConfigSpec<?> section, RecordComponent component
) {
	/** Sliders take at most this many steps; longer ranges are typed. */
	public static final int MAX_SLIDER_STEPS = 50;

	public Object read(Record owner) {
		try {
			return component.getAccessor().invoke(owner);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot read " + name, e);
		}
	}

	public boolean editable() {
		return kind != OptionKind.READ_ONLY && kind != OptionKind.SECTION;
	}

	/** The slider step: {@code step}, or 1 for a whole number without one, or 0 (typed). */
	public double effectiveStep() {
		if (step > 0) return step;
		return kind == OptionKind.WHOLE ? 1 : 0;
	}

	public boolean slider() {
		if (kind != OptionKind.WHOLE && kind != OptionKind.DECIMAL) return false;
		if (optional || Double.isInfinite(min) || Double.isInfinite(max) || effectiveStep() <= 0) return false;
		return (max - min) / effectiveStep() <= MAX_SLIDER_STEPS;
	}

	public boolean bounded() {
		return !Double.isInfinite(min) || !Double.isInfinite(max);
	}

	/** "(0 – ∞)", or "" for an unbounded option. */
	public String rangeText() {
		if (!bounded()) return "";
		return "(" + number(min) + " – " + number(max) + ")";
	}

	public static String number(double value) {
		if (value == Double.POSITIVE_INFINITY) return "∞";
		if (value == Double.NEGATIVE_INFINITY) return "-∞";
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}
}
```

- [ ] **Step 6: Write `ConfigSpec`**

```java
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
```

Note: `ConfigSpec.codec()` returns `DescribedCodec`, created in Task 2. For this task, add a stub `DescribedCodec` so it compiles:
```java
package nu.metacraft.lib.config.describe;

public final class DescribedCodec<R extends Record> {
	DescribedCodec(ConfigSpec<R> spec) {}
}
```

- [ ] **Step 7: Run the test to see it pass**

Run: `./gradlew :mods:metacraft-lib:test --tests TestConfigSpec`
Expected: PASS (5 tests).

- [ ] **Step 8: Commit**

```bash
git add mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/describe mods/metacraft-lib/src/test/java/TestConfigSpec.java mods/metacraft-lib/src/test/java/fixtures
git commit -m "metacraft-lib: describe config records with @Config and @Option"
```

---

### Task 2: The generated file format (`DescribedCodec`)

**Files:**
- Modify: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/describe/DescribedCodec.java` (replace the stub)
- Modify: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/describe/ConfigSpec.java` (add `checkWrittenBy`)
- Test: `mods/metacraft-lib/src/test/java/TestDescribedCodec.java`

**Interfaces:**
- Consumes: `ConfigSpec`, `OptionSpec` (Task 1).
- Produces: `DescribedCodec<R> extends MapCodec<R>`; `DescribedCodec.valueCodec(OptionSpec) : Codec<Object>` (package-private static, reused by Task 3); `ConfigSpec.checkWrittenBy(Codec<R>)`.

- [ ] **Step 1: Write the failing test**

```java
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import fixtures.*;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestDescribedCodec {
	private static final Codec<Sample> CODEC = ConfigSpec.of(Sample.class).codec().codec();

	private static JsonObject encode(Sample value) {
		return CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject();
	}

	@Test
	public void writesSnakeCaseKeysAndHelp() {
		JsonObject json = encode(Sample.DEFAULT);
		assertTrue(json.get("enabled").getAsBoolean());
		assertEquals(6, json.get("count").getAsInt());
		assertEquals("fast", json.get("mode").getAsString());
		assertEquals("minecraft:stone", json.get("block").getAsString());
		assertEquals(10, json.getAsJsonObject("store").get("interval").getAsInt());
		assertEquals(3, json.get("old-name").getAsInt());
		assertFalse(json.has("limit"));   // an empty Optional is left out
		assertEquals("How many.", json.getAsJsonObject("_help").get("count").getAsString());
	}

	@Test
	public void roundTrips() {
		Sample changed = new Sample(false, 2, 12.5, 0.25, Optional.of(3.0), "yo", Sample.DEFAULT.block(),
				Sample.Mode.SLOW, List.of("x", "y"), new SampleSection("db://x", 30), 9);
		assertEquals(changed, CODEC.parse(JsonOps.INSTANCE, encode(changed)).getOrThrow());
	}

	@Test
	public void missingKeysAreDefaults() {
		Sample read = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"count\": 4}")).getOrThrow();
		assertEquals(4, read.count());
		assertEquals(Sample.DEFAULT.title(), read.title());
		assertEquals(Sample.DEFAULT.store(), read.store());
	}

	@Test
	public void refusesOutOfRangeAndNamesTheKey() {
		var result = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"count\": 40}"));
		assertTrue(result.error().isPresent());
		assertTrue(result.error().get().message().contains("count"), result.error().get().message());
	}

	@Test
	public void refusesUnknownChoice() {
		assertTrue(CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"mode\": \"warp\"}")).error().isPresent());
	}

	@Test
	public void cannotGenerateForUnsupportedTypes() {
		assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(WithList.class).codec());
	}

	@Test
	public void checksHandWrittenCodecsForMissingKeys() {
		MapCodec<SampleSection> handWritten = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
				Codec.INT.fieldOf("seconds").forGetter(SampleSection::interval)   // not "interval"
		).apply(i, SampleSection::new));
		var error = assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(SampleSection.class).checkWrittenBy(handWritten.codec()));
		assertTrue(error.getMessage().contains("interval"));
	}
}
```

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew :mods:metacraft-lib:test --tests TestDescribedCodec`
Expected: compilation fails (`codec()` returns the stub, which has no `codec()`).

- [ ] **Step 3: Write `DescribedCodec`**

```java
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
			case CHOICE -> StringRepresentable.fromEnum(() -> (Enum[]) type.getEnumConstants());
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
```

If `StringRepresentable.fromEnum` does not accept the raw supplier, use
`StringRepresentable.fromEnum((java.util.function.Supplier) () -> type.getEnumConstants())` — same raw cast.

- [ ] **Step 4: Add `checkWrittenBy` to `ConfigSpec`**

Add to `ConfigSpec`:
```java
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
```

- [ ] **Step 5: Run to see it pass**

Run: `./gradlew :mods:metacraft-lib:test --tests TestDescribedCodec --tests TestConfigSpec`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add mods/metacraft-lib/src
git commit -m "metacraft-lib: generate a described config's file format"
```

---

### Task 3: Applying typed edits (`ConfigEdits`)

**Files:**
- Create: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/describe/ConfigEdits.java`
- Test: `mods/metacraft-lib/src/test/java/TestConfigEdits.java`

**Interfaces:**
- Consumes: `ConfigSpec`, `OptionSpec`, `DescribedCodec` (Tasks 1–2).
- Produces:
  - `ConfigEdits.apply(ConfigSpec<R> spec, Codec<R> codec, R current, List<String> page, Map<String, String> values, DynamicOps<JsonElement> ops) : DataResult<R>` — `page` is the path of section keys from the top (`List.of()` for the top page); `values` maps option keys on that page to text as typed (`"true"`/`"false"` for booleans, a choice's serialized name, lines for lists, `""` for empty).
  - `ConfigEdits.reset(ConfigSpec<R>, Codec<R>, R current, List<String> page, DynamicOps<JsonElement>) : DataResult<R>`
  - `ConfigEdits.pageSpec(ConfigSpec<?> top, List<String> page) : ConfigSpec<?>` (throws `IllegalArgumentException` for an unknown path)
  - `ConfigEdits.text(OptionSpec, Object value) : String` — the text form of a value, for inputs.

- [ ] **Step 1: Write the failing test**

```java
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import fixtures.*;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestConfigEdits {
	private static final ConfigSpec<Sample> SPEC = ConfigSpec.of(Sample.class);
	private static final Codec<Sample> CODEC = SPEC.codec().codec();

	private static com.mojang.serialization.DataResult<Sample> edit(List<String> page, Map<String, String> values) {
		return ConfigEdits.apply(SPEC, CODEC, Sample.DEFAULT, page, values, JsonOps.INSTANCE);
	}

	private static String error(Map<String, String> values) {
		return edit(List.of(), values).error().orElseThrow().message();
	}

	@Test
	public void appliesEveryKind() {
		Sample s = edit(List.of(), Map.of(
				"enabled", "false", "count", " 12 ", "speed", "7.5", "limit", "2", "title", "new",
				"block", "minecraft:dirt", "mode", "slow", "words", "one\n\n two \n")).getOrThrow();
		assertFalse(s.enabled());
		assertEquals(12, s.count());
		assertEquals(7.5, s.speed());
		assertEquals(Optional.of(2.0), s.limit());
		assertEquals("new", s.title());
		assertEquals("minecraft:dirt", s.block().toString());
		assertEquals(Sample.Mode.SLOW, s.mode());
		assertEquals(List.of("one", "two"), s.words());
		assertEquals(Sample.DEFAULT.store(), s.store());
	}

	@Test
	public void blankOptionalIsAbsent() {
		Sample withLimit = edit(List.of(), Map.of("limit", "5")).getOrThrow();
		Sample cleared = ConfigEdits.apply(SPEC, CODEC, withLimit, List.of(), Map.of("limit", "  "), JsonOps.INSTANCE).getOrThrow();
		assertEquals(Optional.empty(), cleared.limit());
	}

	@Test
	public void numbersWithACommaOrLettersAreRefused() {
		assertTrue(error(Map.of("speed", "1,5")).contains("speed"));
		assertTrue(error(Map.of("speed", "1,5")).contains("."));   // suggests a point
		assertTrue(error(Map.of("count", "lots")).contains("whole number"));
		assertTrue(error(Map.of("count", "")).contains("needs a value"));
		assertTrue(error(Map.of("count", "2.5")).contains("whole number"));
	}

	@Test
	public void outOfRangeAndUnknownChoiceAreRefused() {
		assertTrue(error(Map.of("count", "99")).contains("count"));
		assertTrue(error(Map.of("mode", "warp")).contains("mode"));
		assertTrue(error(Map.of("block", "Not An Id")).contains("block"));
		assertTrue(error(Map.of("enabled", "maybe")).contains("enabled"));
	}

	@Test
	public void unknownAndReadOnlyKeysAreRefused() {
		assertTrue(error(Map.of("nope", "1")).contains("nope"));
		assertTrue(error(Map.of("store", "x")).contains("store"));
	}

	@Test
	public void recordValidationRuns() {
		assertEquals("That title is not allowed.", error(Map.of("title", "forbidden")));
	}

	@Test
	public void nestedPageLeavesEverythingElse() {
		Sample start = edit(List.of(), Map.of("count", "9", "title", "kept")).getOrThrow();
		Sample s = ConfigEdits.apply(SPEC, CODEC, start, List.of("store"), Map.of("url", "db://y", "interval", "20"), JsonOps.INSTANCE).getOrThrow();
		assertEquals(new SampleSection("db://y", 20), s.store());
		assertEquals(9, s.count());
		assertEquals("kept", s.title());
	}

	@Test
	public void resetPutsOnePageBack() {
		Sample start = ConfigEdits.apply(SPEC, CODEC, Sample.DEFAULT, List.of("store"), Map.of("url", "db://z"), JsonOps.INSTANCE).getOrThrow();
		Sample changedTop = ConfigEdits.apply(SPEC, CODEC, start, List.of(), Map.of("count", "2"), JsonOps.INSTANCE).getOrThrow();
		Sample reset = ConfigEdits.reset(SPEC, CODEC, changedTop, List.of("store"), JsonOps.INSTANCE).getOrThrow();
		assertEquals(SampleSection.DEFAULT, reset.store());
		assertEquals(2, reset.count());
	}

	@Test
	public void textForms() {
		assertEquals("60", ConfigEdits.text(SPEC.option("speed").orElseThrow(), 60.0));
		assertEquals("0.5", ConfigEdits.text(SPEC.option("share").orElseThrow(), 0.5));
		assertEquals("", ConfigEdits.text(SPEC.option("limit").orElseThrow(), Optional.empty()));
		assertEquals("a\nb", ConfigEdits.text(SPEC.option("words").orElseThrow(), List.of("a", "b")));
		assertEquals("fast", ConfigEdits.text(SPEC.option("mode").orElseThrow(), Sample.Mode.FAST));
	}
}
```

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew :mods:metacraft-lib:test --tests TestConfigEdits`
Expected: compilation fails, `cannot find symbol ConfigEdits`.

- [ ] **Step 3: Write `ConfigEdits`**

```java
package nu.metacraft.lib.config.describe;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.*;

/**
 * Edits a described config the way the screen does: the current value is written out, the typed
 * text is put into that JSON, and the result is read back with the config's own codec, then its
 * {@code validate()}. So an edit can only produce what the file could hold.
 */
public final class ConfigEdits {
	private ConfigEdits() {}

	public static <R extends Record> DataResult<R> apply(
			ConfigSpec<R> spec, Codec<R> codec, R current, List<String> page, Map<String, String> values,
			DynamicOps<JsonElement> ops
	) {
		ConfigSpec<?> pageSpec;
		try {
			pageSpec = pageSpec(spec, page);
		} catch (IllegalArgumentException e) {
			return DataResult.error(e::getMessage);
		}
		JsonObject root = codec.encodeStart(ops, current).getOrThrow().getAsJsonObject();
		JsonObject object = navigate(root, page);
		for (Map.Entry<String, String> entry : values.entrySet()) {
			Optional<OptionSpec> option = pageSpec.option(entry.getKey());
			if (option.isEmpty() || !option.get().editable()) {
				return DataResult.error(() -> entry.getKey() + ": not an option that can be edited here");
			}
			Optional<String> problem = put(object, option.get(), entry.getValue());
			if (problem.isPresent()) {
				String message = option.get().description() + " — " + problem.get();
				return DataResult.error(() -> message);
			}
		}
		return readBack(spec, codec, root, ops);
	}

	public static <R extends Record> DataResult<R> reset(
			ConfigSpec<R> spec, Codec<R> codec, R current, List<String> page, DynamicOps<JsonElement> ops
	) {
		JsonObject root = codec.encodeStart(ops, current).getOrThrow().getAsJsonObject();
		if (page.isEmpty()) {
			return DataResult.success(spec.defaults());
		}
		ConfigSpec<?> pageSpec = pageSpec(spec, page);
		JsonObject parent = navigate(root, page.subList(0, page.size() - 1));
		parent.add(page.getLast(), encodeDefaults(pageSpec, ops));
		return readBack(spec, codec, root, ops);
	}

	private static <S extends Record> JsonElement encodeDefaults(ConfigSpec<S> spec, DynamicOps<JsonElement> ops) {
		return spec.codec().codec().encodeStart(ops, spec.defaults()).getOrThrow();
	}

	private static <R extends Record> DataResult<R> readBack(ConfigSpec<R> spec, Codec<R> codec, JsonObject root, DynamicOps<JsonElement> ops) {
		DataResult<R> read = codec.parse(ops, root);
		if (read.error().isPresent()) {
			String message = read.error().get().message();
			return DataResult.error(() -> message);
		}
		R value = read.getOrThrow();
		Optional<String> invalid = spec.validate(value);
		if (invalid.isPresent()) return DataResult.error(invalid::get);
		return DataResult.success(value);
	}

	public static ConfigSpec<?> pageSpec(ConfigSpec<?> top, List<String> page) {
		ConfigSpec<?> spec = top;
		for (String key : page) {
			OptionSpec option = spec.option(key).orElseThrow(() -> new IllegalArgumentException("no page " + String.join("/", page)));
			if (option.kind() != OptionKind.SECTION) throw new IllegalArgumentException(key + " is not a page");
			spec = option.section();
		}
		return spec;
	}

	private static JsonObject navigate(JsonObject root, List<String> page) {
		JsonObject object = root;
		for (String key : page) {
			if (!object.has(key) || !object.get(key).isJsonObject()) object.add(key, new JsonObject());
			object = object.getAsJsonObject(key);
		}
		return object;
	}

	/** Puts the typed text into the JSON; a message when it is not a value of the option's kind. */
	private static Optional<String> put(JsonObject object, OptionSpec option, String typed) {
		String text = option.kind() == OptionKind.TEXT ? typed : typed.trim();
		if (text.isEmpty() && option.optional()) {
			object.remove(option.key());
			return Optional.empty();
		}
		switch (option.kind()) {
			case BOOLEAN -> {
				if (!text.equals("true") && !text.equals("false")) return Optional.of("\"" + text + "\" is not true or false");
				object.addProperty(option.key(), Boolean.parseBoolean(text));
			}
			case WHOLE -> {
				if (text.isEmpty()) return Optional.of("needs a value");
				try {
					object.addProperty(option.key(), Long.parseLong(text));
				} catch (NumberFormatException e) {
					return Optional.of("\"" + text + "\" is not a whole number");
				}
			}
			case DECIMAL -> {
				if (text.isEmpty()) return Optional.of("needs a value");
				try {
					double value = Double.parseDouble(text);
					if (Double.isNaN(value) || Double.isInfinite(value)) return Optional.of("\"" + text + "\" is not a number");
					object.addProperty(option.key(), value);
				} catch (NumberFormatException e) {
					return Optional.of(text.contains(",")
							? "\"" + text + "\" is not a number; use a point for decimals, like 1.5"
							: "\"" + text + "\" is not a number");
				}
			}
			case TEXT -> object.addProperty(option.key(), text);
			case IDENTIFIER -> {
				if (Identifier.tryParse(text) == null || text.isEmpty()) return Optional.of("\"" + text + "\" is not an id like minecraft:stone");
				object.addProperty(option.key(), text);
			}
			case CHOICE -> {
				if (!option.choices().contains(text)) return Optional.of("\"" + text + "\" is not one of " + String.join(", ", option.choices()));
				object.addProperty(option.key(), text);
			}
			case TEXT_LIST, IDENTIFIER_LIST -> {
				JsonArray array = new JsonArray();
				for (String line : text.split("\n")) {
					String entry = line.trim();
					if (entry.isEmpty()) continue;
					if (option.kind() == OptionKind.IDENTIFIER_LIST && Identifier.tryParse(entry) == null) {
						return Optional.of("\"" + entry + "\" is not an id like minecraft:stone");
					}
					array.add(entry);
				}
				object.add(option.key(), array);
			}
			case SECTION, READ_ONLY -> {
				return Optional.of("cannot be edited here");
			}
		}
		return Optional.empty();
	}

	/** The text an input starts with: what {@link #apply} would read back as the same value. */
	public static String text(OptionSpec option, Object value) {
		if (value instanceof Optional<?> optional) {
			if (optional.isEmpty()) return "";
			value = optional.get();
		}
		return switch (option.kind()) {
			case WHOLE -> Long.toString(((Number) value).longValue());
			case DECIMAL -> OptionSpec.number(((Number) value).doubleValue());
			case CHOICE -> ((StringRepresentable) value).getSerializedName();
			case TEXT_LIST, IDENTIFIER_LIST -> String.join("\n", ((List<?>) value).stream().map(Object::toString).toList());
			default -> String.valueOf(value);
		};
	}
}
```

Note: the `DECIMAL` message for `"1,5"` must name the option; `apply` prefixes the option's description, and the test checks `contains("speed")` — so also include the key. Change the prefix line in `apply` to:
```java
				String message = option.get().key() + " (" + option.get().description() + "): " + problem.get();
```

- [ ] **Step 4: Run to see it pass**

Run: `./gradlew :mods:metacraft-lib:test --tests TestConfigEdits`
Expected: PASS (8 tests). If `outOfRangeAndUnknownChoiceAreRefused` fails on `count 99`, check the codec error from Task 2 is prefixed with the key (`count: 99 is not in (1 – 16)`).

- [ ] **Step 5: Commit**

```bash
git add mods/metacraft-lib/src
git commit -m "metacraft-lib: apply typed edits to a described config through its codec"
```

---

### Task 4: Containers: keep a broken file, `update`, change listeners

**Files:**
- Modify: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/JsonHelper.java`
- Modify: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/container/ConfigContainerBase.java`
- Modify: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/container/ConfigContainer.java`
- Modify: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/container/impl/BasicConfigContainer.java`
- Modify: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/extensions/Modifiable.java`
- Test: `mods/metacraft-lib/src/test/java/TestContainerLoading.java`

**Interfaces:**
- Produces: `JsonHelper.read(Path, Codec<T>, UnaryOperator<DynamicOps<JsonElement>>) : Optional<DataResult<T>>` (empty when the file does not exist); `ConfigContainerBase.loadError() : Optional<String>` (default empty); `ConfigContainer.update(UnaryOperator<T>)`, `ConfigContainer.addChangeListener(BiConsumer<T, T>)`.

- [ ] **Step 1: Check who implements `ConfigContainer`**

Run: `grep -rn "implements ConfigContainer<\|implements ConfigContainer " mods/`
Expected: only `BasicConfigContainer`. If another class appears, it gets the same `addChangeListener` implementation as below.

- [ ] **Step 2: Write the failing test**

```java
import com.mojang.serialization.Codec;
import fixtures.*;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.ConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestContainerLoading {

	private static ConfigContainer<SampleSection> container(Path file) {
		return ConfigContainer.Builder.create(ConfigSpec.of(SampleSection.class).codec(), () -> SampleSection.DEFAULT).build(file);
	}

	@Test
	public void writesDefaultsWhenThereIsNoFile(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		assertEquals(SampleSection.DEFAULT, container(file).get());
		assertTrue(Files.readString(file).contains("\"interval\""));
	}

	@Test
	public void aBrokenFileIsLeftAloneAndReported(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		Files.writeString(file, "{\"interval\": 999}");
		var container = container(file);
		assertEquals(SampleSection.DEFAULT, container.get());
		assertEquals("{\"interval\": 999}", Files.readString(file));
		assertTrue(container.loadError().orElseThrow().contains("interval"));
		try (var files = Files.list(dir)) {
			assertEquals(1, files.count());   // no .bak
		}
	}

	@Test
	public void aReloadThatBreaksKeepsTheLastGoodValue(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		Files.writeString(file, "{\"url\": \"a\", \"interval\": 5}");
		var container = container(file);
		assertEquals(5, container.get().interval());
		Files.writeString(file, "{\"interval\": ");
		container.reload();
		assertEquals(5, container.get().interval());
		assertTrue(container.loadError().isPresent());
		Files.writeString(file, "{\"url\": \"a\", \"interval\": 6}");
		container.reload();
		assertEquals(6, container.get().interval());
		assertTrue(container.loadError().isEmpty());
	}

	@Test
	public void updateSavesAndNotifies(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		var container = container(file);
		List<String> seen = new ArrayList<>();
		container.addChangeListener((old, current) -> seen.add(old.interval() + "->" + current.interval()));
		container.update(s -> new SampleSection(s.url(), 42 % 60));
		assertEquals(List.of("10->42"), seen);
		assertTrue(Files.readString(file).contains("42"));
		container.update(s -> s);   // no change, no event
		assertEquals(1, seen.size());
	}

	@Test
	public void savingOverABrokenFileClearsTheError(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		Files.writeString(file, "not json");
		var container = container(file);
		container.get();
		assertTrue(container.loadError().isPresent());
		container.update(s -> new SampleSection("fixed", 3));
		assertTrue(container.loadError().isEmpty());
		assertTrue(Files.readString(file).contains("fixed"));
	}

	@Test
	public void reloadNotifiesWhenTheFileChanged(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		var container = container(file);
		container.get();
		List<Integer> seen = new ArrayList<>();
		container.addChangeListener((old, current) -> seen.add(current.interval()));
		Files.writeString(file, "{\"interval\": 7}");
		container.reload();
		assertEquals(List.of(7), seen);
	}
}
```

- [ ] **Step 3: Run to see it fail**

Run: `./gradlew :mods:metacraft-lib:test --tests TestContainerLoading`
Expected: compilation fails (`loadError`, `update`, `addChangeListener` missing).

- [ ] **Step 4: Add `JsonHelper.read`**

Add to `JsonHelper`:
```java
	/**
	 * The file read with {@code codec}: empty when there is no file, an error result (with the
	 * message) when it exists but does not parse. A partial result counts as an error.
	 */
	public static <T> Optional<DataResult<T>> read(Path configPath, Codec<T> codec, UnaryOperator<DynamicOps<JsonElement>> opsFixer) {
		File file = configPath.toFile();
		if (!file.exists()) return Optional.empty();
		try (var reader = new BufferedReader(new FileReader(file))) {
			JsonElement element = JsonParser.parseReader(reader);
			DataResult<T> result = codec.parse(opsFixer.apply(JsonOps.INSTANCE), element);
			if (result.error().isPresent()) {
				String message = result.error().get().message();
				return Optional.of(DataResult.error(() -> message));
			}
			return Optional.of(result);
		} catch (JsonParseException e) {
			return Optional.of(DataResult.error(() -> "not valid JSON: " + e.getMessage()));
		} catch (IOException e) {
			return Optional.of(DataResult.error(() -> "cannot read the file: " + e.getMessage()));
		}
	}
```
Add imports `com.mojang.serialization.DataResult`, `com.google.gson.JsonParseException` if missing (`JsonSyntaxException` and `JsonIOException` both extend `JsonParseException`).

- [ ] **Step 5: Add to the interfaces**

`ConfigContainerBase`:
```java
	/** Why the file could not be read, while the last good value (or the default) is in use. */
	default Optional<String> loadError() {
		return Optional.empty();
	}
```

`ConfigContainer` — mark `modify` deprecated and add:
```java
	/**
	 * @deprecated Needs a mutable config and applies later; use {@link #update}.
	 */
	@Deprecated
	void modify(Predicate<T> modifier);

	/** Replaces the config with {@code change} applied to it, saves, and notifies listeners if it changed. */
	default void update(UnaryOperator<T> change) {
		replace(change.apply(get()));
	}

	/** Called with the old and new value after an update, or a reload that changed the value. */
	void addChangeListener(BiConsumer<T, T> listener);
```

`Modifiable`: add `@Deprecated` and the Javadoc line `@deprecated Use records and {@link nu.metacraft.lib.config.container.ConfigContainer#update}.`

- [ ] **Step 6: Change `BasicConfigContainer`**

Add fields:
```java
	protected @Nullable String loadError;
	protected final List<BiConsumer<T, T>> changeListeners = new ArrayList<>();
```

Replace `loadFromFile` and add `readFile` (and in `WithLookup`, override `readFile` instead of `loadFromFile`):
```java
	protected Optional<T> loadFromFile() {
		Optional<DataResult<T>> read = readFile();
		if (read.isEmpty()) {
			loadError = null;
			return Optional.empty();
		}
		DataResult<T> result = read.get();
		if (result.error().isPresent()) {
			loadError = result.error().get().message();
			METAcraftLib.LOGGER.error("Unable to load {}, keeping the settings in use: {}", configPath, loadError);
			return Optional.empty();
		}
		loadError = null;
		return result.result();
	}

	protected Optional<DataResult<T>> readFile() {
		return JsonHelper.read(configPath, codec, ops -> ops);
	}
```
`WithLookup`:
```java
		@Override
		protected Optional<DataResult<T>> readFile() {
			return JsonHelper.read(configPath, codec, lookupSupplier.get()::createSerializationContext);
		}
```
(remove `WithLookup.loadFromFile`).

In `get()`, replace the `else` branch that backs up the file with:
```java
				} else {
					// No file: write the defaults. A file that does not load is left for a person to fix.
					config = initDefaultConfig();
					if (loadError == null) save();
				}
```

`reload`:
```java
	@Override
	public void reload(ReloadCause cause) {
		if (config instanceof ReloadAware r) {
			r.beforeReload(cause);
		}
		T old = config;
		config = reloader.reload(config, this::loadFromFile, cause);
		triggerLoad(Optional.of(cause));
		onReload.accept(cause);
		notifyChanged(old, config);
	}
```

`replace`:
```java
	@Override
	public void replace(T newConfig) {
		T old = config;
		this.config = newConfig;
		if (old != newConfig) {
			save();
			loadError = null;
		}
		notifyChanged(old, newConfig);
	}

	@Override
	public void addChangeListener(BiConsumer<T, T> listener) {
		changeListeners.add(listener);
	}

	@Override
	public Optional<String> loadError() {
		return Optional.ofNullable(loadError);
	}

	private void notifyChanged(@Nullable T old, @Nullable T current) {
		if (old == null || current == null || old.equals(current)) return;
		for (BiConsumer<T, T> listener : changeListeners) listener.accept(old, current);
	}
```
Mark `modify` `@Deprecated` in `BasicConfigContainer` too. Imports: `DataResult`, `BiConsumer`, `ArrayList`, `org.jetbrains.annotations.Nullable`. Remove imports left unused by the deleted backup code (`Files`, `StandardCopyOption`, `IOException` if unused).

- [ ] **Step 7: Run to see it pass, and nothing else broke**

Run: `./gradlew :mods:metacraft-lib:test`
Expected: PASS (all lib tests, including the existing two).
Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL (every mod still compiles; deprecation warnings in loot-containers, resource-packs and saved-items are expected).

- [ ] **Step 8: Commit**

```bash
git add mods/metacraft-lib/src
git commit -m "metacraft-lib: keep a config file that does not load, report it; update() and change listeners"
```

---

### Task 5: Described configs and their registry

**Files:**
- Create: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/describe/{DescribedConfig,ConfigRegistry}.java`
- Modify: `mods/metacraft-lib/src/main/java/nu/metacraft/lib/config/container/ConfigContainer.java` (`Builder.describedBy`, `RegistryAwareBuilder.describedBy`, tidy casts)
- Test: `mods/metacraft-lib/src/test/java/TestDescribedConfig.java`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces:
  - `DescribedConfig<R extends Record>` with `id() : String`, `spec() : ConfigSpec<R>`, `get() : R`, `loadError() : Optional<String>`, `hash() : int`, `apply(List<String> page, Map<String,String> values, int expectedHash) : DataResult<R>`, `reset(List<String> page, int expectedHash) : DataResult<R>`, `pendingRestart() : List<String>` (descriptions of restart options whose saved value differs from the one the server started with), `valueOn(List<String> page) : Record` (the page's current record).
  - `DescribedConfig.STALE` — the message of a stale save: `"Someone changed this config since you opened it; here it is as it is now."`
  - `ConfigRegistry.register(DescribedConfig<?>)`, `ConfigRegistry.all() : List<DescribedConfig<?>>`, `ConfigRegistry.find(String id) : Optional<DescribedConfig<?>>`, `ConfigRegistry.clearForTests()`.
  - `ConfigContainer.Builder#describedBy(Class<? extends Record>)`, `RegistryAwareBuilder#describedBy(Class<? extends Record>)`; the id is the file name without `.json`.

- [ ] **Step 1: Write the failing test**

```java
import fixtures.*;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestDescribedConfig {

	@BeforeEach
	public void clear() {
		ConfigRegistry.clearForTests();
	}

	private static ConfigContainer<Sample> build(Path file) {
		return ConfigContainer.Builder.create(ConfigSpec.of(Sample.class).codec(), () -> Sample.DEFAULT)
				.describedBy(Sample.class).build(file);
	}

	@Test
	public void registersUnderTheFileName(@TempDir Path dir) {
		build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		assertEquals("Sample", config.spec().name());
	}

	@Test
	public void appliesAnEditThroughTheContainer(@TempDir Path dir) throws Exception {
		var container = build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		assertTrue(config.apply(List.of(), Map.of("count", "3"), config.hash()).error().isEmpty());
		assertEquals(3, container.get().count());
		assertTrue(Files.readString(dir.resolve("sample.json")).contains("\"count\": 3"));
	}

	@Test
	public void aStaleSaveIsRefused(@TempDir Path dir) throws Exception {
		var container = build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		int opened = config.hash();
		Files.writeString(dir.resolve("sample.json"), "{\"count\": 8}");   // edited by hand
		container.reload();
		var result = config.apply(List.of(), Map.of("count", "3"), opened);
		assertEquals(DescribedConfig.STALE, result.error().orElseThrow().message());
		assertEquals(8, container.get().count());
	}

	@Test
	public void restartOptionsArePendingUntilRestart(@TempDir Path dir) {
		build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		config.get();   // the value the server started with
		assertEquals(List.of(), config.pendingRestart());
		config.apply(List.of(), Map.of("mode", "slow"), config.hash());
		assertEquals(List.of("Mode."), config.pendingRestart());
		config.apply(List.of(), Map.of("mode", "fast"), config.hash());
		assertEquals(List.of(), config.pendingRestart());
	}

	@Test
	public void resetsOnePage(@TempDir Path dir) {
		var container = build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		config.apply(List.of("store"), Map.of("url", "x"), config.hash());
		config.apply(List.of(), Map.of("count", "2"), config.hash());
		config.reset(List.of("store"), config.hash());
		assertEquals(SampleSection.DEFAULT, container.get().store());
		assertEquals(2, container.get().count());
	}

	@Test
	public void aDuplicateIdIsRefused(@TempDir Path dir) {
		build(dir.resolve("sample.json"));
		assertThrows(IllegalStateException.class, () -> build(dir.resolve("other/sample.json")));
	}

	@Test
	public void aHandWrittenCodecMustWriteEveryKey(@TempDir Path dir) {
		var handWritten = com.mojang.serialization.codecs.RecordCodecBuilder.<SampleSection>mapCodec(i -> i.group(
				com.mojang.serialization.Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
				com.mojang.serialization.Codec.INT.fieldOf("seconds").forGetter(SampleSection::interval)
		).apply(i, SampleSection::new));
		assertThrows(ConfigSpecException.class, () -> ConfigContainer.Builder.create(handWritten, () -> SampleSection.DEFAULT)
				.describedBy(SampleSection.class).build(dir.resolve("s.json")));
	}
}
```

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew :mods:metacraft-lib:test --tests TestDescribedConfig`
Expected: compilation fails (`describedBy`, `ConfigRegistry` missing).

- [ ] **Step 3: Write `DescribedConfig`**

```java
package nu.metacraft.lib.config.describe;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import nu.metacraft.lib.config.container.ConfigContainer;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A described config the screen can edit: a record {@code R} kept in a container of {@code C}
 * (the record itself, or a pair with registry-aware values). Edits go through {@link ConfigEdits};
 * a save made against a value that has changed since the page was opened is refused.
 */
public final class DescribedConfig<R extends Record> {
	public static final String STALE = "Someone changed this config since you opened it; here it is as it is now.";

	private final String id;
	private final ConfigSpec<R> spec;
	private final Codec<R> codec;
	private final Access<?, R> access;
	private @Nullable R startValue;

	public <C> DescribedConfig(String id, ConfigSpec<R> spec, Codec<R> codec, ConfigContainer<C> container,
			Function<C, R> part, BiFunction<C, R, C> withPart) {
		this.id = id;
		this.spec = spec;
		this.codec = codec;
		this.access = new Access<>(container, part, withPart);
	}

	public String id() { return id; }
	public ConfigSpec<R> spec() { return spec; }
	public Optional<String> loadError() { return access.container.loadError(); }

	public R get() {
		R value = access.get();
		if (startValue == null) startValue = value;
		return value;
	}

	/** The current value's written form, hashed: a page remembers it to notice edits made meanwhile. */
	public int hash() {
		return codec.encodeStart(JsonOps.INSTANCE, get()).getOrThrow().toString().hashCode();
	}

	public DataResult<R> apply(List<String> page, Map<String, String> values, int expectedHash) {
		if (expectedHash != hash()) return DataResult.error(() -> STALE);
		DataResult<R> result = ConfigEdits.apply(spec, codec, get(), page, values, JsonOps.INSTANCE);
		result.result().ifPresent(access::set);
		return result;
	}

	public DataResult<R> reset(List<String> page, int expectedHash) {
		if (expectedHash != hash()) return DataResult.error(() -> STALE);
		DataResult<R> result = ConfigEdits.reset(spec, codec, get(), page, JsonOps.INSTANCE);
		result.result().ifPresent(access::set);
		return result;
	}

	/** The record shown on {@code page}: the config itself, or one of its sections. */
	public Record valueOn(List<String> page) {
		Record value = get();
		ConfigSpec<?> pageSpec = spec;
		for (String key : page) {
			OptionSpec option = pageSpec.option(key).orElseThrow();
			value = (Record) option.read(value);
			pageSpec = option.section();
		}
		return value;
	}

	/** Descriptions of the restart options whose saved value differs from the one the server started with. */
	public List<String> pendingRestart() {
		R start = startValue != null ? startValue : get();
		List<String> pending = new ArrayList<>();
		collectPending(spec, start, get(), pending);
		return pending;
	}

	private static void collectPending(ConfigSpec<?> spec, Record start, Record now, List<String> into) {
		for (OptionSpec option : spec.options()) {
			Object a = option.read(start), b = option.read(now);
			if (option.kind() == OptionKind.SECTION) {
				collectPending(option.section(), (Record) a, (Record) b, into);
			} else if (option.restart() && !Objects.equals(a, b)) {
				into.add(option.description());
			}
		}
	}

	private record Access<C, R>(ConfigContainer<C> container, Function<C, R> part, BiFunction<C, R, C> withPart) {
		R get() {
			return part.apply(container.get());
		}

		void set(R value) {
			container.update(whole -> withPart.apply(whole, value));
		}
	}
}
```

- [ ] **Step 4: Write `ConfigRegistry`**

```java
package nu.metacraft.lib.config.describe;

import java.util.*;

/** Every described config, by id (its file name without {@code .json}), in registration order. */
public final class ConfigRegistry {
	private static final Map<String, DescribedConfig<?>> CONFIGS = new LinkedHashMap<>();

	private ConfigRegistry() {}

	public static synchronized void register(DescribedConfig<?> config) {
		if (CONFIGS.containsKey(config.id())) {
			throw new IllegalStateException("two described configs are called " + config.id());
		}
		CONFIGS.put(config.id(), config);
	}

	public static synchronized List<DescribedConfig<?>> all() {
		return List.copyOf(CONFIGS.values());
	}

	public static synchronized Optional<DescribedConfig<?>> find(String id) {
		return Optional.ofNullable(CONFIGS.get(id));
	}

	/** Tests register the same fixtures over and over. */
	public static synchronized void clearForTests() {
		CONFIGS.clear();
	}
}
```

- [ ] **Step 5: `describedBy` on the builders, and the tidy**

In `ConfigContainer.Builder`, add a field and method:
```java
		protected @Nullable Class<? extends Record> described;

		/**
		 * Lists this config in the config screen, described by the annotations on {@code type}, which
		 * must be the config's own record. Its id is the file name without {@code .json}.
		 */
		public Builder<T> describedBy(Class<? extends Record> type) {
			this.described = type;
			return this;
		}
```
Change `build(Path configPath)`:
```java
		public ConfigContainer<T> build(Path configPath) {
			ConfigContainer<T> container = new BasicConfigContainer<>(codec.codec(), configPath, defaultConfigInitializer, reloadsBeforeServer, reloadsAfterServer, reloader);
			if (described != null) register(configPath, container, t -> t, (whole, part) -> part);
			return container;
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		protected <C> void register(Path configPath, ConfigContainer<C> container, Function<C, T> part, BiFunction<C, T, C> withPart) {
			ConfigSpec spec = ConfigSpec.of((Class) described);
			if (!(codec instanceof DescribedCodec)) spec.checkWrittenBy(codec.codec());
			String id = configPath.getFileName().toString().replaceFirst("\\.json$", "");
			ConfigRegistry.register(new DescribedConfig(id, spec, codec.codec(), container, part, withPart));
		}
```
(`build(Path, Supplier<Provider>)` and `build(Path, Provider)`: add the same `if (described != null) register(...)` after constructing the `WithLookup` container.)

In `RegistryAwareBuilder`, add:
```java
			/** As {@link Builder#describedBy}; describes the static part of the config. */
			public RegistryAwareBuilder<S> describedBy(Class<? extends Record> type) {
				Builder.this.described = type;
				return this;
			}
```
and in its `build(Path)`, keep a reference to the inner `BasicConfigContainer` and register it before wrapping:
```java
				var inner = new BasicConfigContainer<>(
						ServerAware.ConfigPair.createCodec(codec, serverAwareCodec, refreshOnReload),
						configPath, () -> new ServerAware.ConfigPair<>(defaultConfigInitializer.get(), defaultRegistryAwareInitializer.get()),
						reloadsBeforeServer, reloadsAfterServer, ServerAware.wrapReload(reloader)
				);
				if (described != null) {
					register(configPath, inner, ServerAware.ConfigPair::staticValues,
							(pair, part) -> new ServerAware.ConfigPair<>(part, pair.serverAwareValues()));
				}
				return ServerAware.wrap(inner, parser != null ? parser : defaultParser(refreshOnReload), cacheReloader, defaultRegistryAwareInitializer);
```

The tidy: replace the two `NON_RELOADABLE`/`RELOADABLE` constants and their casts. The parser field starts `null`; `refreshOnReload()` no longer swaps it; add:
```java
			/** Parses the registry-aware values with the server's registries (reloadable ones when refreshing on reload). */
			private ServerAware.Parser<ConfigContainer<ServerAware.ConfigPair<T, S>>, S> defaultParser(boolean reloadable) {
				return (config, server) -> config.get().serverAwareValues().parse(
						reloadable ? server.reloadableRegistries().lookup() : server.registryAccess());
			}
```
Check `ObjectStorage.parse` accepts both argument types (`HolderLookup.Provider`); `server.reloadableRegistries().lookup()` and `server.registryAccess()` both are one. The `setParser` method is unchanged.

Imports in `ConfigContainer`: `nu.metacraft.lib.config.describe.*`, `org.jetbrains.annotations.Nullable`, `java.util.function.BiFunction`.

- [ ] **Step 6: Run to see it pass, and the whole build**

Run: `./gradlew :mods:metacraft-lib:test`
Expected: PASS.
Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add mods/metacraft-lib/src
git commit -m "metacraft-lib: described configs register for the config screen; tidy the registry-aware builder"
```

---

### Task 6: faster-minecarts as a described record

**Files:**
- Modify: `mods/faster-minecarts/src/main/java/nu/metacraft/faster_minecarts/FasterMinecartsConfig.java`
- Test: `mods/faster-minecarts/src/test/java/TestFasterMinecartsConfig.java`, `mods/faster-minecarts/src/test/resources/faster_minecarts_before.json`

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: `FasterMinecartsConfig` record with the same accessor names as before (`globalFasterMinecarts()`, `maxMinecartSpeed()`, `maxMinecartSpeedUnderwater()`, `dangerousMinecartSpeed()`, `damageFactor()`, `experimentalMinecartMode()`), `FasterMinecartsConfig.DEFAULT`; registered id `faster_minecarts` (the file is `config/faster_minecarts.json` — `FasterMinecarts.NAMESPACE`; confirm the value and use it in the test).

- [ ] **Step 1: Capture today's file as a fixture**

Before changing anything, write the file the current code produces:
```bash
./gradlew :mods:faster-minecarts:runServer --offline -q &   # stop it once "Done" is logged, or reuse a run dir
```
If running a server is impractical, write the fixture by hand from the current codec and defaults:
`mods/faster-minecarts/src/test/resources/faster_minecarts_before.json`
```json
{
	"global_faster_minecarts": false,
	"max_minecart_speed": 60.0,
	"max_minecart_speed_underwater": 45.0,
	"dangerous_minecart_speed": 0.4166666666666667,
	"damage_factor": 43.2,
	"experimental_minecart_mode": "experimental"
}
```
(30 / 3.6 / 20 = 0.41666…, 2.16 × 20 = 43.2.) The registry-aware keys (`minecart_modifiers`, …) are not part of the static codec and are left out.

- [ ] **Step 2: Write the failing test**

```java
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.faster_minecarts.FasterMinecartsConfig;
import nu.metacraft.lib.config.describe.ConfigSpec;
import nu.metacraft.lib.config.describe.OptionSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestFasterMinecartsConfig {
	@BeforeAll
	public static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void keepsItsFileKeys() {
		assertEquals(List.of("global_faster_minecarts", "max_minecart_speed", "max_minecart_speed_underwater",
						"dangerous_minecart_speed", "damage_factor", "experimental_minecart_mode"),
				ConfigSpec.of(FasterMinecartsConfig.class).options().stream().map(OptionSpec::key).toList());
	}

	@Test
	public void readsTheFileWrittenBeforeTheChange() throws Exception {
		try (var in = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/faster_minecarts_before.json")))) {
			var read = ConfigSpec.of(FasterMinecartsConfig.class).codec().codec().parse(JsonOps.INSTANCE, JsonParser.parseReader(in)).getOrThrow();
			assertEquals(FasterMinecartsConfig.DEFAULT, read);
		}
	}

	@Test
	public void experimentalModeNeedsARestart() {
		assertTrue(ConfigSpec.of(FasterMinecartsConfig.class).option("experimental_minecart_mode").orElseThrow().restart());
	}
}
```

- [ ] **Step 3: Run to see it fail**

Run: `./gradlew :mods:faster-minecarts:test`
Expected: compilation fails (`FasterMinecartsConfig.DEFAULT` missing; not a record).

- [ ] **Step 4: Convert the static part to a record**

Replace the class declaration, the fields, the constructor, `createDefault` and the six getters in `FasterMinecartsConfig.java` with:
```java
@Config(name = "Faster Minecarts", description = "Minecarts that go faster, on the rails and blocks the server lists.")
public record FasterMinecartsConfig(
		@Option(description = "Every minecart is fast, not only upgraded ones.") boolean globalFasterMinecarts,
		@Option(description = "Top speed of a fast minecart, blocks per second.", min = 0) double maxMinecartSpeed,
		@Option(description = "Top speed under water, blocks per second.", min = 0) double maxMinecartSpeedUnderwater,
		@Option(description = "Above this speed (blocks per tick) a minecart hurts what it hits; empty for never.", min = 0) Optional<Double> dangerousMinecartSpeed,
		@Option(description = "Damage per block per tick above the dangerous speed.", min = 0) double damageFactor,
		@Option(description = "Use vanilla's experimental minecart physics.", restart = true) ExperimentalMinecartMode experimentalMinecartMode
) {
	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve(FasterMinecarts.NAMESPACE + ".json");

	public static final FasterMinecartsConfig DEFAULT = new FasterMinecartsConfig(
			false, 60, 45, Optional.of(30 / 3.6 / 20), 2.16 * 20, ExperimentalMinecartMode.EXPERIMENTAL
	);

	public static final MapCodec<FasterMinecartsConfig> CODEC = ConfigSpec.of(FasterMinecartsConfig.class).codec();

	private static final ServerAware<ConfigContainer<ServerAware.ConfigPair<FasterMinecartsConfig, Loaded>>, Loaded> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> DEFAULT
	).makeRegistryAware(Loaded.CODEC).describedBy(FasterMinecartsConfig.class).setInitializer(Loaded::createDefault).build(configPath);
```
Keep `getConfig()`, `getConfig(MinecraftServer)` and the nested types (`MinecartModifier`, `EntityDamageList`, `BlockBooster`, `ExperimentalMinecartMode`, `Loaded`) unchanged inside the record body. Note: `DEFAULT` must be declared before `CODEC` and `CONTAINER` (static initialisation order: `ConfigSpec.of` reads `DEFAULT`). Imports: `nu.metacraft.lib.config.describe.Config`, `...Option`, `...ConfigSpec`.

The descriptions above are my reading of the code; check them against `FasterMinecartsHelper` and `FasterMinecarts` (e.g. whether `global_faster_minecarts` means "every minecart" and the units of `dangerous_minecart_speed`) and correct the wording if the code says otherwise.

- [ ] **Step 5: Run to see it pass, and the whole build**

Run: `./gradlew :mods:faster-minecarts:test :mods:metacraft-lib:test`
Expected: PASS.
Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL (callers use the same accessor names).

- [ ] **Step 6: Commit**

```bash
git add mods/faster-minecarts
git commit -m "faster-minecarts: describe its config for the config screen, file unchanged"
```

---

### Task 7: The metacraft-config mod and its sources

**Files:**
- Modify: `settings.gradle` (add `include "mods:metacraft-config"` after `metacraft-bundles`), `gradle.properties` (add `metacraft_config_version = 1.0.0` beside the other mod versions)
- Create: `mods/metacraft-config/build.gradle`, `mods/metacraft-config/src/main/resources/fabric.mod.json`
- Create: `mods/metacraft-config/src/main/java/nu/metacraft/config/MetacraftConfig.java`
- Create: `mods/metacraft-config/src/main/java/nu/metacraft/config/source/{ConfigSource,Page,Field,Link,EditOutcome,DescribedSource,Sources}.java`
- Test: `mods/metacraft-config/src/test/java/{TestInit,TestDescribedSource}.java`, `mods/metacraft-config/src/test/java/fixtures/Demo.java`

**Interfaces:**
- Consumes: `DescribedConfig`, `ConfigRegistry`, `ConfigEdits.text`, `OptionSpec`, `OptionKind` (Tasks 1–5).
- Produces:
```java
public interface ConfigSource {
	String id();
	String name();
	String description();
	Optional<String> loadError();
	List<String> pendingRestart();
	int hash();
	/** @throws IllegalArgumentException for a page the source does not have */
	Page page(List<String> path);
	EditOutcome apply(List<String> path, Map<String, String> values, int expectedHash);
	EditOutcome reset(List<String> path, int expectedHash);
}
public record Page(List<String> path, String title, List<Field> fields, List<Link> sections) {}
public record Field(String key, String label, OptionKind kind, String value, double min, double max, double step,
		boolean slider, List<String> choices, boolean restart, boolean editable) {}
public record Link(String key, String label) {}
public sealed interface EditOutcome {
	record Saved() implements EditOutcome {}
	record Refused(String message) implements EditOutcome {}
	record Stale() implements EditOutcome {}
}
Sources.all() : List<ConfigSource>; Sources.find(String id) : Optional<ConfigSource>; Sources.addAdapter(ConfigSource)
```

- [ ] **Step 1: Scaffold the module**

`mods/metacraft-config/build.gradle`:
```groovy
// metacraft-config — /config: every described config the server runs, edited in a vanilla dialog.
version = project.metacraft_config_version
```
(Every mod already gets metacraft-lib, Fabric API and fabric-permissions-api from the root build.)

`fabric.mod.json`:
```json
{
	"schemaVersion": 1,
	"id": "metacraft-config",
	"version": "${version}",
	"name": "METAcraft Config",
	"description": "/config: change the server's mod settings in a dialog.",
	"authors": ["Froosty11"],
	"contact": {
		"sources": "${git_repo}",
		"homepage": "${website}",
		"issues": "${issues}"
	},
	"license": "Apache-2.0",
	"environment": "*",
	"entrypoints": {
		"main": ["nu.metacraft.config.MetacraftConfig"]
	},
	"depends": {
		"fabricloader": ">=${loader_version}",
		"fabric-api": "*",
		"minecraft": "~${minecraft_version}",
		"metacraft-lib": "*"
	},
	"suggests": {
		"polydecorations": "*"
	}
}
```
Check another mod's `fabric.mod.json` for the placeholders the build expands (`${git_repo}` etc.) and match it.

`MetacraftConfig.java` (grows in Tasks 9–10):
```java
package nu.metacraft.config;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@code /config}: the server's described configs, edited in vanilla dialogs. */
public final class MetacraftConfig implements ModInitializer {
	public static final String MOD_ID = "metacraft-config";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
	}
}
```
Check which logger other mods use (`METAcraftLib.LOGGER` is log4j); use the same library.

Run: `./gradlew :mods:metacraft-config:build -x test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Write the failing test**

`src/test/java/TestInit.java` — copy `mods/metacraft-lib/src/test/java/TestInit.java`, replacing its init lambda body with nothing (`() -> {}`) and `METAcraftLib::new` kept.

`fixtures/Demo.java`:
```java
package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

import java.util.Optional;

@Config(name = "Demo", description = "For tests.")
public record Demo(
		@Option(description = "On.") boolean on,
		@Option(description = "Stitches.", min = 1, max = 16) int stitches,
		@Option(description = "Speed.", min = 0) double speed,
		@Option(description = "Cap.", min = 0) Optional<Double> cap,
		@Option(description = "Needs a restart.", restart = true) boolean heavy,
		@Option(description = "Inner.") Inner inner
) {
	public static final Demo DEFAULT = new Demo(true, 6, 2.5, Optional.empty(), false, Inner.DEFAULT);

	@Config(name = "Inner")
	public record Inner(@Option(description = "Name.") String name) {
		public static final Inner DEFAULT = new Inner("x");
	}
}
```

`TestDescribedSource.java`:
```java
import fixtures.Demo;
import nu.metacraft.config.source.*;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestDescribedSource {
	@BeforeEach
	public void clear() {
		ConfigRegistry.clearForTests();
		Sources.clearAdaptersForTests();
	}

	private static ConfigContainer<Demo> demo(Path dir) {
		return ConfigContainer.Builder.create(ConfigSpec.of(Demo.class).codec(), () -> Demo.DEFAULT)
				.describedBy(Demo.class).build(dir.resolve("demo.json"));
	}

	@Test
	public void listsRegisteredConfigs(@TempDir Path dir) {
		demo(dir);
		assertEquals(List.of("demo"), Sources.all().stream().map(ConfigSource::id).toList());
		assertEquals("Demo", Sources.find("demo").orElseThrow().name());
	}

	@Test
	public void describesThePage(@TempDir Path dir) {
		demo(dir);
		Page page = Sources.find("demo").orElseThrow().page(List.of());
		assertEquals("Demo", page.title());
		assertEquals(List.of("on", "stitches", "speed", "cap", "heavy"), page.fields().stream().map(Field::key).toList());
		Field stitches = page.fields().get(1);
		assertTrue(stitches.slider());
		assertEquals("6", stitches.value());
		assertEquals("Stitches. (1 – 16)", stitches.label());
		assertEquals("", page.fields().get(3).value());
		assertTrue(page.fields().get(4).restart());
		assertEquals(List.of(new Link("inner", "Inner.")), page.sections());
		assertEquals("Inner", Sources.find("demo").orElseThrow().page(List.of("inner")).title());
	}

	@Test
	public void savesAndReportsOutcomes(@TempDir Path dir) {
		var container = demo(dir);
		ConfigSource source = Sources.find("demo").orElseThrow();
		assertEquals(new EditOutcome.Saved(), source.apply(List.of(), Map.of("speed", "4"), source.hash()));
		assertEquals(4.0, container.get().speed());
		assertInstanceOf(EditOutcome.Refused.class, source.apply(List.of(), Map.of("speed", "fast"), source.hash()));
		assertEquals(new EditOutcome.Stale(), source.apply(List.of(), Map.of("speed", "5"), source.hash() + 1));
	}

	@Test
	public void unknownPageThrows(@TempDir Path dir) {
		demo(dir);
		assertThrows(IllegalArgumentException.class, () -> Sources.find("demo").orElseThrow().page(List.of("nope")));
	}
}
```

Run: `./gradlew :mods:metacraft-config:test`
Expected: compilation fails (`nu.metacraft.config.source` missing).

- [ ] **Step 3: Write the model**

`ConfigSource.java`, `Page.java`, `Field.java`, `Link.java`, `EditOutcome.java` exactly as in **Interfaces** above, each in package `nu.metacraft.config.source` with a one-line Javadoc:
- `ConfigSource`: "One config the screen can show and edit: a described config, or an adapter for another mod's file."
- `Page`: "One page of a source: its editable fields and the buttons to its sections. {@code path} is the section keys from the top."
- `Field`: "One option on a page, as the screen draws it; {@code value} is its current value as text."
- `Link`: "A button to a section's own page."
- `EditOutcome`: "What a save or reset did."

- [ ] **Step 4: Write `DescribedSource` and `Sources`**

```java
package nu.metacraft.config.source;

import com.mojang.serialization.DataResult;
import nu.metacraft.lib.config.describe.*;

import java.util.*;

/** A config described in metacraft-lib, as the screen sees it. */
public record DescribedSource(DescribedConfig<?> config) implements ConfigSource {

	@Override public String id() { return config.id(); }
	@Override public String name() { return config.spec().name(); }
	@Override public String description() { return config.spec().description(); }
	@Override public Optional<String> loadError() { return config.loadError(); }
	@Override public List<String> pendingRestart() { return config.pendingRestart(); }
	@Override public int hash() { return config.hash(); }

	@Override
	public Page page(List<String> path) {
		ConfigSpec<?> spec = ConfigEdits.pageSpec(config.spec(), path);
		Record value = config.valueOn(path);
		List<Field> fields = new ArrayList<>();
		List<Link> sections = new ArrayList<>();
		for (OptionSpec option : spec.options()) {
			if (option.kind() == OptionKind.SECTION) {
				sections.add(new Link(option.key(), option.description()));
				continue;
			}
			String range = option.rangeText();
			String label = range.isEmpty() ? option.description() : option.description() + " " + range;
			fields.add(new Field(option.key(), label, option.kind(), ConfigEdits.text(option, option.read(value)),
					option.min(), option.max(), option.effectiveStep(), option.slider(), option.choices(),
					option.restart(), option.editable()));
		}
		return new Page(List.copyOf(path), spec.name(), fields, sections);
	}

	@Override
	public EditOutcome apply(List<String> path, Map<String, String> values, int expectedHash) {
		return outcome(config.apply(path, values, expectedHash));
	}

	@Override
	public EditOutcome reset(List<String> path, int expectedHash) {
		return outcome(config.reset(path, expectedHash));
	}

	private static EditOutcome outcome(DataResult<?> result) {
		if (result.error().isEmpty()) return new EditOutcome.Saved();
		String message = result.error().get().message();
		return message.equals(DescribedConfig.STALE) ? new EditOutcome.Stale() : new EditOutcome.Refused(message);
	}
}
```

```java
package nu.metacraft.config.source;

import nu.metacraft.lib.config.describe.ConfigRegistry;

import java.util.*;

/** Everything the screen lists: metacraft-lib's described configs, then adapters. */
public final class Sources {
	private static final List<ConfigSource> ADAPTERS = new ArrayList<>();

	private Sources() {}

	public static synchronized void addAdapter(ConfigSource source) {
		ADAPTERS.add(source);
	}

	public static synchronized List<ConfigSource> all() {
		List<ConfigSource> all = new ArrayList<>();
		ConfigRegistry.all().forEach(config -> all.add(new DescribedSource(config)));
		all.addAll(ADAPTERS);
		return all;
	}

	public static Optional<ConfigSource> find(String id) {
		return all().stream().filter(source -> source.id().equals(id)).findFirst();
	}

	public static synchronized void clearAdaptersForTests() {
		ADAPTERS.clear();
	}
}
```

- [ ] **Step 5: Run to see it pass**

Run: `./gradlew :mods:metacraft-config:test`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle gradle.properties mods/metacraft-config
git commit -m "metacraft-config: the mod, and the sources the screen reads"
```

---

### Task 8: Pages as dialogs

**Files:**
- Create: `mods/metacraft-config/src/main/java/nu/metacraft/config/screen/{Inputs,Pages}.java`
- Test: `mods/metacraft-config/src/test/java/TestPages.java`

**Interfaces:**
- Consumes: `ConfigSource`, `Page`, `Field`, `Link` (Task 7).
- Produces:
  - `Inputs.forField(Field field, String value) : InputControl` (`value` is the text to start with: the current value, or what was typed before a refused save); `Inputs.key(int index) : String` (`"o" + index`).
  - `Pages.main(List<ConfigSource>) : Dialog`
  - `Pages.config(ConfigSource, List<String> path, Optional<Component> message, Map<String,String> typed) : Dialog` — `typed` pre-fills inputs after a refused save (field key → text); empty map for current values.
  - `Pages.confirmReset(ConfigSource, List<String> path) : Dialog`
  - Handler ids (used by Task 9): `Pages.OPEN = Identifier.fromNamespaceAndPath("metacraft-config", "open")`, `Pages.SAVE = …"save"`, `Pages.RESET = …"reset"`. Payload keys: `"source"` (string, `""` for the main page), `"page"` (string, section keys joined with `/`, `""` for the top), `"hash"` (int), `"confirm"` (byte, on RESET: 0 asks, 1 does it).

- [ ] **Step 1: Write the failing test**

```java
import fixtures.Demo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.input.*;
import nu.metacraft.config.screen.*;
import nu.metacraft.config.source.*;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestPages {
	@BeforeEach
	public void clear() {
		ConfigRegistry.clearForTests();
		Sources.clearAdaptersForTests();
	}

	private static ConfigSource demo(Path dir) {
		ConfigContainer.Builder.create(ConfigSpec.of(Demo.class).codec(), () -> Demo.DEFAULT)
				.describedBy(Demo.class).build(dir.resolve("demo.json"));
		return Sources.find("demo").orElseThrow();
	}

	@Test
	public void eachKindGetsItsInput(@TempDir Path dir) {
		MultiActionDialog page = (MultiActionDialog) Pages.config(demo(dir), List.of(), Optional.empty(), Map.of());
		List<Input> inputs = page.common().inputs();
		assertEquals(List.of("o0", "o1", "o2", "o3", "o4"), inputs.stream().map(Input::key).toList());
		assertInstanceOf(BooleanInput.class, inputs.get(0).control());
		NumberRangeInput slider = assertInstanceOf(NumberRangeInput.class, inputs.get(1).control());
		assertEquals(1f, slider.rangeInfo().start());
		assertEquals(16f, slider.rangeInfo().end());
		assertEquals(Optional.of(6f), slider.rangeInfo().initial());
		TextInput speed = assertInstanceOf(TextInput.class, inputs.get(2).control());
		assertEquals("2.5", speed.initial());
		assertInstanceOf(TextInput.class, inputs.get(3).control());   // optional: typed, blank = none
	}

	@Test
	public void saveCarriesSourcePageAndHash(@TempDir Path dir) {
		ConfigSource source = demo(dir);
		MultiActionDialog page = (MultiActionDialog) Pages.config(source, List.of(), Optional.empty(), Map.of());
		ActionButton save = page.actions().getFirst();
		CustomAll action = assertInstanceOf(CustomAll.class, save.action().orElseThrow());
		assertEquals(Pages.SAVE, action.id());
		var additions = action.additions().orElseThrow();
		assertEquals("demo", additions.getStringOr("source", "?"));
		assertEquals("", additions.getStringOr("page", "?"));
		assertEquals(source.hash(), additions.getIntOr("hash", 0));
	}

	@Test
	public void sectionsAreButtons(@TempDir Path dir) {
		MultiActionDialog page = (MultiActionDialog) Pages.config(demo(dir), List.of(), Optional.empty(), Map.of());
		assertTrue(page.actions().stream().anyMatch(b -> b.label().getString().equals("Inner.")));
	}

	@Test
	public void refusedValuesArePreFilled(@TempDir Path dir) {
		MultiActionDialog page = (MultiActionDialog) Pages.config(demo(dir), List.of(), Optional.of(Component.literal("bad")),
				Map.of("speed", "fast"));
		assertEquals("fast", ((TextInput) page.common().inputs().get(2).control()).initial());
	}

	@Test
	public void mainPageListsSourcesAndMarksProblems(@TempDir Path dir) {
		ConfigSource source = demo(dir);
		MultiActionDialog main = (MultiActionDialog) Pages.main(List.of(source));
		assertEquals("Demo", main.actions().getFirst().label().getString());
	}
}
```

Check accessor names against the real classes before running (`javap` on `MultiActionDialog`, `CommonDialogData`, `ActionButton`, `NumberRangeInput`, `RangeInfo`, `TextInput`, `CompoundTag#getStringOr/getIntOr`) and adjust the test's calls to match; the records' accessors are named after their constructor parameters.

Run: `./gradlew :mods:metacraft-config:test --tests TestPages`
Expected: compilation fails (`nu.metacraft.config.screen` missing).

- [ ] **Step 2: Write `Inputs`**

```java
package nu.metacraft.config.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.input.*;
import nu.metacraft.config.source.Field;
import nu.metacraft.lib.config.describe.OptionKind;

import java.util.Optional;

/** The dialog input for a field. Keys are positional ({@code o0}, {@code o1}, …): option keys may hold characters inputs cannot. */
public final class Inputs {
	private static final int WIDTH = 256, LIST_WIDTH = 300;

	private Inputs() {}

	public static String key(int index) {
		return "o" + index;
	}

	public static InputControl forField(Field field, String value) {
		Component label = Component.literal(field.label() + (field.restart() ? " ⟳" : ""));
		return switch (field.kind()) {
			case BOOLEAN -> new BooleanInput(label, value.equals("true"), "true", "false");
			case WHOLE, DECIMAL -> field.slider()
					? new NumberRangeInput(WIDTH, label, "options.generic_value",
							new NumberRangeInput.RangeInfo((float) field.min(), (float) field.max(),
									Optional.of(parse(value, (float) field.min())), Optional.of((float) field.step())))
					: text(label, value, 64);
			case CHOICE -> new SingleOptionInput(WIDTH, field.choices().stream()
					.map(choice -> new SingleOptionInput.Entry(choice, Optional.empty(), choice.equals(value))).toList(), label, true);
			case TEXT_LIST, IDENTIFIER_LIST -> new TextInput(LIST_WIDTH, label, true, value, 8192,
					Optional.of(new TextInput.MultilineOptions(Optional.of(8), Optional.empty())));
			default -> text(label, value, 1024);
		};
	}

	private static TextInput text(Component label, String value, int maxLength) {
		return new TextInput(WIDTH, label, true, value, maxLength, Optional.empty());
	}

	private static float parse(String value, float fallback) {
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
```

- [ ] **Step 3: Write `Pages`**

```java
package nu.metacraft.config.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import nu.metacraft.config.source.*;

import java.util.*;

/** The screen's dialogs: the list of configs, a config's page, and "reset this page?". */
public final class Pages {
	public static final Identifier OPEN = Identifier.fromNamespaceAndPath("metacraft-config", "open");
	public static final Identifier SAVE = Identifier.fromNamespaceAndPath("metacraft-config", "save");
	public static final Identifier RESET = Identifier.fromNamespaceAndPath("metacraft-config", "reset");
	private static final int BODY_WIDTH = 300, BUTTON_WIDTH = 150;

	private Pages() {}

	public static Dialog main(List<ConfigSource> sources) {
		List<DialogBody> body = new ArrayList<>();
		List<String> pending = new ArrayList<>();
		for (ConfigSource source : sources) {
			for (String option : source.pendingRestart()) pending.add(source.name() + ": " + option);
		}
		if (!pending.isEmpty()) {
			body.add(message(Component.literal("Restart needed for: " + String.join("; ", pending)).withStyle(ChatFormatting.GOLD)));
		}
		if (sources.isEmpty()) body.add(message(Component.literal("No configs are described on this server.")));
		List<ActionButton> buttons = new ArrayList<>();
		for (ConfigSource source : sources) {
			String marks = (source.loadError().isPresent() ? " ⚠" : "") + (source.pendingRestart().isEmpty() ? "" : " ⟳");
			buttons.add(button(Component.literal(source.name() + marks), Optional.of(Component.literal(source.description())),
					OPEN, target(source.id(), List.of())));
		}
		return new MultiActionDialog(common(Component.literal("Server config"), body, List.of()), buttons,
				Optional.of(closeButton()), 2);
	}

	public static Dialog config(ConfigSource source, List<String> path, Optional<Component> message, Map<String, String> typed) {
		Page page = source.page(path);
		List<DialogBody> body = new ArrayList<>();
		message.ifPresent(m -> body.add(message(m)));
		source.loadError().ifPresent(error -> body.add(message(Component.literal(
				"The file does not load, so the server uses the values below: " + error + ". Saving writes them.").withStyle(ChatFormatting.RED))));
		List<Input> inputs = new ArrayList<>();
		for (int i = 0; i < page.fields().size(); i++) {
			Field field = page.fields().get(i);
			if (!field.editable()) {
				body.add(message(Component.literal(field.label() + ": " + field.value() + " (edit in the file)").withStyle(ChatFormatting.GRAY)));
				continue;
			}
			inputs.add(new Input(Inputs.key(i), Inputs.forField(field, typed.getOrDefault(field.key(), field.value()))));
		}
		CompoundTag here = target(source.id(), path);
		here.putInt("hash", source.hash());
		List<ActionButton> buttons = new ArrayList<>();
		buttons.add(button(Component.literal("Save").withStyle(ChatFormatting.GREEN), Optional.empty(), SAVE, here.copy()));
		for (Link link : page.sections()) {
			List<String> sub = new ArrayList<>(path);
			sub.add(link.key());
			buttons.add(button(Component.literal(link.label()), Optional.of(Component.literal("Unsaved changes on this page are lost.")),
					OPEN, target(source.id(), sub)));
		}
		CompoundTag reset = here.copy();
		reset.putBoolean("confirm", false);
		buttons.add(button(Component.literal("Reset to defaults"), Optional.empty(), RESET, reset));
		buttons.add(button(Component.literal("Back"), Optional.empty(), OPEN,
				path.isEmpty() ? target("", List.of()) : target(source.id(), path.subList(0, path.size() - 1))));
		Component title = Component.literal(path.isEmpty() ? source.name() : source.name() + " › " + page.title());
		return new MultiActionDialog(common(title, body, inputs), buttons, Optional.of(closeButton()), 2);
	}

	public static Dialog confirmReset(ConfigSource source, List<String> path) {
		CompoundTag yes = target(source.id(), path);
		yes.putInt("hash", source.hash());
		yes.putBoolean("confirm", true);
		Component what = Component.literal(path.isEmpty() ? source.name() : source.page(path).title());
		return new MultiActionDialog(common(Component.literal("Reset " + what.getString() + "?"),
				List.of(message(Component.literal("Every option on this page goes back to its default. This is saved at once."))), List.of()),
				List.of(button(Component.literal("Reset").withStyle(ChatFormatting.RED), Optional.empty(), RESET, yes),
						button(Component.literal("Keep"), Optional.empty(), OPEN, target(source.id(), path))),
				Optional.empty(), 2);
	}

	static CompoundTag target(String source, List<String> path) {
		CompoundTag tag = new CompoundTag();
		tag.putString("source", source);
		tag.putString("page", String.join("/", path));
		return tag;
	}

	private static CommonDialogData common(Component title, List<DialogBody> body, List<Input> inputs) {
		return new CommonDialogData(title, Optional.empty(), true, false, DialogAction.WAIT_FOR_RESPONSE, body, inputs);
	}

	private static PlainMessage message(Component text) {
		return new PlainMessage(text, BODY_WIDTH);
	}

	private static ActionButton button(Component label, Optional<Component> tooltip, Identifier action, CompoundTag payload) {
		return new ActionButton(new CommonButtonData(label, tooltip, BUTTON_WIDTH), Optional.of(new CustomAll(action, Optional.of(payload))));
	}

	private static ActionButton closeButton() {
		return new ActionButton(new CommonButtonData(Component.literal("Close"), BUTTON_WIDTH), Optional.empty());
	}
}
```

- [ ] **Step 4: Run to see it pass**

Run: `./gradlew :mods:metacraft-config:test --tests TestPages`
Expected: PASS (5 tests). If `new NumberRangeInput` refuses the label format key, use `"options.generic_value"` as in vanilla's `number_range` default (check `NumberRangeInput`'s codec default for `label_format`).

- [ ] **Step 5: Commit**

```bash
git add mods/metacraft-config/src
git commit -m "metacraft-config: a source's page as a vanilla dialog"
```

---

### Task 9: Buttons, `/config`, and the join notice

**Files:**
- Create: `mods/metacraft-config/src/main/java/nu/metacraft/config/screen/{Payloads,Actions}.java`, `mods/metacraft-config/src/main/java/nu/metacraft/config/ConfigCommand.java`
- Modify: `mods/metacraft-config/src/main/java/nu/metacraft/config/MetacraftConfig.java`
- Test: `mods/metacraft-config/src/test/java/TestPayloads.java`

**Interfaces:**
- Consumes: `Pages`, `Inputs`, `Sources`, `ConfigSource`, `EditOutcome`; metacraft-lib's `CustomMessageRegistry.REGISTRY`, `CustomMessageHandler`, `PotentialPlayer`.
- Produces: `Payloads.values(Page, CompoundTag) : Map<String, String>` (field key → text; throws nothing, missing inputs are left out); `Payloads.path(CompoundTag) : List<String>`; `Actions.PERMISSION = "metacraft.config"`, `Actions.LEVEL = 3`; `Actions.openMain(ServerPlayer)`, `Actions.openConfig(ServerPlayer, ConfigSource, List<String>)`.

- [ ] **Step 1: Write the failing test**

```java
import net.minecraft.nbt.CompoundTag;
import nu.metacraft.config.screen.Payloads;
import nu.metacraft.config.source.Field;
import nu.metacraft.config.source.Page;
import nu.metacraft.lib.config.describe.OptionKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestPayloads {
	private static Field field(String key, OptionKind kind, boolean slider) {
		return new Field(key, key, kind, "", 0, 16, 1, slider, List.of("a", "b"), false, true);
	}

	private static final Page PAGE = new Page(List.of(), "T", List.of(
			field("on", OptionKind.BOOLEAN, false),
			field("count", OptionKind.WHOLE, true),
			field("share", OptionKind.DECIMAL, true),
			field("speed", OptionKind.DECIMAL, false),
			field("mode", OptionKind.CHOICE, false)), List.of());

	@Test
	public void readsEachInputAsText() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("o0", false);
		tag.putFloat("o1", 7.0f);
		tag.putFloat("o2", 0.25f);
		tag.putString("o3", " 3.5 ");
		tag.putString("o4", "b");
		assertEquals(Map.of("on", "false", "count", "7", "share", "0.25", "speed", " 3.5 ", "mode", "b"), Payloads.values(PAGE, tag));
	}

	@Test
	public void missingOrWrongTypedInputsAreLeftOutOrPassedAsText() {
		CompoundTag tag = new CompoundTag();
		tag.putString("o1", "lots");   // a modified client sends text for a slider
		tag.putInt("o9", 1);          // no such field
		Map<String, String> values = Payloads.values(PAGE, tag);
		assertEquals(Map.of("count", "lots"), values);   // ConfigEdits then refuses "lots" with a message
	}

	@Test
	public void readsThePath() {
		CompoundTag tag = new CompoundTag();
		tag.putString("page", "designs/store");
		assertEquals(List.of("designs", "store"), Payloads.path(tag));
		tag.putString("page", "");
		assertEquals(List.of(), Payloads.path(tag));
	}
}
```

Run: `./gradlew :mods:metacraft-config:test --tests TestPayloads`
Expected: compilation fails (`Payloads` missing).

- [ ] **Step 2: Write `Payloads`**

```java
package nu.metacraft.config.screen;

import net.minecraft.nbt.*;
import nu.metacraft.config.source.Field;
import nu.metacraft.config.source.Page;
import nu.metacraft.lib.config.describe.OptionKind;
import nu.metacraft.lib.config.describe.OptionSpec;

import java.util.*;

/** A dialog's payload read back into text per field, the form {@code ConfigEdits} takes. */
public final class Payloads {
	private Payloads() {}

	public static Map<String, String> values(Page page, CompoundTag payload) {
		Map<String, String> values = new LinkedHashMap<>();
		for (int i = 0; i < page.fields().size(); i++) {
			Field field = page.fields().get(i);
			Tag tag = payload.get(Inputs.key(i));
			if (tag == null || !field.editable()) continue;
			values.put(field.key(), text(field, tag));
		}
		return values;
	}

	private static String text(Field field, Tag tag) {
		return switch (tag) {
			case ByteTag b when field.kind() == OptionKind.BOOLEAN -> b.byteValue() != 0 ? "true" : "false";
			case NumericTag n when field.kind() == OptionKind.WHOLE -> Long.toString(Math.round(n.doubleValue()));
			case NumericTag n -> OptionSpec.number(Double.parseDouble(Float.toString(n.floatValue())));
			case StringTag s -> s.value();
			default -> tag.toString();
		};
	}

	public static List<String> path(CompoundTag payload) {
		String page = payload.getStringOr("page", "");
		return page.isEmpty() ? List.of() : List.of(page.split("/"));
	}
}
```
(`Double.parseDouble(Float.toString(f))` turns the slider's `0.25f` into `0.25`, not `0.25000000372…`. Check the accessor names of `ByteTag`, `NumericTag`, `StringTag` in 26.3 with `javap` — they are records/sealed types in recent versions; adjust `byteValue()`/`value()` to what exists.)

- [ ] **Step 3: Write `Actions`**

```java
package nu.metacraft.config.screen;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.config.source.*;
import nu.metacraft.lib.custom_message.CustomMessageHandler;
import nu.metacraft.lib.custom_message.CustomMessageRegistry;
import nu.metacraft.lib.util.PotentialPlayer;   // check the real package of PotentialPlayer

import java.util.*;

/** The screen's buttons, handled on the server; every press checks the permission again. */
public final class Actions {
	public static final String PERMISSION = "metacraft.config";
	public static final int LEVEL = 3;

	private Actions() {}

	public static void register() {
		Registry.register(CustomMessageRegistry.REGISTRY, Pages.OPEN, handler(Actions::open));
		Registry.register(CustomMessageRegistry.REGISTRY, Pages.SAVE, handler(Actions::save));
		Registry.register(CustomMessageRegistry.REGISTRY, Pages.RESET, handler(Actions::reset));
	}

	public static boolean allowed(ServerPlayer player) {
		return Permissions.check(player.createCommandSourceStack(), PERMISSION, LEVEL);
	}

	public static void openMain(ServerPlayer player) {
		player.openDialog(Holder.direct(Pages.main(Sources.all())));
	}

	public static void openConfig(ServerPlayer player, ConfigSource source, List<String> path) {
		show(player, source, path, Optional.empty(), Map.of());
	}

	private interface Press {
		void handle(ServerPlayer player, CompoundTag payload);
	}

	private static CustomMessageHandler handler(Press press) {
		return (payload, server, potential) -> potential.getPlayer(server).ifPresent(player -> {
			if (!allowed(player)) return;
			CompoundTag tag = payload.flatMap(Tag::asCompound).orElseGet(CompoundTag::new);
			press.handle(player, tag);
		});
	}

	private static void open(ServerPlayer player, CompoundTag payload) {
		String id = payload.getStringOr("source", "");
		if (id.isEmpty()) {
			openMain(player);
			return;
		}
		withSource(player, id, source -> openConfig(player, source, Payloads.path(payload)));
	}

	private static void save(ServerPlayer player, CompoundTag payload) {
		withSource(player, payload.getStringOr("source", ""), source -> {
			List<String> path = Payloads.path(payload);
			Page page;
			try {
				page = source.page(path);
			} catch (IllegalArgumentException e) {
				openMain(player);
				return;
			}
			Map<String, String> values = Payloads.values(page, payload);
			switch (source.apply(path, values, payload.getIntOr("hash", 0))) {
				case EditOutcome.Saved saved -> show(player, source, path,
						Optional.of(Component.literal("Saved.").withStyle(ChatFormatting.GREEN)), Map.of());
				case EditOutcome.Refused refused -> show(player, source, path,
						Optional.of(Component.literal(refused.message()).withStyle(ChatFormatting.RED)), values);
				case EditOutcome.Stale stale -> show(player, source, path,
						Optional.of(Component.literal("Someone changed this config since you opened it; here it is as it is now.")
								.withStyle(ChatFormatting.GOLD)), Map.of());
			}
		});
	}

	private static void reset(ServerPlayer player, CompoundTag payload) {
		withSource(player, payload.getStringOr("source", ""), source -> {
			List<String> path = Payloads.path(payload);
			if (!payload.getBooleanOr("confirm", false)) {
				player.openDialog(Holder.direct(Pages.confirmReset(source, path)));
				return;
			}
			EditOutcome outcome = source.reset(path, payload.getIntOr("hash", 0));
			Component message = switch (outcome) {
				case EditOutcome.Saved saved -> Component.literal("Reset to defaults.").withStyle(ChatFormatting.GREEN);
				case EditOutcome.Refused refused -> Component.literal(refused.message()).withStyle(ChatFormatting.RED);
				case EditOutcome.Stale stale -> Component.literal("Someone changed this config meanwhile; nothing was reset.").withStyle(ChatFormatting.GOLD);
			};
			show(player, source, path, Optional.of(message), Map.of());
		});
	}

	private static void show(ServerPlayer player, ConfigSource source, List<String> path, Optional<Component> message, Map<String, String> typed) {
		try {
			player.openDialog(Holder.direct(Pages.config(source, path, message, typed)));
		} catch (IllegalArgumentException e) {
			openMain(player);
		}
	}

	private static void withSource(ServerPlayer player, String id, java.util.function.Consumer<ConfigSource> then) {
		Sources.find(id).ifPresentOrElse(then, () -> openMain(player));
	}
}
```
Check before compiling: the package of `PotentialPlayer` (`grep -rn "class PotentialPlayer\|record PotentialPlayer\|interface PotentialPlayer" mods/metacraft-lib/src`), whether `CustomMessageHandler` is a functional interface (it has one abstract method — the lambda works), `Tag::asCompound` and `CompoundTag#getStringOr/getIntOr/getBooleanOr` in 26.3 (`javap`), and `ServerPlayer#createCommandSourceStack`. Adjust names to what exists.

- [ ] **Step 4: Write `ConfigCommand` and wire the entrypoint**

```java
package nu.metacraft.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import nu.metacraft.config.screen.Actions;
import nu.metacraft.config.source.ConfigSource;
import nu.metacraft.config.source.Sources;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** {@code /config} opens the list; {@code /config <id>} one config. */
public final class ConfigCommand {
	private ConfigCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("config")
				.requires(Permissions.require(Actions.PERMISSION, Actions.LEVEL))
				.executes(ctx -> {
					Actions.openMain(ctx.getSource().getPlayerOrException());
					return 1;
				})
				.then(argument("config", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Sources.all().stream().map(ConfigSource::id), builder))
						.executes(ctx -> {
							String id = StringArgumentType.getString(ctx, "config");
							ConfigSource source = Sources.find(id).orElse(null);
							if (source == null) {
								ctx.getSource().sendFailure(Component.literal("No config called " + id));
								return 0;
							}
							Actions.openConfig(ctx.getSource().getPlayerOrException(), source, List.of());
							return 1;
						})));
	}
}
```
`StringArgumentType.word()` does not accept `-` in ids like `faster-minecarts`? It accepts `[0-9A-Za-z_\-.+]`, so ids from file names are fine.

`MetacraftConfig.onInitialize`:
```java
	@Override
	public void onInitialize() {
		Actions.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ConfigCommand.register(dispatcher));
		// Operators hear about a config file that does not load when they join.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (!Actions.allowed(player)) return;
			for (ConfigSource source : Sources.all()) {
				source.loadError().ifPresent(error -> player.sendSystemMessage(Component.literal(
						"[config] " + source.name() + " does not load: " + error + " — /config " + source.id()).withStyle(ChatFormatting.RED)));
			}
		});
	}
```

- [ ] **Step 5: Run the tests, then try it in a server**

Run: `./gradlew :mods:metacraft-config:test`
Expected: PASS.
Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL.
Manual check (records what the client test in Task 11 automates): `./gradlew :mods:metacraft-config:runClient`, open a singleplayer world with cheats, run `/config`, open Faster Minecarts, change "Top speed" to `70`, Save → "Saved.", `config/faster_minecarts.json` has `70.0`; type `fast` → red error, value kept in the box, file unchanged.

- [ ] **Step 6: Commit**

```bash
git add mods/metacraft-config/src
git commit -m "metacraft-config: /config, saving and resetting from the dialog, join notice for broken files"
```

---

### Task 10: The PolyDecorations adapter

**Files:**
- Create: `mods/metacraft-config/src/main/java/nu/metacraft/config/source/PolyDecorationsSource.java`
- Modify: `mods/metacraft-config/src/main/java/nu/metacraft/config/MetacraftConfig.java`
- Test: `mods/metacraft-config/src/test/java/TestPolyDecorationsSource.java`

**Interfaces:**
- Consumes: `ConfigSource`, `Page`, `Field`, `EditOutcome` (Task 7).
- Produces: `PolyDecorationsSource.detect(Path file) : Optional<PolyDecorationsSource>` — present when the file exists and has a `features` object (the Froosty11 fork, branch `metacraft-config`); id `polydecorations`.

- [ ] **Step 1: Write the failing test**

```java
import com.google.gson.JsonParser;
import nu.metacraft.config.source.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestPolyDecorationsSource {
	private static final String FORK_FILE = """
			{
				"features": {
					"canvas": true,
					"bench": false,
					"statues": false
				},
				"something_else": 1
			}
			""";

	@Test
	public void onlyTheForksFormatIsPickedUp(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		assertTrue(PolyDecorationsSource.detect(file).isEmpty());   // no file
		Files.writeString(file, "{\"canvas\": true}");
		assertTrue(PolyDecorationsSource.detect(file).isEmpty());   // not the fork's format
		Files.writeString(file, FORK_FILE);
		assertTrue(PolyDecorationsSource.detect(file).isPresent());
	}

	@Test
	public void everyFeatureIsARestartToggle(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		Page page = PolyDecorationsSource.detect(file).orElseThrow().page(List.of());
		assertEquals(List.of("canvas", "bench", "statues"), page.fields().stream().map(Field::key).toList());
		assertTrue(page.fields().stream().allMatch(f -> f.restart() && f.kind() == nu.metacraft.lib.config.describe.OptionKind.BOOLEAN));
		assertEquals("false", page.fields().get(1).value());
	}

	@Test
	public void writesBackKeepingOtherKeys(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		ConfigSource source = PolyDecorationsSource.detect(file).orElseThrow();
		assertEquals(new EditOutcome.Saved(), source.apply(List.of(), Map.of("bench", "true"), source.hash()));
		var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertTrue(json.getAsJsonObject("features").get("bench").getAsBoolean());
		assertEquals(1, json.get("something_else").getAsInt());
		assertEquals(List.of("bench"), source.pendingRestart().stream().map(s -> s.split(" ")[0].toLowerCase()).toList());
	}

	@Test
	public void refusesStaleAndBadValues(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		ConfigSource source = PolyDecorationsSource.detect(file).orElseThrow();
		assertEquals(new EditOutcome.Stale(), source.apply(List.of(), Map.of("bench", "true"), source.hash() + 1));
		assertInstanceOf(EditOutcome.Refused.class, source.apply(List.of(), Map.of("bench", "maybe"), source.hash()));
		assertInstanceOf(EditOutcome.Refused.class, source.apply(List.of(), Map.of("warp_drive", "true"), source.hash()));
	}

	@Test
	public void aBrokenFileIsReportedNotOverwrittenUntilSaved(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		ConfigSource source = PolyDecorationsSource.detect(file).orElseThrow();
		Files.writeString(file, "{ broken");
		assertTrue(source.loadError().isPresent());
		assertEquals("{ broken", Files.readString(file));
	}
}
```

Run: `./gradlew :mods:metacraft-config:test --tests TestPolyDecorationsSource`
Expected: compilation fails.

- [ ] **Step 2: Write `PolyDecorationsSource`**

```java
package nu.metacraft.config.source;

import com.google.gson.*;
import nu.metacraft.lib.config.describe.OptionKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * PolyDecorations' feature switches ({@code config/polydecorations.json}, the Froosty11 fork's
 * format: a {@code features} object of booleans). Every feature is registered at startup, so every
 * change applies after a restart. The file is read each time, so hand edits show up at once.
 */
public final class PolyDecorationsSource implements ConfigSource {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final Map<String, Boolean> atStart;

	private PolyDecorationsSource(Path file, Map<String, Boolean> atStart) {
		this.file = file;
		this.atStart = atStart;
	}

	public static Optional<PolyDecorationsSource> detect(Path file) {
		return read(file).map(json -> new PolyDecorationsSource(file, features(json)));
	}

	private static Optional<JsonObject> read(Path file) {
		try {
			if (!Files.exists(file)) return Optional.empty();
			JsonElement json = JsonParser.parseString(Files.readString(file));
			if (!json.isJsonObject() || !json.getAsJsonObject().has("features") || !json.getAsJsonObject().get("features").isJsonObject()) {
				return Optional.empty();
			}
			return Optional.of(json.getAsJsonObject());
		} catch (IOException | JsonParseException e) {
			return Optional.empty();
		}
	}

	private static Map<String, Boolean> features(JsonObject json) {
		Map<String, Boolean> features = new LinkedHashMap<>();
		for (var entry : json.getAsJsonObject("features").entrySet()) {
			if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isBoolean()) {
				features.put(entry.getKey(), entry.getValue().getAsBoolean());
			}
		}
		return features;
	}

	private String text() {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			return "";
		}
	}

	@Override public String id() { return "polydecorations"; }
	@Override public String name() { return "PolyDecorations"; }
	@Override public String description() { return "Which PolyDecorations features exist. Applies after a restart."; }
	@Override public int hash() { return text().hashCode(); }

	@Override
	public Optional<String> loadError() {
		return read(file).isPresent() ? Optional.empty() : Optional.of("the file is missing, or is not the fork's format");
	}

	@Override
	public List<String> pendingRestart() {
		Map<String, Boolean> now = read(file).map(PolyDecorationsSource::features).orElse(atStart);
		List<String> pending = new ArrayList<>();
		now.forEach((feature, on) -> {
			if (!on.equals(atStart.get(feature))) pending.add(label(feature) + (on ? " on" : " off"));
		});
		return pending;
	}

	@Override
	public Page page(List<String> path) {
		if (!path.isEmpty()) throw new IllegalArgumentException("PolyDecorations has one page");
		Map<String, Boolean> now = read(file).map(PolyDecorationsSource::features).orElse(atStart);
		List<Field> fields = new ArrayList<>();
		now.forEach((feature, on) -> fields.add(new Field(feature, label(feature), OptionKind.BOOLEAN, on.toString(),
				0, 1, 0, false, List.of(), true, true)));
		return new Page(List.of(), "PolyDecorations", fields, List.of());
	}

	@Override
	public EditOutcome apply(List<String> path, Map<String, String> values, int expectedHash) {
		if (expectedHash != hash()) return new EditOutcome.Stale();
		JsonObject json = read(file).orElseGet(() -> {
			JsonObject fresh = new JsonObject();
			fresh.add("features", new JsonObject());
			return fresh;
		});
		JsonObject features = json.getAsJsonObject("features");
		for (var entry : values.entrySet()) {
			if (!features.has(entry.getKey())) return new EditOutcome.Refused("PolyDecorations has no feature " + entry.getKey());
			String value = entry.getValue().trim();
			if (!value.equals("true") && !value.equals("false")) return new EditOutcome.Refused(entry.getKey() + ": \"" + value + "\" is not true or false");
			features.addProperty(entry.getKey(), Boolean.parseBoolean(value));
		}
		try {
			Files.writeString(file, GSON.toJson(json));
		} catch (IOException e) {
			return new EditOutcome.Refused("cannot write " + file + ": " + e.getMessage());
		}
		return new EditOutcome.Saved();
	}

	@Override
	public EditOutcome reset(List<String> path, int expectedHash) {
		Map<String, String> allOn = new LinkedHashMap<>();
		page(path).fields().forEach(field -> allOn.put(field.key(), "true"));
		return apply(path, allOn, expectedHash);   // the fork's default is every feature on
	}

	private static String label(String feature) {
		String words = feature.replace('_', ' ');
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}
}
```

- [ ] **Step 3: Register it**

In `MetacraftConfig.onInitialize`, after `Actions.register()`:
```java
		if (FabricLoader.getInstance().isModLoaded("polydecorations")) {
			PolyDecorationsSource.detect(FabricLoader.getInstance().getConfigDir().resolve("polydecorations.json"))
					.ifPresent(Sources::addAdapter);
		}
```
PolyDecorations writes its file during its own initialisation. If metacraft-config initialises first, the file may not exist yet on a first start: move the detection into `ServerLifecycleEvents.SERVER_STARTING` so every mod has initialised.

- [ ] **Step 4: Run to see it pass**

Run: `./gradlew :mods:metacraft-config:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mods/metacraft-config/src
git commit -m "metacraft-config: PolyDecorations' feature switches, for the Froosty11 fork"
```

---

### Task 11: Screenshots from a vanilla client

**Files:**
- Modify: `mods/metacraft-config/build.gradle`
- Create: `mods/metacraft-config/src/gametest/java/nu/metacraft/config/clienttest/ConfigScreenshots.java`, `mods/metacraft-config/src/gametest/resources/fabric.mod.json`
- Create: `mods/metacraft-config/docs/` (screenshots copied in for review)

**Interfaces:**
- Consumes: `/config`, `/config faster_minecarts` (Task 9), faster-minecarts' described config (Task 6).

- [ ] **Step 1: Set up the client test source set**

Append to `mods/metacraft-config/build.gradle`:
```groovy
// Client screenshot test: a vanilla client opens /config and photographs it. Source set src/gametest,
// task runClientGameTest (needs a display).
fabricApi {
	configureTests {
		createSourceSet = true
		modId = "metacraft-config-clienttest"
		enableGameTests = false
		enableClientGameTests = true
		eula = true
		username = "Tester"
	}
}

dependencies {
	// The screen's first real config.
	gametestRuntimeOnly(project(path: ":mods:faster-minecarts"))
}
```

`src/gametest/resources/fabric.mod.json`:
```json
{
	"schemaVersion": 1,
	"id": "metacraft-config-clienttest",
	"version": "1.0.0",
	"name": "METAcraft Config client tests",
	"description": "Screenshots: /config as a vanilla client sees it.",
	"license": "Apache-2.0",
	"environment": "client",
	"entrypoints": {
		"fabric-client-gametest": ["nu.metacraft.config.clienttest.ConfigScreenshots"]
	},
	"depends": {
		"metacraft-config": "*",
		"fabric-client-gametest-api-v1": "*"
	}
}
```

- [ ] **Step 2: Write the test**

```java
package nu.metacraft.config.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.dialog.DialogScreen;

import java.nio.file.Files;
import java.util.Properties;

/**
 * /config as a vanilla client sees it: the list, faster-minecarts' page, and a page whose file does
 * not load. Asserts only that each opens as a dialog; the pictures are for a person to judge.
 */
public final class ConfigScreenshots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25604");
		props.setProperty("level-type", "minecraft:flat");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ctx.getInput().resizeWindow(1920, 1080);
				conn.waitForChunksRender();
				server.runCommand("op Tester");
				shoot(ctx, server, conn, "execute as Tester run config", "config_main");
				shoot(ctx, server, conn, "execute as Tester run config faster_minecarts", "config_faster_minecarts");
				server.runOnServer(mc -> {
					try {
						Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("faster_minecarts.json"), "{ \"max_minecart_speed\": -5 }");
					} catch (Exception e) {
						throw new AssertionError(e);
					}
				});
				server.runCommand("reload");
				ctx.waitTicks(40);
				shoot(ctx, server, conn, "execute as Tester run config faster_minecarts", "config_load_error");
			}
		}
	}

	private static void shoot(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, String command, String name) {
		ctx.runOnClient(client -> client.gui.setScreen(null));
		ctx.waitTicks(5);
		server.runCommand(command);
		conn.waitForClientboundPackets();
		ctx.waitFor(client -> client.gui.screen() instanceof DialogScreen<?>, 20 * 10);
		ctx.runOnClient(client -> client.gui.toastManager().clear());
		ctx.getInput().setCursorPos(0, 0);
		ctx.waitTicks(5);
		ctx.takeScreenshot(TestScreenshotOptions.of(name));
	}
}
```
The dedicated test server shares the dev config dir only if `getConfigDir()` resolves to the server's run dir; if the written file does not reach the server, write it with `server.runOnServer` using `mc.getServerDirectory().resolve("config/faster_minecarts.json")` instead. faster-minecarts' container must reload on `/reload` for the error page to appear; if its builder has neither `reloadBeforeServer()` nor `reloadAfterServer()`, add `.reloadAfterServer()` in Task 6's builder chain.

- [ ] **Step 3: Run it and look at the pictures**

Run: `./gradlew :mods:metacraft-config:runClientGameTest`
Expected: BUILD SUCCESSFUL; screenshots in `mods/metacraft-config/build/run/clientGameTest/screenshots/`: `*_config_main.png`, `*_config_faster_minecarts.png`, `*_config_load_error.png`. Open each and check: the list shows Faster Minecarts; its page has a toggle, typed number fields with "(0 – ∞)", an Optional field, the mode as a choice marked ⟳, and Save / Reset / Back / Close; the error page shows the red load error above the inputs.

Copy them into `mods/metacraft-config/docs/` (without the numeric prefix).

- [ ] **Step 4: Commit**

```bash
git add mods/metacraft-config
git commit -m "metacraft-config: screenshots of /config from a vanilla client"
```

---

### Task 12: Whole-branch check

- [ ] **Step 1:** `./gradlew runDatagen && ./gradlew build` (as CI does) — Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 2:** `git diff upstream/dev --stat` — only metacraft-lib's config package, faster-minecarts, the new mod, `settings.gradle`, `gradle.properties`, and `docs/superpowers/`. Nothing else touched.
- [ ] **Step 3:** Write `mods/metacraft-config/README.md`: what `/config` does, how a mod joins (`@Config`, `@Option`, `DEFAULT`, `.describedBy(...)`), the slider rule, the changed load behaviour (a broken file keeps the server on its last good settings), the PolyDecorations adapter. Commit.
