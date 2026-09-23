package se.metacraft.playertrading.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.Validatable;
import net.minecraft.world.level.storage.loot.ValidationContextSource;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;

import java.util.Optional;

public class ShopTradeTrigger extends SimpleCriterionTrigger<ShopTradeTrigger.TriggerInstance> {

	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	public void trigger(ServerPlayer serverPlayer, ShopBlockEntity shop, ItemStack itemStack) {
		LootParams lootParams = new LootParams.Builder(serverPlayer.level())
				.withParameter(LootContextParams.THIS_ENTITY, serverPlayer)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(shop.getBlockPos()))
				.withParameter(LootContextParams.BLOCK_STATE, shop.getBlockState())
				.withParameter(LootContextParams.BLOCK_ENTITY, shop)
				.create(ShopCriteriaTriggers.SHOP_CONTEXT);
		var ctx = new LootContext.Builder(lootParams).create(Optional.empty());
		this.trigger(serverPlayer, triggerInstance -> triggerInstance.matches(ctx, itemStack));
	}

	public record TriggerInstance(Optional<Holder<LootItemCondition>> player, Optional<Holder<LootItemCondition>> shop, Optional<ItemPredicate> item)
			implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
								LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
								LootItemCondition.CODEC.optionalFieldOf("shop").forGetter(TriggerInstance::shop),
								ItemPredicate.CODEC.optionalFieldOf("item").forGetter(TriggerInstance::item)
						)
						.apply(instance, TriggerInstance::new)
		);

		public boolean matches(LootContext lootContext, ItemStack itemStack) {
			return (this.shop.isEmpty() || this.shop.get().value().test(lootContext)) && (this.item.isEmpty() || this.item.get().test(itemStack));
		}

		@Override
		public void validate(@NonNull ValidationContextSource criterionValidator) {
			SimpleCriterionTrigger.SimpleInstance.super.validate(criterionValidator);
			Validatable.validateHolder(criterionValidator.entityContext(), "shop", this.shop);
		}
	}

}
