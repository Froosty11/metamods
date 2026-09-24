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
