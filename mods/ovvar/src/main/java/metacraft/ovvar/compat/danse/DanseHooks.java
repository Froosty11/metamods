package metacraft.ovvar.compat.danse;

import metacraft.ovvar.content.OvveFeet;
import metacraft.ovvar.pack.Combos;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gesture etiquette: the few places ovvar has to hold still while Danse is animating a stand-in.
 *
 * <p>Danse plays a gesture by hiding the real player — an empty equipment packet to every watcher
 * and the invisible flag — and animating a puppet of item displays built from the equipment it
 * took <em>once</em>, at the start. Ovvar re-sends equipment on a schedule of its own
 * ({@link Combos#packLoaded} after a pack load, {@link OvveFeet} when a viewer's cuff dye changes),
 * and either of those would re-dress the invisible player in the middle of the gesture. So both
 * ask here first.
 *
 * <p><b>This is the whole of ovvar's Danse coupling outside the mixins.</b> Danse types are named
 * only inside {@link Impl}, a class that is not loaded until {@link #active()} has already said
 * Danse is here, so a server without Danse never verifies a class that mentions it. The system
 * property {@code ovvar.danse.compat=false} turns the whole layer off (this and
 * {@link DanseMixinPlugin} read the same flag), which is how the "before" screenshot is taken.
 */
public final class DanseHooks {
	private DanseHooks() {}

	/** {@code -Dovvar.danse.compat=false} disables the compat layer: hooks and mixins alike. */
	public static final String PROPERTY = "ovvar.danse.compat";

	private static final boolean ACTIVE =
			!"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"))
					&& FabricLoader.getInstance().isModLoaded("danse");

	/** Is Danse loaded and the compat layer switched on? */
	public static boolean active() {
		return ACTIVE;
	}

	/**
	 * Is this entity in the middle of a Danse gesture — that is, is Danse drawing a stand-in for
	 * them and holding their real body invisible? While that is true nothing of ours may send them
	 * equipment.
	 */
	public static boolean gesturing(LivingEntity entity) {
		return ACTIVE && entity instanceof ServerPlayer player && Impl.gesturing(player);
	}

	/**
	 * A gesture has ended (from the mixin on Danse's {@code onStop}): put the wearer's ovve back on
	 * every tracker's screen.
	 *
	 * <p>Danse's own {@code onStop} re-sends the player's <em>raw</em> equipment, which for a wearer
	 * with no boots means an empty feet slot — and the feet slot is where ovvar smuggles the legs'
	 * second dye channel, the virtual cuffs. Ovvar's cuff cache still believes that viewer has them,
	 * so without this the leg patches in the boots channel would stay dark until something else
	 * changed. Forgetting the wearer makes the next tick re-send the cuffs to everyone tracking
	 * them; the equipment re-send puts the ovve and the companion top back with the right assets.
	 */
	public static void gestureEnded(ServerPlayer player) {
		if (!ACTIVE) return;
		OvveFeet.forget(player);
		Combos.resendEquipment(player);
	}

	/** The only class in ovvar that names a Danse type; loaded lazily, behind {@link #ACTIVE}. */
	private static final class Impl {
		private Impl() {}

		static boolean gesturing(ServerPlayer player) {
			return de.tomalbrc.danse.GestureController.GESTURE_CAMS.containsKey(player.getUUID());
		}
	}
}
