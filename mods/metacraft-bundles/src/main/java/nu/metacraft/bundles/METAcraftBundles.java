package nu.metacraft.bundles;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.bundles.util.BundleHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class METAcraftBundles implements ModInitializer {

	public static final String MODID = "metacraft-bundles";
	public static final String NAMESPACE = "metacraft";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	/**
	 * {@code bundleRendering} as it was when first read. Registering the mod's resource pack assets as
	 * required happens once, here, so every other reader uses this and not a live
	 * {@code BundleConfig.getInstance()} - a {@code /config} save can't turn rendering on without the
	 * resource pack that was (or wasn't) registered at startup.
	 */
	private static boolean bundleRenderingAtStartup;

	@Override
	public void onInitialize() {
		bundleRenderingAtStartup = BundleConfig.getInstance().bundleRendering();

		BundleComponents.init();
		BundleHelper.init();

		if (bundleRenderingAtStartup) {
			PolymerResourcePackUtils.addModAssets(MODID);
			PolymerResourcePackUtils.markAsRequired();
		}
	}

	public static boolean bundleRenderingAtStartup() {
		return bundleRenderingAtStartup;
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}
