package se.metacraft.playertrading;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import se.metacraft.playertrading.block.blocks.BaseShopBlock;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;

public class Events {

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			var blockPos = hitResult.getBlockPos();
			if (player.getItemInHand(hand).getItem() instanceof BlockItem && player.isCrouching()) return InteractionResult.PASS;
			if (BaseShopBlock.validShopContainer(level, blockPos) && hand == InteractionHand.MAIN_HAND) {
				return ShopBlockEntity.getConnectedTo(level, blockPos).map(
					shop -> shop.onUse(player, blockPos)
				).orElse(InteractionResult.PASS);
			}
			return InteractionResult.PASS;
		});
	}

}
