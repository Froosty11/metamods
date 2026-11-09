package se.metacraft.ovvar.gametest;

import com.mojang.serialization.DataResult;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import org.apache.commons.lang3.math.Fraction;
import se.metacraft.bundles.util.BundleHelper;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.enchantment.EnderPockets;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Ender Pockets: the ovve's pockets are the clicking player's ender chest. */
public final class EnderPocketsTests {
	private static ServerPlayer survivor(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static ItemStack enderOvve(GameTestHelper helper) {
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		ovve.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(OvveEnchantments.ENDER_POCKETS), 1);
		return ovve;
	}

	private static boolean enderEmpty(ServerPlayer player) {
		return player.getEnderChestInventory().isEmpty();
	}

	private static Set<UUID> itemIds(GameTestHelper helper, Entity at, double radius) {
		Set<UUID> ids = new HashSet<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, at.getBoundingBox().inflate(radius))) ids.add(e.getUUID());
		return ids;
	}

	private static List<ItemEntity> newItemsNear(GameTestHelper helper, Entity at, double radius, Set<UUID> before) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, at.getBoundingBox().inflate(radius))
				.stream().filter(e -> !before.contains(e.getUUID())).toList();
	}

	private static void assertStack(GameTestHelper helper, ItemStack got, net.minecraft.world.item.Item item, int count, String what) {
		if (!got.is(item) || got.getCount() != count) helper.fail(what + ": want " + count + " " + item + ", got " + got);
	}

	private static boolean pocketsEmpty(ItemStack ovve) {
		BundleContents contents = ovve.get(DataComponents.BUNDLE_CONTENTS);
		return contents == null || contents.isEmpty();
	}

	@GameTest
	public void enchantingSpillsThePockets(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		BundleContents.Mutable pockets = ovve.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
		pockets.tryInsert(new ItemStack(Items.DIRT, 3));
		ovve.set(DataComponents.BUNDLE_CONTENTS, pockets.toImmutable());
		ovve.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(OvveEnchantments.ENDER_POCKETS), 1);
		player.getInventory().setItem(0, ovve);
		Set<UUID> before = itemIds(helper, player, 4);
		player.getInventory().tick();
		helper.assertTrue(pocketsEmpty(player.getInventory().getItem(0)), "the pockets were emptied: " + player.getInventory().getItem(0).get(DataComponents.BUNDLE_CONTENTS));
		int dirt = 0;
		for (ItemEntity e : newItemsNear(helper, player, 4, before)) if (e.getItem().is(Items.DIRT)) dirt += e.getItem().getCount();
		helper.assertValueEqual(dirt, 3, "dirt thrown on the ground");
		helper.assertTrue(enderEmpty(player), "nothing went into the ender chest");
		helper.succeed();
	}

	@GameTest
	public void clicksGoToTheEnderChest(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack ovve = enderOvve(helper);
		player.getInventory().setItem(9, ovve);
		player.inventoryMenu.setCarried(new ItemStack(Items.COBBLESTONE, 16));
		player.inventoryMenu.clicked(9, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "the cursor is empty: " + player.inventoryMenu.getCarried());
		assertStack(helper, player.getEnderChestInventory().getItem(0), Items.COBBLESTONE, 16, "ender slot 0");
		helper.assertTrue(pocketsEmpty(player.getInventory().getItem(9)), "the ovve's own pockets stay empty");
		player.inventoryMenu.clicked(9, 1, ContainerInput.PICKUP, player);
		assertStack(helper, player.inventoryMenu.getCarried(), Items.COBBLESTONE, 16, "the cursor after taking out");
		helper.assertTrue(enderEmpty(player), "the ender chest is empty again");
		helper.succeed();
	}

	@GameTest
	public void wornClickGoesToTheEnderChest(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		player.setItemSlot(EquipmentSlot.LEGS, enderOvve(helper));
		player.inventoryMenu.setCarried(new ItemStack(Items.COBBLESTONE, 16));
		player.inventoryMenu.clicked(7, 0, ContainerInput.PICKUP, player);
		assertStack(helper, player.getEnderChestInventory().getItem(0), Items.COBBLESTONE, 16, "ender slot 0");
		helper.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "the cursor is empty");
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.LEGS).is(ModContent.ovve(Chapter.DATA)), "the ovve is still worn");
		helper.succeed();
	}

	@GameTest
	public void twoPlayersTwoChests(GameTestHelper helper) {
		ServerPlayer a = survivor(helper), b = survivor(helper);
		ItemStack ovve = enderOvve(helper);
		a.getInventory().setItem(9, ovve);
		a.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND));
		a.inventoryMenu.clicked(9, 0, ContainerInput.PICKUP, a);
		ItemStack moved = a.getInventory().removeItemNoUpdate(9);
		b.setItemInHand(InteractionHand.MAIN_HAND, moved);
		helper.assertTrue(EnderPockets.view(b, -1).isEmpty(), "B sees B's (empty) ender chest");
		BundleContents aView = EnderPockets.view(a, -1);
		helper.assertValueEqual(aView.size(), 1, "A's view size");
		helper.assertTrue(aView.items().getFirst().item().value() == Items.DIAMOND && aView.items().getFirst().count() == 1, "A's view holds the diamond: " + aView.items());
		helper.assertTrue(enderEmpty(b), "B's ender chest untouched");
		helper.succeed();
	}

	@GameTest
	public void viewMirrorsEnderChest(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(0, new ItemStack(Items.DIRT, 5));
		ender.setItem(3, new ItemStack(Items.STONE, 64));
		ender.setItem(26, new ItemStack(Items.APPLE, 1));
		List<ItemStackTemplate> items = EnderPockets.view(player, -1).items();
		helper.assertValueEqual(items.size(), 3, "view size");
		helper.assertTrue(items.get(0).item().value() == Items.APPLE && items.get(0).count() == 1, "first apple×1: " + items);
		helper.assertTrue(items.get(1).item().value() == Items.STONE && items.get(1).count() == 64, "then stone×64: " + items);
		helper.assertTrue(items.get(2).item().value() == Items.DIRT && items.get(2).count() == 5, "then dirt×5: " + items);
		helper.assertValueEqual(EnderPockets.slotOf(player, 0), 26, "view 0's slot");
		helper.assertValueEqual(EnderPockets.slotOf(player, 3), -1, "view 3's slot");

		ItemStack ovve = enderOvve(helper);
		ovve.set(ModComponents.ENDER_SELECTED, 1);
		player.getInventory().setItem(9, ovve);
		player.inventoryMenu.clicked(9, 1, ContainerInput.PICKUP, player);
		assertStack(helper, player.inventoryMenu.getCarried(), Items.STONE, 64, "the selected stack on the cursor");
		helper.assertTrue(ender.getItem(3).isEmpty(), "ender slot 3 emptied");
		assertStack(helper, ender.getItem(26), Items.APPLE, 1, "ender slot 26 untouched");
		helper.succeed();
	}

	@GameTest
	public void viewDoesNotRecurse(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack held = enderOvve(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(0, enderOvve(helper));
		ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
		shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(enderOvve(helper), new ItemStack(Items.DIRT, 2))));
		ender.setItem(1, shulker);
		PacketContext context = player.connection.getPacketContext();
		var access = helper.getLevel().registryAccess();

		// What the network thread does: encode the stack in this player's packet context. Polymer turns every
		// nested stack into its client form while encoding, so a self-including view would never finish.
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
		try {
			PacketContext.runWithContext(player.connection, () -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, held));
		} catch (StackOverflowError e) {
			helper.fail("encoding the held ovve recursed");
		} finally {
			buf.release();
		}

		ItemStack client = PolymerItemUtils.getPolymerItemStack(held, context, access);
		BundleContents shown = client.get(DataComponents.BUNDLE_CONTENTS);
		helper.assertTrue(shown != null && shown.size() == 2, "the held ovve shows 2 entries: " + shown);
		ItemStack shownShulker = shown.items().get(0).create(), shownOvve = shown.items().get(1).create();
		helper.assertTrue(shownShulker.is(Items.SHULKER_BOX), "newest first: the shulker box: " + shownShulker);
		helper.assertTrue(pocketsEmpty(PolymerItemUtils.getPolymerItemStack(shownOvve, context, access)), "a nested Ender Pockets ovve shows empty pockets");
		ItemStack inShulker = shownShulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream()
				.filter(s -> s.is(ModContent.ovve(Chapter.DATA))).findFirst().orElse(ItemStack.EMPTY);
		helper.assertFalse(inShulker.isEmpty(), "the shulker box still holds its ovve");
		helper.assertTrue(pocketsEmpty(PolymerItemUtils.getPolymerItemStack(inShulker, context, access)), "an Ender Pockets ovve in a shulker box shows empty pockets");
		helper.succeed();
	}

	@GameTest
	public void polymerStackShowsTheViewersChest(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
		ItemStack ovve = enderOvve(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, ovve);
		ItemStack client = PolymerItemUtils.getPolymerItemStack(ovve, player.connection.getPacketContext(), helper.getLevel().registryAccess());
		BundleContents shown = client.get(DataComponents.BUNDLE_CONTENTS);
		helper.assertTrue(shown != null && shown.size() == 1 && shown.items().getFirst().item().value() == Items.DIAMOND
				&& shown.items().getFirst().count() == 5, "the client pockets are [diamond×5]: " + shown);
		List<Component> lore = client.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
		Component want = BundleHelper.getOccupancyText(DataResult.success(Fraction.getFraction(5, 64 * 27)));
		helper.assertFalse(lore.isEmpty(), "the client stack has a fill bar");
		helper.assertValueEqual(lore.getFirst(), want, "the fill bar");
		helper.succeed();
	}

	@GameTest
	public void useOpensTheEnderChest(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack ovve = enderOvve(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, ovve);
		Set<UUID> before = itemIds(helper, player, 4);
		InteractionResult result = ovve.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		// ItemStack.use records the held stack on the result, so this is SUCCESS with that filled in.
		helper.assertTrue(result instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SUCCESS.swingSource(),
				"use succeeds: " + result);
		helper.assertTrue(player.containerMenu instanceof ChestMenu chest && chest.getContainer() == player.getEnderChestInventory(),
				"the open menu is the ender chest: " + player.containerMenu);
		helper.assertTrue(newItemsNear(helper, player, 4, before).isEmpty(), "nothing dropped");
		helper.succeed();
	}
}
