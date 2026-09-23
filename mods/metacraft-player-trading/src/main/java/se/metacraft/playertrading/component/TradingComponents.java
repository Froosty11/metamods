package se.metacraft.playertrading.component;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import se.metacraft.playertrading.PlayerTrading;
import se.metacraft.playertrading.component.components.ShopKey;
import se.metacraft.playertrading.shop.Shop;

import java.util.UUID;
import java.util.function.UnaryOperator;

public class TradingComponents {

	public static final DataComponentType<Shop> SHOP = register("shop", builder -> builder.persistent(Shop.CODEC));
	public static final DataComponentType<ShopKey> SHOP_KEY = register("shop_key", builder -> builder.persistent(ShopKey.CODEC));

	public static void init() {

	}

	private static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE, PlayerTrading.getID(id),
			builderOperator.apply(DataComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
