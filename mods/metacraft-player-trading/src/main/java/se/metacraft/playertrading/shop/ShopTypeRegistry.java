package se.metacraft.playertrading.shop;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import se.metacraft.playertrading.PlayerTrading;

public class ShopTypeRegistry {

	public static final Registry<MapCodec<? extends ShopType>> REGISTRY = FabricRegistryBuilder.<MapCodec<? extends ShopType>>create(
		ResourceKey.createRegistryKey(PlayerTrading.getID("shop_type"))
	).buildAndRegister();


	public static void init() {
		register("player", ShopType.Player.CODEC);
		register("admin", ShopType.Admin.CODEC);
		register("limited_uses", ShopType.LimitedUses.CODEC);
	}

	private static void register(String id, MapCodec<? extends ShopType> codec) {
		Registry.register(REGISTRY, PlayerTrading.getID(id), codec);
	}

}
