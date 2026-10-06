package se.metacraft.config.extensions;

import se.metacraft.config.container.ReloadCause;

import java.util.Optional;

public interface LoadAware {

	void afterLoad(Optional<ReloadCause> cause);

}
