package se.metacraft.ovvar.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import com.mojang.serialization.JsonOps;
import se.metacraft.ovvar.PunkphozConfig;
import se.metacraft.ovvar.PunkphozConfig.PoolEntry;
import se.metacraft.ovvar.content.entity.Punkphoz;
import se.metacraft.ovvar.content.entity.PunkphozEntities;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Punkphöz: a wandering trader wearing the traders' model, with no invisibility potion to drink. */
public final class PunkphozTests {

	@GameTest
	public void punkphozIsAWanderingTrader(GameTestHelper helper) {
		var punkphoz = PunkphozEntities.PUNKPHOZ.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		if (!(punkphoz instanceof WanderingTrader)) helper.fail("Punkphöz is not a wandering trader: " + punkphoz);
		helper.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.getKey(PunkphozEntities.PUNKPHOZ),
				Identifier.fromNamespaceAndPath("ovvar", "punkphoz"), "Punkphöz's entity id");
		helper.succeed();
	}

	/** The model is the traders' IT trader, copied in at build time; in its format 5 file the outliner names groups by uuid. */
	@GameTest
	public void modelIsTheTradersPunkphoz(GameTestHelper helper) {
		try (InputStream in = Punkphoz.class.getResourceAsStream("/model/ovvar/punkphoz.bbmodel")) {
			if (in == null) {
				helper.fail("no /model/ovvar/punkphoz.bbmodel");
				return;
			}
			JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, String> groupNames = new HashMap<>();
			if (model.has("groups")) {
				for (var group : model.getAsJsonArray("groups")) {
					var g = group.getAsJsonObject();
					groupNames.put(g.get("uuid").getAsString(), g.get("name").getAsString());
				}
			}
			boolean head = false;
			for (var entry : model.getAsJsonArray("outliner")) {
				if (!entry.isJsonObject()) continue;
				var group = entry.getAsJsonObject();
				String name = group.has("name") ? group.get("name").getAsString() : groupNames.get(group.get("uuid").getAsString());
				if ("head".equals(name)) head = true;
			}
			if (!head) helper.fail("the model's outliner has no group named head");
		} catch (IOException e) {
			helper.fail("reading the model: " + e);
		}
		if (PunkphozEntities.model() == null) helper.fail("PunkphozEntities.model() is null");
		helper.succeed();
	}

	@GameTest
	public void noInvisibilityPotion(GameTestHelper helper) {
		Punkphoz punkphoz = helper.spawn(PunkphozEntities.PUNKPHOZ, new BlockPos(1, 1, 1));
		List<Goal> goals = goals(punkphoz).getAvailableGoals().stream().map(WrappedGoal::getGoal).toList();
		if (goals.stream().anyMatch(g -> g instanceof UseItemGoal)) helper.fail("Punkphöz still has a UseItemGoal (a potion or milk)");
		if (goals.stream().noneMatch(g -> g instanceof TradeWithPlayerGoal)) helper.fail("Punkphöz has no TradeWithPlayerGoal");
		if (goals.stream().noneMatch(g -> g.getClass().getSimpleName().equals("WanderToPositionGoal"))) {
			helper.fail("Punkphöz has no WanderToPositionGoal");
		}
		helper.succeed();
	}

	@GameTest
	public void leavesLikeAWanderer(GameTestHelper helper) {
		Punkphoz punkphoz = helper.spawn(PunkphozEntities.PUNKPHOZ, new BlockPos(1, 1, 1));
		punkphoz.setDespawnDelay(5);
		helper.runAfterDelay(20, () -> {
			if (punkphoz.isAlive()) helper.fail("Punkphöz is still here after his despawn delay ran out");
			helper.succeed();
		});
	}

	/** His offers survive a save and load. (They are empty until he has stock.) */
	@GameTest
	public void stockSurvivesSaveAndLoad(GameTestHelper helper) {
		var level = helper.getLevel();
		Punkphoz punkphoz = helper.spawn(PunkphozEntities.PUNKPHOZ, new BlockPos(1, 1, 1));
		MerchantOffers before = punkphoz.getOffers();
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		punkphoz.saveWithoutId(output);
		CompoundTag saved = output.buildResult();

		Punkphoz loaded = PunkphozEntities.PUNKPHOZ.create(level, EntitySpawnReason.LOAD);
		if (loaded == null) {
			helper.fail("no fresh Punkphöz to load into");
			return;
		}
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
		helper.assertValueEqual(describe(loaded.getOffers()), describe(before), "the offers after a save and load");
		helper.succeed();
	}

	private static PoolEntry entry(String item, int price) {
		return new PoolEntry(Identifier.parse(item), price, 1, 1);
	}

	private static PunkphozConfig withPool(PoolEntry... pool) {
		var d = PunkphozConfig.DEFAULT;
		return new PunkphozConfig(d.enabled(), d.share(), d.chapterOvves(), d.ovvePrice(), d.patches(), d.patchPrice(),
				d.poolPicks(), d.playersPerRoll(), d.maxLoaded(), List.of(pool));
	}

	@GameTest
	public void defaultsAreWritten(GameTestHelper helper) {
		JsonObject json = PunkphozConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, PunkphozConfig.DEFAULT).getOrThrow().getAsJsonObject();
		for (String key : List.of("enabled", "share", "chapter_ovves", "ovve_price", "patches", "patch_price", "pool_picks",
				"players_per_roll", "max_loaded", "pool")) {
			if (!json.has(key)) helper.fail("the encoded default has no '" + key + "': " + json);
		}
		helper.assertValueEqual(json.size(), 10, "keys written");
		helper.succeed();
	}

	@GameTest
	public void poolSkipsMissingItems(GameTestHelper helper) {
		var resolved = PunkphozConfig.resolve(withPool(entry("nosuchmod:thing", 3), entry("minecraft:emerald_block", 5)));
		helper.assertValueEqual(resolved.entries().stream().map(PoolEntry::item).toList(),
				List.of(Identifier.parse("minecraft:emerald_block")), "entries");
		helper.assertValueEqual(resolved.warnings(),
				List.of("Punkphöz: leaving out nosuchmod:thing from the pool: it isn't registered"), "warnings");
		helper.succeed();
	}

	@GameTest
	public void poolRejectsGivenOnlyPatches(GameTestHelper helper) {
		var resolved = PunkphozConfig.resolve(withPool(entry("ovvar:patch_rivals", 4), entry("ovvar:patch_tmeit-marshal", 4),
				entry("ovvar:patch_metacrew", 4), entry("ovvar:patch_spiken", 4)));
		helper.assertValueEqual(resolved.entries().stream().map(e -> e.item().getPath()).toList(), List.of("patch_spiken"), "entries");
		helper.assertValueEqual(resolved.warnings(), List.of(
				"Punkphöz: leaving out ovvar:patch_rivals from the pool: patch 'rivals' is given-only (PatchLoot.NON_LOOT)",
				"Punkphöz: leaving out ovvar:patch_tmeit-marshal from the pool: patch 'tmeit-marshal' is given-only (PatchLoot.NON_LOOT)",
				"Punkphöz: leaving out ovvar:patch_metacrew from the pool: patch 'metacrew' is given-only (PatchLoot.NON_LOOT)"), "warnings");
		helper.succeed();
	}

	@GameTest
	public void poolWarningsOncePerLoad(GameTestHelper helper) {
		var config = withPool(entry("nosuchmod:thing", 3), entry("ovvar:patch_rivals", 4), entry("minecraft:emerald_block", 5));
		int before = PunkphozConfig.warningsLogged();
		for (int i = 0; i < 50; i++) PunkphozConfig.poolOnOffer(config);
		helper.assertValueEqual(PunkphozConfig.warningsLogged() - before, 2, "warnings logged over 50 calls");
		helper.succeed();
	}

	private static List<String> describe(MerchantOffers offers) {
		return offers.stream().map(PunkphozTests::describe).toList();
	}

	private static String describe(MerchantOffer offer) {
		return describe(offer.getResult()) + " for " + describe(offer.getBaseCostA()) + " + " + describe(offer.getCostB())
				+ ", used " + offer.getUses() + "/" + offer.getMaxUses();
	}

	private static String describe(ItemStack stack) {
		return stack.isEmpty() ? "nothing" : stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	private static GoalSelector goals(Mob mob) {
		try {
			Field field = Mob.class.getDeclaredField("goalSelector");
			field.setAccessible(true);
			return (GoalSelector) field.get(mob);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Mob.goalSelector", e);
		}
	}
}
