package nu.metacraft.config;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.config.screen.Actions;
import nu.metacraft.config.source.ConfigSource;
import nu.metacraft.config.source.PolyDecorationsSource;
import nu.metacraft.config.source.Sources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/** {@code /config}: the server's described configs, edited in vanilla dialogs. */
public final class MetacraftConfig implements ModInitializer {
	public static final String MOD_ID = "metacraft-config";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	// PolyDecorations writes its config during its own init, which may run after ours: detect it once the
	// server is starting, when every mod has initialised. Guarded so singleplayer's repeat server starts
	// in one JVM don't register the adapter twice.
	private final AtomicBoolean polyDecorationsRegistered = new AtomicBoolean();

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
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (FabricLoader.getInstance().isModLoaded("polydecorations") && polyDecorationsRegistered.compareAndSet(false, true)) {
				PolyDecorationsSource.detect(FabricLoader.getInstance().getConfigDir().resolve("polydecorations.json"))
						.ifPresent(Sources::addAdapter);
			}
		});
	}
}
