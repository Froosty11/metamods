package se.metacraft.playertrading;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.advancements.triggers.RecipeUnlockedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import org.jspecify.annotations.NonNull;
import se.metacraft.playertrading.block.TradingBlocks;
import se.metacraft.playertrading.component.TradingComponents;
import se.metacraft.playertrading.item.TradingItems;

import java.util.Map;
import java.util.Optional;

public class DataGen implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		var pack = fabricDataGenerator.createPack();
		pack.addProvider((output, registries) -> new FabricRecipeProvider(output, registries) {
			@Override
			protected @NonNull RecipeProvider createRecipeProvider(
				HolderLookup.@NonNull Provider registries,
				@NonNull BootstrapContext<Recipe<?>> recipes,
				@NonNull BootstrapContext<Advancement> advancements
			) {
				return new RecipeProvider(recipes, advancements) {
					@Override
					public void buildRecipes() {
						var shopBlock = ResourceKey.create(
							Registries.RECIPE,
							PlayerTrading.getID("shop_block")
						);
						Advancement.Builder builder = output.advancement().addCriterion(
							"has_the_recipe", RecipeUnlockedTrigger.unlocked(output.lookup(Registries.RECIPE).getOrThrow(shopBlock))
						).rewards(AdvancementRewards.Builder.recipe(shopBlock)).requirements(
							AdvancementRequirements.Strategy.OR
						);
						builder.addCriterion(
							"trigger_always",
							CriteriaTriggers.TICK.createCriterion(new PlayerTrigger.TriggerInstance(
								Optional.empty()
							))
						);
						output.accept(
							shopBlock,
							new ShapedRecipe(
								new Recipe.CommonInfo(true),
								new CraftingRecipe.CraftingBookInfo(
									CraftingBookCategory.MISC,
									"playertrading"
								),
								ShapedRecipePattern.of(
									Map.of(
										'A', tag(ItemTags.WOOL),
										'B', tag(ItemTags.PLANKS),
										'C', Ingredient.of(Items.BARREL),
										'D', Ingredient.of(Items.IRON_INGOT)
									),
									"AAA",
									"BCB",
									"BDB"
								),
								new ItemStackTemplate(TradingItems.SHOP)
							),
							builder.build(shopBlock.identifier().withPrefix("recipes/" + RecipeCategory.MISC.getFolderName() + "/"))
						);
					}
				};
			}

			@Override
			public @NonNull String getName() {
				return "shop-block";
			}
		});

		pack.addProvider((output, registriesFuture) -> new FabricBlockLootSubProvider(output, registriesFuture) {

			@Override
			public void generate() {
				var shopDrop = TradingItems.SHOP;
				add(
					TradingBlocks.SHOP.value(),
					LootTable.lootTable().withPool(
						this.applyExplosionCondition(
							shopDrop,
							LootPool.lootPool()
								.setRolls(ContextIntProviders.exactly(1))
								.add(
									LootItem.lootTableItem(shopDrop).apply(
										CopyComponentsFunction.copyComponentsFromBlockEntity(
											LootContextParams.BLOCK_ENTITY
										).include(TradingComponents.SHOP).include(TradingComponents.SHOP_KEY)
									)
								)
						)
					)
				);
			}
		});
	}
}
