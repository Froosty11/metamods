package metacraft.ovvar.compat.danse;

import metacraft.ovvar.Ovvar;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The compat layer's one entry point, called from {@link Ovvar}'s init behind
 * {@link DanseHooks#active()}. It does nothing but remember the server: the pixels for a chestplate
 * worn <em>over</em> an ovve have to find the wearer's ovve from the wearer's id, and Danse's
 * {@code TextureCache} is static and has no context to ask.
 */
public final class DanseCompat {
	private DanseCompat() {}

	private static volatile @Nullable MinecraftServer server;

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			DansePixels.clearCache();
		});
		Ovvar.LOGGER.info("[ovvar] Danse is here: gestures will wear the ovve");
	}

	static @Nullable MinecraftServer server() {
		return server;
	}
}
