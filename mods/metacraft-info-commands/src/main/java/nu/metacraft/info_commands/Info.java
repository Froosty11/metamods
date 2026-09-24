package nu.metacraft.info_commands;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.lib.config.container.ConfigContainer;

import java.nio.file.Path;

public class Info implements ModInitializer {

	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("metacraft-info-commands.json");
	private static final ConfigContainer<InfoConfig> config = ConfigContainer.Builder.create(
			InfoConfig.CODEC, () -> InfoConfig.DEFAULT
	).reloadBeforeServer().describedBy(InfoConfig.class).build(configPath);

	public static final Logger LOGGER = LogManager.getLogger("METAcraft-info-commands");

	private final InfoMessages infoMessages = new InfoMessages();

	@Override
	public void onInitialize() {
		Commands.init();

		ServerTickEvents.END_SERVER_TICK.register(server -> this.infoMessages.tick(server, getConfig()));
	}

	/**
	 * Returns the config.
	 * DO NOT CACHE THIS IN VARIABLES FOR LONGER PERIODS OF TIME!
	 * @return The config.
	 */
	public static InfoConfig getConfig() {
		return config.get();
	}
}
