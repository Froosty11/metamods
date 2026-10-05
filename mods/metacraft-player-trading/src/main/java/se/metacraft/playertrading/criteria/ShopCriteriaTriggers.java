package se.metacraft.playertrading.criteria;

import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import se.metacraft.playertrading.PlayerTrading;

import java.util.function.Consumer;

public class ShopCriteriaTriggers {

	public static final ContextKeySet SHOP_CONTEXT = registerKeySet(
			"shop", builder -> builder
					.required(LootContextParams.THIS_ENTITY)
					.required(LootContextParams.ORIGIN)
					.required(LootContextParams.BLOCK_STATE)
					.required(LootContextParams.BLOCK_ENTITY)
	);


	public static final ShopTradeTrigger TRADE = register("shop_trade", new ShopTradeTrigger());


	public static void init() {

	}


	public static <T extends CriterionTrigger<?>> T register(String string, T criterionTrigger) {
		return Registry.register(
				BuiltInRegistries.TRIGGER_TYPES,
				PlayerTrading.getID(string),
				criterionTrigger
		);
	}

	private static ContextKeySet registerKeySet(String string, Consumer<ContextKeySet.Builder> consumer) {
		ContextKeySet.Builder builder = new ContextKeySet.Builder();
		consumer.accept(builder);
		ContextKeySet contextKeySet = builder.build();
		Identifier id = PlayerTrading.getID(string);
		return Registry.register(BuiltInRegistries.CONTEXT_KEY_SET, id, contextKeySet);
	}
}
