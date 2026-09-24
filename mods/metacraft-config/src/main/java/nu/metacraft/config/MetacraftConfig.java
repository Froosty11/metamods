package nu.metacraft.config;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.config.screen.Actions;
import nu.metacraft.config.source.ConfigSource;
import nu.metacraft.config.source.Sources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** {@code /config}: the server's described configs, edited in vanilla dialogs. */
public final class MetacraftConfig implements ModInitializer {
	public static final String MOD_ID = "metacraft-config";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Actions.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ConfigCommand.register(dispatcher));
		// Operators hear about a config file that does not load when they join.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (!Actions.allowed(player)) return;
			for (ConfigSource source : Sources.all()) {
				source.loadError().ifPresent(error -> player.sendSystemMessage(Component.literal(
						"[config] " + source.name() + " does not load: " + error + " — /config " + source.id()).withStyle(ChatFormatting.RED)));
			}
		});
	}
}
