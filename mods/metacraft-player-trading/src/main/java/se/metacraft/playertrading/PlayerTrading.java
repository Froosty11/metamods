package se.metacraft.playertrading;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.playertrading.block.TradingBlockEntities;
import se.metacraft.playertrading.block.TradingBlocks;
import se.metacraft.playertrading.component.TradingComponents;
import se.metacraft.playertrading.criteria.ShopCriteriaTriggers;
import se.metacraft.playertrading.item.TradingItems;
import se.metacraft.playertrading.shop.ShopTypeRegistry;

public class PlayerTrading implements ModInitializer {
	public static final String NAMESPACE = "metacraft";
	public static final Logger LOGGER = LogManager.getLogger(NAMESPACE + "-playertrading");
	public static final String MODID = "metacraft-player-trading";

	@Override
	public void onInitialize() {
		ShopCriteriaTriggers.init();
		ShopTypeRegistry.init();
		TradingComponents.init();
		TradingBlocks.init();
		TradingBlockEntities.init();
		TradingItems.init();
		Events.init();

		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
	}

	public static Identifier getID(String value) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, value);
	}
}