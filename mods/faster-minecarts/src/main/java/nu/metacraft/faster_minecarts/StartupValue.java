package nu.metacraft.faster_minecarts;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A config value read once, on first use, and kept for the rest of the run: for options that only
 * take effect after a restart, so a reload or a {@code /config} save cannot flip them halfway.
 */
public final class StartupValue<T> {
	private final Supplier<T> reader;
	private volatile T value;

	public StartupValue(Supplier<T> reader) {
		this.reader = reader;
	}

	public T get() {
		T read = value;
		if (read == null) {
			synchronized (this) {
				if (value == null) value = Objects.requireNonNull(reader.get());
				read = value;
			}
		}
		return read;
	}
}
