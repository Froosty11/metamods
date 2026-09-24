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
