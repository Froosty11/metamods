package se.metacraft.ovvar.gametest;

import com.mojang.serialization.JavaOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import se.metacraft.ovvar.MobOvvarConfig;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.MobOvvar;
import se.metacraft.ovvar.content.PatchLoot;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.content.item.OvveItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How a mob is dressed in an ovve or a frack (mob ovvar): pure, seeded, and needing no lock, since
 * nothing here touches the config or the world.
 */
public final class MobOvvarTests {
	private static final int SEEDS = 2000;

	private static List<ItemStack> garments(EquipmentSlot slot, boolean topCells) {
		List<ItemStack> garments = new ArrayList<>();
		for (int seed = 0; seed < SEEDS; seed++) garments.add(MobOvvar.garment(RandomSource.create(seed), slot, topCells));
		return garments;
	}

	private static Chapter chapter(ItemStack garment) {
		return ((OvveItem) garment.getItem()).chapter;
	}

	private static List<Placement> placements(ItemStack garment) {
		return SpotPlacements.asPlacementList(Looks.sewn(garment));
	}

	@GameTest
	public void wearsEveryLootPatchAndNothingElse(GameTestHelper helper) {
		helper.assertValueEqual(MobOvvar.patches(), PatchLoot.looted(), "the patches a mob may wear");
		helper.assertValueEqual(MobOvvar.patches().size(), 21, "loot patches");
		Set<String> seen = new HashSet<>();
		for (EquipmentSlot slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.CHEST)) {
			for (ItemStack garment : garments(slot, true)) {
				for (Placement p : placements(garment)) {
					if (!PatchLoot.LOOT.contains(p.patch().id())) helper.fail("a mob wears " + p.patch().id() + ", which is not loot");
					seen.add(p.patch().id());
				}
			}
		}
		helper.assertValueEqual(seen, PatchLoot.LOOT, "the patches seen over " + 2 * SEEDS + " garments");
		helper.succeed();
	}

	@GameTest
	public void dressedLikeAMob(GameTestHelper helper) {
		for (EquipmentSlot slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.CHEST)) {
			for (ItemStack garment : garments(slot, true)) {
				if (garment.isEmpty()) helper.fail("no garment for " + slot);
				if (chapter(garment).slot != slot) helper.fail(chapter(garment).id + " is not worn in " + slot);
				if (!MobOvvar.is(garment)) helper.fail("a mob garment lacks the marker");
				if (OvveItem.owner(garment) != null) helper.fail("a mob garment has an owner");
				if (!garment.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).isEmpty()) helper.fail("a mob garment has something in its pockets");
				List<Placement> all = placements(garment);
				if (all.isEmpty() || all.size() > 3) helper.fail("a mob garment has " + all.size() + " patches");
				for (Piece piece : Piece.values()) {
					if (all.stream().filter(p -> p.piece() == piece).count() > Looks.INSTANT) helper.fail("more than " + Looks.INSTANT + " on " + piece);
				}
				if (slot == EquipmentSlot.CHEST && all.stream().anyMatch(p -> p.piece() != Piece.TOP)) helper.fail("a frack with patches off its top");
				SpotPlacements sewn = Looks.sewn(garment).orElseThrow();
				var encoded = SpotPlacements.CODEC.encodeStart(JavaOps.INSTANCE, sewn).getOrThrow();
				var decoded = SpotPlacements.CODEC.parse(JavaOps.INSTANCE, encoded).getOrThrow();
				helper.assertValueEqual(decoded, sewn, "the sewn patches after a round trip");
			}
		}
		helper.succeed();
	}

	@GameTest
	public void everyChapterAndBothTops(GameTestHelper helper) {
		for (EquipmentSlot slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.CHEST)) {
			Set<Chapter> seen = new HashSet<>();
			Set<Chapter> up = new HashSet<>(), down = new HashSet<>();
			Map<Integer, Integer> counts = new HashMap<>();
			for (ItemStack garment : garments(slot, true)) {
				Chapter chapter = chapter(garment);
				seen.add(chapter);
				(OvveItem.topUp(garment) ? up : down).add(chapter);
				counts.merge(placements(garment).size(), 1, Integer::sum);
			}
			helper.assertValueEqual(seen, new HashSet<>(MobOvvar.chapters(slot)), "the chapters seen in " + slot);
			Set<Chapter> rollable = seen.stream().filter(c -> c.rollable).collect(Collectors.toSet());
			if (slot == EquipmentSlot.LEGS) {
				helper.assertValueEqual(down, rollable, "the rollable chapters seen top-down");
				helper.assertValueEqual(up, seen, "the chapters seen top-up");
			}
			for (int n = 1; n <= 3; n++) {
				if (counts.getOrDefault(n, 0) < 400) helper.fail(slot + ": " + n + " patches seen only " + counts.getOrDefault(n, 0) + " times in " + SEEDS);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void hiddenCellsStayBare(GameTestHelper helper) {
		for (ItemStack garment : garments(EquipmentSlot.LEGS, true)) {
			if (!OvveItem.topUp(garment) && placements(garment).stream().anyMatch(p -> p.piece() == Piece.TOP)) helper.fail("a top-down ovve has a patch on its top");
		}
		for (ItemStack garment : garments(EquipmentSlot.LEGS, false)) {
			if (placements(garment).stream().anyMatch(p -> p.piece() == Piece.TOP)) helper.fail("a covered top has a patch");
		}
		helper.succeed();
	}

	@GameTest
	public void spawnPieceFollowsTheConfig(GameTestHelper helper) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		if (zombie == null) helper.fail("no zombie");
		MobOvvarConfig always = new MobOvvarConfig(true, 1f, .5f);
		ItemStack leggings = new ItemStack(Items.IRON_LEGGINGS), chest = new ItemStack(Items.IRON_CHESTPLATE);
		ItemStack legs = MobOvvar.spawnPiece(zombie, EquipmentSlot.LEGS, leggings, RandomSource.create(1), always);
		if (!MobOvvar.is(legs)) helper.fail("leggings at chance 1 stayed " + legs);
		helper.assertValueEqual(zombie.getDropChances().byEquipment(EquipmentSlot.LEGS), 0f, "the legs drop chance");
		ItemStack top = MobOvvar.spawnPiece(zombie, EquipmentSlot.CHEST, chest, RandomSource.create(2), always);
		if (!MobOvvar.is(top)) helper.fail("a chestplate at chance 1 stayed " + top);
		helper.assertValueEqual(chapter(top).slot, EquipmentSlot.CHEST, "the frack's slot");
		helper.assertValueEqual(zombie.getDropChances().byEquipment(EquipmentSlot.CHEST), 0f, "the chest drop chance");

		RandomSource random = RandomSource.create(3);
		helper.assertTrue(MobOvvar.spawnPiece(zombie, EquipmentSlot.LEGS, leggings, random, new MobOvvarConfig(true, 0f, .5f)) == leggings, "chance 0 keeps vanilla");
		helper.assertTrue(MobOvvar.spawnPiece(zombie, EquipmentSlot.LEGS, leggings, random, new MobOvvarConfig(false, 1f, .5f)) == leggings, "disabled keeps vanilla");
		ItemStack helmet = new ItemStack(Items.IRON_HELMET);
		helper.assertTrue(MobOvvar.spawnPiece(zombie, EquipmentSlot.HEAD, helmet, random, always) == helmet, "a helmet is never replaced");
		helper.assertTrue(MobOvvar.spawnPiece(zombie, EquipmentSlot.LEGS, ItemStack.EMPTY, random, always).isEmpty(), "nothing stays nothing");

		MobOvvarConfig rare = new MobOvvarConfig(true, 0.05f, .5f);
		// One stream: the first float of RandomSource.create(n) over consecutive n is a narrow, ordered band, not a sample.
		RandomSource stream = RandomSource.create(5);
		int made = 0;
		for (int i = 0; i < 10_000; i++) {
			if (MobOvvar.is(MobOvvar.spawnPiece(zombie, EquipmentSlot.LEGS, leggings, stream, rare))) made++;
		}
		if (made < 400 || made > 600) helper.fail(made + " garments in 10000 rolls at 0.05");
		helper.succeed();
	}

	@GameTest
	public void patchDropsAreTheWornPatches(GameTestHelper helper) {
		int worn = 0, dropped = 0;
		RandomSource stream = RandomSource.create(7);   // one stream for the odds: consecutive seeds' first floats are correlated
		for (int seed = 0; seed < SEEDS; seed++) {
			ItemStack garment = MobOvvar.garment(RandomSource.create(seed), seed % 2 == 0 ? EquipmentSlot.LEGS : EquipmentSlot.CHEST, true);
			List<String> ids = placements(garment).stream().map(p -> p.patch().id()).sorted().toList();
			List<String> all = MobOvvar.patchDrops(garment, RandomSource.create(seed), 1f).stream()
					.map(s -> ((se.metacraft.ovvar.content.item.PatchItem) s.getItem()).patch.id()).sorted().toList();
			helper.assertValueEqual(all, ids, "the drops at chance 1");
			if (!MobOvvar.patchDrops(garment, RandomSource.create(seed), 0f).isEmpty()) helper.fail("a drop at chance 0");
			worn += ids.size();
			dropped += MobOvvar.patchDrops(garment, stream, 0.5f).size();
		}
		double ratio = dropped / (double) worn;
		if (ratio < 0.45 || ratio > 0.55) helper.fail("drop ratio " + ratio + " at 0.5");
		helper.succeed();
	}
}
