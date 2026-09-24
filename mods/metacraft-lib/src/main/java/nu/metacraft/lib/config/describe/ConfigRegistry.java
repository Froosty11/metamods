package nu.metacraft.lib.config.describe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.util.*;

/** Every described config, by id (its file name without {@code .json}), in registration order. */
public final class ConfigRegistry {
	private static final Map<String, DescribedConfig<?>> CONFIGS = new LinkedHashMap<>();

	private ConfigRegistry() {}

	/** Wires {@link #serverStarted()} and {@link #serverStopped()} to the server's lifecycle. */
	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> serverStarted());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> serverStopped());
	}

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

	/** Loads every described config, so each records the value the server started with. */
	public static synchronized void serverStarted() {
		CONFIGS.values().forEach(DescribedConfig::get);
	}

	/** Forgets every described config's start value; the next {@link #serverStarted()} records a fresh one. */
	public static synchronized void serverStopped() {
		CONFIGS.values().forEach(DescribedConfig::clearStart);
	}

	/** Tests register the same fixtures over and over. */
	public static synchronized void clearForTests() {
		CONFIGS.clear();
	}
}
