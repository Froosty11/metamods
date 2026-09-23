package se.metacraft.playertrading.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import net.minecraft.util.datafix.schemas.V1460;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.playertrading.PlayerTrading;
import se.metacraft.playertrading.block.TradingBlockEntities;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;
import se.metacraft.playertrading.shop.Shop;

import java.util.Map;
import java.util.function.Supplier;

@Mixin(V1460.class)
public abstract class V1460Mixin extends NamespacedSchema {

	public V1460Mixin(int versionKey, Schema parent) {
		super(versionKey, parent);
	}

	@Inject(
		method = "registerBlockEntities",
		at = @At("RETURN")
	)
	public void registerBlockEntities(
			Schema schema, CallbackInfoReturnable<Map<String, Supplier<TypeTemplate>>> cir,
			@Local(name = "map") Map<String, Supplier<TypeTemplate>> map
	) {
		schema.register(
			map,
			PlayerTrading.getID("shop").toString(),
			() -> DSL.optionalFields(
				ShopBlockEntity.SHOP, DSL.optionalFields(
					Shop.OFFERS, DSL.list(
						DSL.optionalFields(
							Shop.SimpleOffer.PRICE, DSL.or(
								DSL.list(
									References.ITEM_STACK.in(schema)
								),
								References.ITEM_STACK.in(schema)
							),
							Shop.SimpleOffer.RESULT, References.ITEM_STACK.in(schema)
						)
					)
				)
			)
		);
	}

}
