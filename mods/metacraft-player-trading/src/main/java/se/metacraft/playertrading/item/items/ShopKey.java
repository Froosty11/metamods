package se.metacraft.playertrading.item.items;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ShopKey extends Item implements PolymerItem {

	public ShopKey(Properties properties) {
		super(properties);
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return Items.GOLD_INGOT;
	}

}
