package metacraft.ovvar.compat.danse;

import de.tomalbrc.danse.api.BodyLayers;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import metacraft.ovvar.Ovvar;

/**
 * The compat layer's one entry point, called from {@link Ovvar}'s init behind
 * {@link DanseHooks#active()}: registers the body-layer provider with our Danse fork and adds the
 * layer models to the pack.
 */
public final class DanseCompat {
	private DanseCompat() {}

	public static void init() {
		BodyLayers.register(DanseLayers.INSTANCE);
		PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> DanseModels.write(builder::addData));
		Ovvar.LOGGER.info("[ovvar] Danse is here: gestures will wear the ovve");
	}
}
