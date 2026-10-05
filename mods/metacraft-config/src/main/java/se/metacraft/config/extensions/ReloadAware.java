package se.metacraft.config.extensions;

import se.metacraft.config.container.ReloadCause;

public interface ReloadAware {

	void beforeReload(ReloadCause cause);

}
