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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EntityTypes;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.PatchLoot;
import se.metacraft.ovvar.content.enchantment.Keepsake;
import se.metacraft.ovvar.content.enchantment.Magpie;
import se.metacraft.ovvar.content.item.PatchItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.content.enchantment.PatchworkProtection;
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
import java.util.stream.Stream;

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

	@GameTest
	public void patchworkScalesWithPatches(GameTestHelper helper) {
		EnchantmentsConfig.Patchwork cfg = EnchantmentsConfig.Patchwork.DEFAULT;
		float[][] cases = {{0, 1, 0f}, {4, 1, 1.0f}, {8, 1, 2.0f}, {16, 1, 4.0f}, {40, 1, 4.0f}, {16, 0, 0f}};
		for (float[] c : cases) {
			helper.assertValueEqual(PatchworkProtection.protection((int) c[0], (int) c[1], cfg), c[2], "protection(" + (int) c[0] + ", " + (int) c[1] + ")");
		}
		helper.succeed();
	}

	@GameTest
	public void patchworkProtectsTheWearer(GameTestHelper helper) {
		Holder<Enchantment> patchwork = holder(helper, PATCHWORK_PROTECTION);
		var itk = Patches.get("itk");
		List<Placement> placements = Stream.of(Spot.FRONT_TOP_LEFT, Spot.FRONT_TOP_RIGHT, Spot.FRONT_LOW_LEFT, Spot.FRONT_LOW_RIGHT,
				Spot.SLEEVE_OUT_TOP_R, Spot.SLEEVE_OUT_TOP_L, Spot.LEG_OUT_TOP_R, Spot.LEG_OUT_TOP_L).map(s -> new Placement(s, itk)).toList();
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
		ItemStack plain = new ItemStack(ModContent.ovve(Chapter.DATA));
		Looks.setSewn(plain, SpotPlacements.fromList(placements).getOrThrow());
		ItemStack enchanted = plain.copy();
		enchanted.enchant(patchwork, 1);
		var level = helper.getLevel();
		OvveItem.wear(stand, enchanted);
		float got = EnchantmentHelper.getDamageProtection(level, stand, level.damageSources().generic());
		helper.assertTrue(Math.abs(got - 2.0f) < 1e-4f, "protection with 8 patches was " + got);
		helper.assertValueEqual(EnchantmentHelper.getDamageProtection(level, stand, level.damageSources().fellOutOfWorld()), 0f, "protection against the void");
		OvveItem.wear(stand, plain);
		helper.assertValueEqual(EnchantmentHelper.getDamageProtection(level, stand, level.damageSources().generic()), 0f, "protection without the enchantment");
		helper.succeed();
	}

	// ---- Magpie

	private static void near(GameTestHelper helper, float got, float want, String what) {
		helper.assertTrue(Math.abs(got - want) < 1e-6f, what + " was " + got + ", wanted " + want);
	}

	@GameTest
	public void magpieChances(GameTestHelper helper) {
		EnchantmentsConfig.Magpie cfg = EnchantmentsConfig.Magpie.DEFAULT;
		near(helper, Magpie.chestChance(0, cfg), 0.05f, "chestChance(0)");
		near(helper, Magpie.chestChance(1, cfg), 0.08f, "chestChance(1)");
		near(helper, Magpie.chestChance(3, cfg), 0.14f, "chestChance(3)");
		near(helper, Magpie.mobOvvarChance(0.25f, 2, cfg), 0.45f, "mobOvvarChance(0.25, 2)");
		near(helper, Magpie.mobOvvarChance(0.95f, 3, cfg), 1.0f, "mobOvvarChance(0.95, 3)");
		helper.assertValueEqual(Magpie.level(null), 0, "level(null)");
		helper.succeed();
	}

	/** An armour stand in a Data ovve (or a Media frack) with {@code magpie} levels of Magpie; 0 means plain. */
	private static ArmorStand standIn(GameTestHelper helper, Chapter chapter, int magpie) {
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
		ItemStack garment = new ItemStack(ModContent.ovve(chapter));
		if (magpie > 0) garment.enchant(holder(helper, MAGPIE), magpie);
		OvveItem.wear(stand, garment);
		return stand;
	}

	/** The share of 4000 dungeon chests, opened by {@code opener}, that hold a patch; no NON_LOOT patch may ever show. */
	private static double patchShare(GameTestHelper helper, Entity opener) {
		LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
		LootParams.Builder builder = new LootParams.Builder(helper.getLevel())
				.withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1)));
		if (opener != null) builder.withParameter(LootContextParams.THIS_ENTITY, opener);
		LootParams params = builder.create(LootContextParamSets.CHEST);
		int with = 0;
		for (int seed = 0; seed < 4000; seed++) {
			boolean any = false;
			for (ItemStack stack : table.getRandomItems(params, seed)) {
				if (stack.getItem() instanceof PatchItem item) {
					any = true;
					helper.assertFalse(PatchLoot.NON_LOOT.contains(item.patch.id()), "a dungeon chest held " + item.patch.id() + ", which is not loot");
				}
			}
			if (any) with++;
		}
		return with / 4000.0;
	}

	private static void share(GameTestHelper helper, double got, double lo, double hi, String what) {
		helper.assertTrue(got >= lo && got <= hi, what + ": " + got + " not in [" + lo + ", " + hi + "]");
	}

	@GameTest
	public void magpieRaisesChestPatches(GameTestHelper helper) {
		share(helper, patchShare(helper, standIn(helper, Chapter.DATA, 3)), 0.12, 0.16, "Magpie III opener");
		share(helper, patchShare(helper, standIn(helper, Chapter.DATA, 0)), 0.035, 0.065, "plain ovve opener");
		share(helper, patchShare(helper, null), 0.035, 0.065, "no opener");
		helper.succeed();
	}

	@GameTest
	public void magpieOnAFrackCounts(GameTestHelper helper) {
		share(helper, patchShare(helper, standIn(helper, Chapter.MEDIA, 3)), 0.12, 0.16, "Magpie III frack opener");
		helper.succeed();
	}

	/** Part 2's mob-ovvar patch-drop roll will call {@link Magpie#mobOvvarDrop}; this is that comparison. */
	@GameTest
	public void magpieRaisesMobOvvarDrops(GameTestHelper helper) {
		float base = 0.25f;
		RandomSource roll = new SingleThreadedRandomSource(0) {
			@Override
			public float nextFloat() {
				return base + 0.05f;
			}
		};
		EnchantmentsConfig.Magpie cfg = EnchantmentsConfig.Magpie.DEFAULT;
		helper.assertFalse(Magpie.mobOvvarDrop(roll, base, standIn(helper, Chapter.DATA, 0), cfg), "a killer in a plain ovve got a drop");
		helper.assertTrue(Magpie.mobOvvarDrop(roll, base, standIn(helper, Chapter.DATA, 1), cfg), "a killer in a Magpie I ovve got no drop");
		helper.assertFalse(Magpie.mobOvvarDrop(roll, base, null, cfg), "no killer got a drop");
		near(helper, Magpie.patchDropBonus(standIn(helper, Chapter.DATA, 2)), 0.20f, "patchDropBonus(Magpie II)");
		near(helper, Magpie.patchDropBonus(null), 0f, "patchDropBonus(null)");
		helper.succeed();
	}

	// ---- Keepsake

	private static ItemStack keepsakeOvve(GameTestHelper helper, int dirt) {
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		ovve.enchant(holder(helper, KEEPSAKE), 1);
		if (dirt > 0) {
			var bundle = ovve.getOrDefault(DataComponents.BUNDLE_CONTENTS, net.minecraft.world.item.component.BundleContents.EMPTY).asMutable();
			bundle.tryInsert(new ItemStack(Items.DIRT, dirt));
			ovve.set(DataComponents.BUNDLE_CONTENTS, bundle.toImmutable());
		}
		return ovve;
	}

	private static int dirtIn(ItemStack ovve) {
		int n = 0;
		var contents = ovve.get(DataComponents.BUNDLE_CONTENTS);
		if (contents != null) for (var s : contents.items()) n += s.count();
		return n;
	}

	private static int legsIndex() {
		for (var e : net.minecraft.world.entity.player.Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet()) {
			if (e.getValue() == EquipmentSlot.LEGS) return e.getIntKey();
		}
		throw new IllegalStateException("no legs slot");
	}

	/** The item entities around {@code at} that were not there before (tests share the world, and it may hold litter from earlier runs). */
	private static List<net.minecraft.world.entity.item.ItemEntity> itemsNear(GameTestHelper helper, Entity at, double radius, Set<java.util.UUID> before) {
		return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, at.getBoundingBox().inflate(radius))
				.stream().filter(e -> !before.contains(e.getUUID())).toList();
	}

	private static Set<java.util.UUID> itemIds(GameTestHelper helper, Entity at, double radius) {
		Set<java.util.UUID> ids = new HashSet<>();
		for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, at.getBoundingBox().inflate(radius))) ids.add(e.getUUID());
		return ids;
	}

	@GameTest
	public void keepsakeOvveSurvivesDeath(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setItemSlot(EquipmentSlot.LEGS, keepsakeOvve(helper, 3));
		ItemStack plain = new ItemStack(ModContent.ovve(Chapter.IT));
		player.getInventory().setItem(5, plain);
		player.getInventory().setItem(6, new ItemStack(Items.DIRT, 10));
		Set<java.util.UUID> before = itemIds(helper, player, 8);
		player.die(helper.getLevel().damageSources().generic());

		List<Keepsake.Kept> kept = player.getAttachedOrElse(Keepsake.KEPT, List.of());
		helper.assertValueEqual(kept.size(), 1, "kept entries");
		helper.assertValueEqual(kept.getFirst().slot(), legsIndex(), "kept slot");
		helper.assertValueEqual(dirtIn(kept.getFirst().stack()), 3, "dirt in the kept ovve");
		boolean itOvve = false, dirt = false, data = false;
		for (var item : itemsNear(helper, player, 8, before)) {
			ItemStack s = item.getItem();
			if (s.is(ModContent.ovve(Chapter.IT))) itOvve = true;
			if (s.is(Items.DIRT) && s.getCount() == 10) dirt = true;
			if (s.is(ModContent.ovve(Chapter.DATA))) data = true;
		}
		helper.assertTrue(itOvve, "the plain IT ovve dropped");
		helper.assertTrue(dirt, "the 10 dirt dropped");
		helper.assertFalse(data, "the Keepsake ovve dropped");

		ServerPlayer fresh = helper.makeMockServerPlayerInLevel();
		Keepsake.restore(player, fresh);
		ItemStack legs = fresh.getItemBySlot(EquipmentSlot.LEGS);
		helper.assertTrue(legs.is(ModContent.ovve(Chapter.DATA)), "the ovve is back in the legs slot: " + legs);
		helper.assertValueEqual(dirtIn(legs), 3, "dirt in the restored ovve");
		helper.assertFalse(player.hasAttached(Keepsake.KEPT), "the dead player still has KEPT");
		helper.succeed();
	}

	@GameTest
	public void keepsakeRestoreNeverDeletes(GameTestHelper helper) {
		ServerPlayer from = helper.makeMockServerPlayerInLevel();
		ItemStack a = keepsakeOvve(helper, 1), b = keepsakeOvve(helper, 2);
		from.setAttached(Keepsake.KEPT, List.of(new Keepsake.Kept(legsIndex(), a), new Keepsake.Kept(5, b)));
		ServerPlayer fresh = helper.makeMockServerPlayerInLevel();
		fresh.setGameMode(GameType.SURVIVAL);
		ItemStack c = new ItemStack(ModContent.ovve(Chapter.IT));
		fresh.setItemSlot(EquipmentSlot.LEGS, c);
		for (int i = 0; i < 36; i++) if (i != 5) fresh.getInventory().setItem(i, new ItemStack(Items.STONE));
		Set<java.util.UUID> before = itemIds(helper, fresh, 2);
		Keepsake.restore(from, fresh);

		helper.assertValueEqual(dirtIn(fresh.getInventory().getItem(5)), 2, "slot 5 holds ovve B");
		helper.assertTrue(fresh.getItemBySlot(EquipmentSlot.LEGS).is(ModContent.ovve(Chapter.IT)), "the worn ovve C stayed");
		int ovvar = 0, droppedA = 0;
		for (int i = 0; i < fresh.getInventory().getContainerSize(); i++) {
			if (fresh.getInventory().getItem(i).getItem() instanceof OvveItem) ovvar++;
		}
		for (var item : itemsNear(helper, fresh, 2, before)) {
			if (item.getItem().getItem() instanceof OvveItem) {
				ovvar++;
				if (dirtIn(item.getItem()) == 1) droppedA++;
			}
		}
		helper.assertValueEqual(droppedA, 1, "ovve A lying near the player");
		helper.assertValueEqual(ovvar, 3, "ovvar across inventory and ground");
		helper.assertFalse(from.hasAttached(Keepsake.KEPT), "KEPT removed from the old player");
		helper.succeed();
	}
}
