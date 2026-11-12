package se.metacraft.playertrading.gametest;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.NonNull;
import se.metacraft.playertrading.block.TradingBlockEntities;
import se.metacraft.playertrading.block.TradingBlocks;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;
import se.metacraft.playertrading.shop.Shop;
import se.metacraft.playertrading.shop.ShopType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DataFixerTest implements CustomTestMethodInvoker {

	private static final int COUNT = 5;
	private static final String NAME = "TestName";
	private static final String LORE = "Lore";
	private static final String CUSTOM_KEY = "Custom";
	private static final int CUSTOM_VALUE = 123;

	private static String stringify(String str) {
		return "\"" + str + "\"";
	}

	private static Map<?, ?> upgradableItem() {
		return Map.of(
			"id", "diamond",
			"Count", COUNT,
			"tag", Map.of(
				"display", Map.of(
					"Name", stringify(NAME),
					"Lore", List.of(
						stringify(LORE)
					)
				),
				CUSTOM_KEY, CUSTOM_VALUE
			)
		);
	}

	private static void validateItem(GameTestHelper helper, ItemStackTemplate item) {
		if (!item.is(Items.DIAMOND)) {
			helper.fail("Not a diamond");
		}
		if (item.count() != COUNT) {
			helper.fail("Expected count " + COUNT + ", but found " + item.count());
		}
		var customName = item.get(DataComponents.CUSTOM_NAME);
		var lore = item.get(DataComponents.LORE);
		var customData = item.get(DataComponents.CUSTOM_DATA);
		if (customName == null) {
			helper.fail("No custom name");
		} else if (!customName.getString().equals(NAME)) {
			helper.fail("Expected custom name " + NAME + ", but found " + customName.getString());
		}

		if (lore == null) {
			helper.fail("No lore");
		} else if (lore.lines().size() != 1 || !lore.lines().getFirst().getString().equals(LORE)) {
			helper.fail("Expected lore " + LORE + ", but found " + lore.lines());
		}

		if (customData == null) {
			helper.fail("No custom data");
		} else {
			var data = customData.copyTag();
			if (!data.contains(CUSTOM_KEY) || data.getIntOr(CUSTOM_KEY, -1) != CUSTOM_VALUE) {
				helper.fail("Expected custom data " + CUSTOM_KEY + " -> " + CUSTOM_VALUE + ", but found " + data);
			}
		}
	}

	@GameTest
	public void shopMenu(GameTestHelper helper) {
		var data = helper.getLevel().getServer().getFixerUpper().update(
			References.BLOCK_ENTITY, new Dynamic<>(
				JavaOps.INSTANCE,
				Map.of(
					"id", TradingBlockEntities.SHOP.builtInRegistryHolder().key().identifier().toString(),
					ShopBlockEntity.SHOP, Map.of(
						"shop_type", ShopType.CODEC.encodeStart(
							JavaOps.INSTANCE, new ShopType.Player(Optional.empty())
						).getOrThrow(),
						Shop.OFFERS, List.of(
							Map.of(
								Shop.SimpleOffer.PRICE, upgradableItem(),
								Shop.SimpleOffer.RESULT, upgradableItem()
							),
							Map.of(
								Shop.SimpleOffer.PRICE, List.of(upgradableItem(), upgradableItem()),
								Shop.SimpleOffer.RESULT, upgradableItem()
							)
						)
					)
				)
			),
			3816,
			SharedConstants.getCurrentVersion().dataVersion().version()
		);

		var blockEntity = BlockEntity.loadStatic(
			BlockPos.ZERO, TradingBlocks.SHOP.value().defaultBlockState(),
			(CompoundTag) JavaOps.INSTANCE.convertTo(NbtOps.INSTANCE, data.getValue()),
			helper.getLevel().registryAccess()
		);
		if (blockEntity instanceof ShopBlockEntity shop) {
			shop.getShop().ifPresentOrElse(s -> {
				for (var offer : s.offers()) {
					validateItem(helper, offer.price().left());
					offer.price().right().ifPresent(o -> validateItem(helper, o));
					validateItem(helper, offer.result());
				}
			}, () -> helper.fail("No shop"));
		} else {
			helper.fail("Not a shop " + blockEntity);
		}
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(
		@NonNull GameTestHelper helper, @NonNull Method method
	) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
