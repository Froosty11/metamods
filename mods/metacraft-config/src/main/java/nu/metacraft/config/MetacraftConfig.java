package nu.metacraft.config;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** {@code /config}: the server's described configs, edited in vanilla dialogs. */
public final class MetacraftConfig implements ModInitializer {
	public static final String MOD_ID = "metacraft-config";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
	}
}
