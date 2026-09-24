package nu.metacraft.lib.config.describe;

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
