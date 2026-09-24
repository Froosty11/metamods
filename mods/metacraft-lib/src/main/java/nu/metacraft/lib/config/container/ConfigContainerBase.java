package nu.metacraft.lib.config.container;

import java.util.Optional;
import java.util.function.Consumer;

public interface ConfigContainerBase<T> {

	/**
	 * Reloads the config.
	 * This means that the next time {@link ConfigContainer#get()} is called, the config will be loaded again.
	 * Normally reloaded using {@link ReloadCause#DEFAULT}
	 */
	default void reload() {
		reload(ReloadCause.DEFAULT);
	}

	/**
	 * Reloads the config.
	 * This means that the next time {@link ConfigContainer#get()} is called, the config will be loaded again.
	 * @param reloadCause The reason for the reload.
	 */
	void reload(ReloadCause reloadCause);

	/**
	 * Manually saves the config.
	 */
	void save();


	void addReloadHandler(Consumer<ReloadCause> handler);

	/** Why the file could not be read, while the last good value (or the default) is in use. */
	default Optional<String> loadError() {
		return Optional.empty();
	}

}
