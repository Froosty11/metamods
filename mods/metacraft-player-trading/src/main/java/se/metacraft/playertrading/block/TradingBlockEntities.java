package se.metacraft.playertrading.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import se.metacraft.playertrading.PlayerTrading;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;

import java.util.Set;

public class TradingBlockEntities {

	public static final BlockEntityType<ShopBlockEntity> SHOP = register(
		"shop", new BlockEntityType<>(ShopBlockEntity::new, Set.of(TradingBlocks.SHOP.value(), TradingBlocks.SHOP_WALL.value()))
	);

	public static void init() {

	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String id, BlockEntityType<T> type) {
		PolymerBlockUtils.registerBlockEntity(type);
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PlayerTrading.getID(id), type);
	}

}
