package nu.metacraft.config.source;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One config the screen can show and edit: a described config, or an adapter for another mod's file. */
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
