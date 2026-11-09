package se.metacraft.ovvar.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.enchantment.EnchantmentsConfig;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;
import se.metacraft.ovvar.content.item.OvveFeet;
import se.metacraft.ovvar.content.item.OvveItem;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static se.metacraft.ovvar.content.enchantment.OvveEnchantments.*;

/** The six ovve enchantments: what takes them, what they clash with, where they come from, and the glint. */
public final class EnchantmentTests {
	private static HolderLookup.RegistryLookup<Enchantment> registry(GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return registry(helper).getOrThrow(key);
	}

	@GameTest
	public void everyOvveTakesTheSix(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) {
			ItemStack ovve = new ItemStack(ModContent.ovve(chapter));
			if (!ovve.is(ENCHANTABLE_OVVE)) wrong.add(chapter + " not in #ovvar:enchantable/ovve");
			if (!ovve.isEnchantable()) wrong.add(chapter + " not enchantable");
			for (ResourceKey<Enchantment> key : ALL) {
				if (!holder(helper, key).value().canEnchant(ovve)) wrong.add(chapter + " refuses " + key.identifier());
			}
		}
		List<ItemStack> others = List.of(new ItemStack(ModContent.top(Chapter.DATA)),
				new ItemStack(ModContent.patchItem(Patches.all().getFirst())), new ItemStack(Items.DIAMOND_LEGGINGS));
		for (ItemStack other : others) {
			for (ResourceKey<Enchantment> key : ALL) {
				if (holder(helper, key).value().canEnchant(other)) wrong.add(other.getItem() + " takes " + key.identifier());
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/** No vanilla (or other mod's) enchantment goes on an ovve: not Unbreaking, not Mending, not any Protection. */
	@GameTest
	public void unbreakingAndMendingNeverApply(GameTestHelper helper) {
		// Fails loudly before the six exist, like the rest of this class.
		holder(helper, MAGPIE);
		List<String> wrong = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) {
			ItemStack ovve = new ItemStack(ModContent.ovve(chapter));
			registry(helper).listElements().forEach(h -> {
				if (h.key().identifier().getNamespace().equals("ovvar")) return;
				if (h.value().canEnchant(ovve)) wrong.add(chapter + " takes " + h.key().identifier());
			});
		}
		if (!wrong.isEmpty()) {
			helper.fail("vanilla enchantments apply to an ovve (" + Enchantments.UNBREAKING.identifier() + " and "
					+ Enchantments.MENDING.identifier() + " must never): " + wrong);
		}
		helper.succeed();
	}

	@GameTest
	public void incompatibilities(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		if (Enchantment.areCompatible(holder(helper, ENDER_POCKETS), holder(helper, KEYRING))) wrong.add("ender pockets + keyring compatible");
		for (ResourceKey<Enchantment> protection : List.of(Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION,
				Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION)) {
			if (Enchantment.areCompatible(holder(helper, PATCHWORK_PROTECTION), holder(helper, protection))) {
				wrong.add("patchwork + " + protection.identifier() + " compatible");
			}
		}
		for (int i = 0; i < ALL.size(); i++) {
			for (int j = i + 1; j < ALL.size(); j++) {
				ResourceKey<Enchantment> a = ALL.get(i), b = ALL.get(j);
				boolean pockets = Set.of(a, b).equals(Set.of(ENDER_POCKETS, KEYRING));
				if (!pockets && !Enchantment.areCompatible(holder(helper, a), holder(helper, b))) {
					wrong.add(a.identifier() + " + " + b.identifier() + " incompatible");
				}
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void tableOffersFiveNeverTheCurse(GameTestHelper helper) {
		var registry = registry(helper);
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		Set<ResourceKey<Enchantment>> seen = new HashSet<>();
		for (int seed = 0; seed < 500; seed++) {
			for (EnchantmentInstance instance : EnchantmentHelper.selectEnchantment(RandomSource.create(seed), ovve, 30,
					registry.getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE).stream())) {
				seen.add(instance.enchantment().unwrapKey().orElseThrow());
			}
		}
		Set<ResourceKey<Enchantment>> want = new HashSet<>(ALL);
		want.remove(SPILLED_DRINK_CURSE);
		helper.assertValueEqual(seen, want, "what the table offers an ovve at level 30");
		Holder<Enchantment> curse = holder(helper, SPILLED_DRINK_CURSE);
		helper.assertTrue(curse.is(EnchantmentTags.CURSE), "the curse is in #minecraft:curse");
		helper.assertTrue(curse.is(EnchantmentTags.TREASURE), "the curse is in #minecraft:treasure");
		helper.assertFalse(curse.is(EnchantmentTags.IN_ENCHANTING_TABLE), "the curse is in #minecraft:in_enchanting_table");
		helper.assertFalse(curse.is(EnchantmentTags.TRADEABLE), "the curse is in #minecraft:tradeable");
		helper.succeed();
	}

	@GameTest
	public void randomLootBooksFollowBrewery(GameTestHelper helper) {
		boolean brewery = FabricLoader.getInstance().isModLoaded("brewery");
		for (ResourceKey<Enchantment> key : ALL) {
			boolean want = key != SPILLED_DRINK_CURSE || brewery;
			helper.assertValueEqual(holder(helper, key).is(EnchantmentTags.ON_RANDOM_LOOT), want, key.identifier() + " in #minecraft:on_random_loot");
		}
		LootItemFunction function = EnchantRandomlyFunction.randomEnchantment().build();
		LootParams params = new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY);
		Set<ResourceKey<Enchantment>> seen = new HashSet<>();
		for (int seed = 0; seed < 4000; seed++) {
			LootContext context = new LootContext.Builder(params).withOptionalRandomSource(RandomSource.create(seed)).create(Optional.empty());
			ItemStack book = function.apply(new ItemStack(Items.BOOK), context);
			for (Holder<Enchantment> h : EnchantmentHelper.getEnchantmentsForCrafting(book).keySet()) h.unwrapKey().ifPresent(seen::add);
		}
		for (ResourceKey<Enchantment> key : ALL) {
			boolean want = key != SPILLED_DRINK_CURSE || brewery;
			helper.assertValueEqual(seen.contains(key), want, key.identifier() + " on a random book (brewery " + brewery + ")");
		}
		helper.assertValueEqual(OvveEnchantments.BREWERY, brewery, "OvveEnchantments.BREWERY");
		var selected = helper.getLevel().getServer().getPackRepository().getSelectedIds();
		helper.assertValueEqual(selected.contains("ovvar:brewery_curse"), brewery, "ovvar:brewery_curse selected (" + selected + ")");
		helper.succeed();
	}

	@GameTest
	public void curseOnlyWithBrewery(GameTestHelper helper) {
		Holder<Enchantment> curse = holder(helper, SPILLED_DRINK_CURSE), magpie = holder(helper, MAGPIE);
		EnchantmentsConfig cfg = EnchantmentsConfig.DEFAULT;
		helper.assertFalse(offered(curse, cfg, false), "the curse offered without Brewery");
		helper.assertTrue(offered(curse, cfg, true), "the curse offered with Brewery");
		helper.assertFalse(enabled(SPILLED_DRINK_CURSE, cfg, false), "the curse enabled without Brewery");
		helper.assertTrue(offered(magpie, cfg, false), "magpie offered without Brewery");
		helper.succeed();
	}

	@GameTest
	public void disabledIsNotOffered(GameTestHelper helper) {
		EnchantmentsConfig cfg = EnchantmentsConfig.DEFAULT.keyring(new EnchantmentsConfig.Keyring(false, 20));
		helper.assertFalse(offered(holder(helper, KEYRING), cfg, BREWERY), "keyring offered while off");
		helper.assertTrue(offered(holder(helper, ENDER_POCKETS), cfg, BREWERY), "ender pockets offered");
		helper.assertTrue(offered(holder(helper, Enchantments.PROTECTION), cfg, BREWERY), "protection offered");
		helper.assertFalse(enabled(KEYRING, cfg, BREWERY), "keyring enabled while off");
		helper.succeed();
	}

	@GameTest
	public void enchantedOvveGlintsAndSoDoesItsTop(GameTestHelper helper) {
		Holder<Enchantment> magpie = holder(helper, MAGPIE);
		List<String> wrong = new ArrayList<>();
		for (boolean enchanted : List.of(true, false)) {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
			OvveItem.setTopUp(ovve, true);
			if (enchanted) ovve.enchant(magpie, 2);
			player.setItemSlot(EquipmentSlot.LEGS, ovve);
			ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
			legs.inventoryTick(helper.getLevel(), player, EquipmentSlot.LEGS);
			ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
			if (chest.getItem() != ModContent.top(Chapter.DATA)) { wrong.add("no companion top: " + chest); continue; }
			var context = player.connection.getPacketContext();
			var access = helper.getLevel().registryAccess();
			ItemStack clientLegs = PolymerItemUtils.getPolymerItemStack(legs, context, access);
			ItemStack clientTop = PolymerItemUtils.getPolymerItemStack(chest, context, access);
			ItemStack clientCuffs = PolymerItemUtils.getPolymerItemStack(OvveFeet.cuffsFor(legs), context, access);
			String mode = enchanted ? "enchanted: " : "plain: ";
			if (clientLegs.hasFoil() != enchanted) wrong.add(mode + "legs foil " + clientLegs.hasFoil());
			if (clientTop.hasFoil() != enchanted) wrong.add(mode + "top foil " + clientTop.hasFoil());
			if (clientCuffs.hasFoil() != enchanted) wrong.add(mode + "cuffs foil " + clientCuffs.hasFoil());
			if (enchanted) {
				if (!Boolean.TRUE.equals(clientTop.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) wrong.add("top has no glint override");
				if (!Boolean.TRUE.equals(clientCuffs.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) wrong.add("cuffs have no glint override");
				if (clientLegs.getEnchantments().getLevel(magpie) != 2) wrong.add("legs' client magpie level " + clientLegs.getEnchantments().getLevel(magpie));
			}
		}
		JsonObject en = lang("en_us"), sv = lang("sv_se");
		for (ResourceKey<Enchantment> key : ALL) {
			String langKey = "enchantment.ovvar." + key.identifier().getPath();
			Names names = NAMES.get(key);
			if (en == null || !en.has(langKey) || !en.get(langKey).getAsString().equals(names.en())) wrong.add("en_us " + langKey);
			if (sv == null || !sv.has(langKey) || !sv.get(langKey).getAsString().equals(names.sv())) wrong.add("sv_se " + langKey);
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	private static JsonObject lang(String language) {
		try (var in = EnchantmentTests.class.getResourceAsStream("/assets/ovvar/lang/" + language + ".json")) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			return null;
		}
	}

	@GameTest
	public void anvilRefusesKeyringOnEnderPockets(GameTestHelper helper) {
		Holder<Enchantment> enderPockets = holder(helper, ENDER_POCKETS), keyring = holder(helper, KEYRING), magpie = holder(helper, MAGPIE);
		ItemStack refused = anvil(helper, enderPockets, keyring);
		helper.assertTrue(refused.isEmpty(), "the anvil put Keyring on an Ender Pockets ovve: " + refused);
		ItemStack made = anvil(helper, enderPockets, magpie);
		helper.assertValueEqual(made.getItem(), ModContent.ovve(Chapter.DATA), "the anvil's result");
		helper.assertValueEqual(made.getEnchantments().getLevel(enderPockets), 1, "ender pockets on the result");
		helper.assertValueEqual(made.getEnchantments().getLevel(magpie), 1, "magpie on the result");
		helper.succeed();
	}

	private static ItemStack anvil(GameTestHelper helper, Holder<Enchantment> onOvve, Holder<Enchantment> onBook) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		ovve.enchant(onOvve, 1);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(ovve);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(EnchantmentHelper.createBook(new EnchantmentInstance(onBook, 1)));
		menu.createResult();
		return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
	}
}
