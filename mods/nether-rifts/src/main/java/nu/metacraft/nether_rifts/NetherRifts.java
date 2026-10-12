package nu.metacraft.nether_rifts;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NetherRifts implements ModInitializer {

	public static final String MODID = "nether-rifts";
	public static final String NAMESPACE = "nether_rifts";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		NetherRiftsConfig.getInstance();
		RiftsCommand.init();

		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
	}

	public static Identifier getID(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}
}
