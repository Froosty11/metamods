package se.metacraft.playertrading.block;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.PushReaction;
import se.metacraft.playertrading.PlayerTrading;
import se.metacraft.playertrading.block.blocks.ShopBlock;
import se.metacraft.playertrading.block.blocks.WallShopBlock;

import java.util.function.Function;

public class TradingBlocks {

	public static final Holder.Reference<Block> SHOP = register(
		"shop", ShopBlock::new,
		BlockBehaviour.Properties.of().instrument(
			NoteBlockInstrument.CUSTOM_HEAD
		).strength(1.0F).pushReaction(PushReaction.POPPED).noOcclusion()
	);

	public static final Holder.Reference<Block> SHOP_WALL = register(
		"shop_wall", WallShopBlock::new,
		BlockBehaviour.Properties.of().overrideLootTable(
			SHOP.value().getLootTable()
		).overrideDescription(
			SHOP.value().getDescriptionId()
		).strength(1.0F).pushReaction(PushReaction.POPPED).noOcclusion()
	);

	public static void init() {

	}

	private static Holder.Reference<Block> register(String id, Function<BlockBehaviour.Properties, Block> creator, BlockBehaviour.Properties settings) {
		var key = ResourceKey.create(Registries.BLOCK, PlayerTrading.getID(id));
		return Registry.registerForHolder(BuiltInRegistries.BLOCK, key, creator.apply(settings.setId(key)));
	}

}
