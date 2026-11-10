package se.metacraft.ovvar.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

import com.mojang.serialization.JsonOps;
import se.metacraft.ovvar.PunkphozConfig;
import se.metacraft.ovvar.PunkphozConfig.PoolEntry;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.PatchLoot;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.entity.Punkphoz;
import se.metacraft.ovvar.content.entity.PunkphozEntities;
import se.metacraft.ovvar.content.entity.PunkphozHistory;
import se.metacraft.ovvar.content.entity.PunkphozSpawning;
import se.metacraft.ovvar.content.entity.PunkphozStock;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	/** His offers, a trade made on one of them, survive a save and load; loading him is not a visit. */
	@GameTest
	public void stockSurvivesSaveAndLoad(GameTestHelper helper) {
		var level = helper.getLevel();
		Punkphoz punkphoz = helper.spawn(PunkphozEntities.PUNKPHOZ, new BlockPos(1, 1, 1));
		MerchantOffers before = punkphoz.getOffers();
		if (before.isEmpty()) {
			helper.fail("Punkphöz has no stock to save");
			return;
		}
		before.getFirst().increaseUses();
		int visits = PunkphozHistory.get(level.getServer()).visits();
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
		helper.assertValueEqual(loaded.getOffers().getFirst().getUses(), 1, "the traded offer's uses after a save and load");
		helper.assertValueEqual(PunkphozHistory.get(level.getServer()).visits(), visits, "visits after loading him");
		helper.succeed();
	}

	private static final List<PoolEntry> SIX = List.of(entry("minecraft:apple", 1), entry("minecraft:bread", 2),
			entry("minecraft:diamond", 3), entry("minecraft:saddle", 4), entry("minecraft:name_tag", 5), entry("minecraft:cake", 6));

	private static PunkphozConfig withCounts(int chapterOvves, int poolPicks) {
		var d = PunkphozConfig.DEFAULT;
		return new PunkphozConfig(d.enabled(), d.share(), chapterOvves, d.ovvePrice(), d.patches(), d.patchPrice(),
				poolPicks, d.playersPerRoll(), d.maxLoaded(), List.of());
	}

	private static Set<String> lootIds() {
		Set<String> ids = new HashSet<>();
		for (Patches.Patch patch : PatchLoot.looted()) ids.add(BuiltInRegistries.ITEM.getKey(ModContent.patchItem(patch)).toString());
		return ids;
	}

	private static String id(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static Chapter chapterOf(ItemStack stack) {
		for (Chapter chapter : Chapter.values()) if (stack.is(ModContent.ovve(chapter))) return chapter;
		return null;
	}

	@GameTest
	public void rollHasExactCounts(GameTestHelper helper) {
		var stock = PunkphozStock.roll(PunkphozConfig.DEFAULT, SIX, RandomSource.create(42), new PunkphozHistory());
		helper.assertValueEqual(stock.size(), 9, "offers");
		Set<Chapter> chapters = EnumSet.noneOf(Chapter.class);
		for (var s : stock.subList(0, 3)) {
			Chapter chapter = chapterOf(s.result());
			if (chapter == null || !PunkphozStock.EXCLUSIVE.contains(chapter)) helper.fail("not an exclusive ovve: " + id(s.result()));
			chapters.add(chapter);
			helper.assertValueEqual(s.emeralds(), 24, "an ovve's price");
			helper.assertValueEqual(s.maxUses(), 1, "an ovve's max uses");
		}
		helper.assertValueEqual(chapters.size(), 3, "distinct chapters");
		Set<String> loot = lootIds(), patches = new HashSet<>();
		for (var s : stock.subList(3, 5)) {
			if (!loot.contains(id(s.result()))) helper.fail("not a loot patch: " + id(s.result()));
			patches.add(id(s.result()));
			helper.assertValueEqual(s.emeralds(), 6, "a patch's price");
		}
		helper.assertValueEqual(patches.size(), 2, "distinct patches");
		Map<String, Integer> prices = new HashMap<>();
		for (PoolEntry e : SIX) prices.put(e.item().toString(), e.price());
		Set<String> picks = new HashSet<>();
		for (var s : stock.subList(5, 9)) {
			Integer price = prices.get(id(s.result()));
			if (price == null) helper.fail("not from the pool: " + id(s.result()));
			helper.assertValueEqual(s.emeralds(), price, "the price of " + id(s.result()));
			picks.add(id(s.result()));
		}
		helper.assertValueEqual(picks.size(), 4, "distinct pool picks");
		helper.succeed();
	}

	@GameTest
	public void patchesOnlyFromLoot(GameTestHelper helper) {
		RandomSource random = RandomSource.create(7);
		Set<String> nonLoot = new HashSet<>();
		for (String patch : PatchLoot.NON_LOOT) nonLoot.add("ovvar:patch_" + patch);
		Set<String> loot = lootIds();
		for (int i = 0; i < 2000; i++) {
			for (var s : PunkphozStock.roll(withCounts(0, 0), List.of(), random, new PunkphozHistory())) {
				String id = id(s.result());
				if (!loot.contains(id) || nonLoot.contains(id)) {
					helper.fail("a patch not from PatchLoot.LOOT: " + id);
					return;
				}
			}
		}
		helper.succeed();
	}

	@GameTest
	public void chapterPicksFavourTheLongestWaiting(GameTestHelper helper) {
		Chapter waiting = PunkphozStock.EXCLUSIVE.iterator().next();
		Map<String, Integer> last = new HashMap<>();
		for (Chapter chapter : PunkphozStock.EXCLUSIVE) if (chapter != waiting) last.put(chapter.id, 9);
		RandomSource random = RandomSource.create(42);
		int picked = 0;
		for (int i = 0; i < 1000; i++) {
			var stock = PunkphozStock.roll(withCounts(1, 0), List.of(), random, new PunkphozHistory(10, last));
			if (stock.getFirst().result().is(ModContent.ovve(waiting))) picked++;
		}
		if (picked < 400) helper.fail(waiting.id + " (never offered) picked only " + picked + " of 1000 times");
		helper.succeed();
	}

	@GameTest
	public void historyRecordsTheVisit(GameTestHelper helper) {
		PunkphozHistory history = new PunkphozHistory();
		var stock = PunkphozStock.roll(PunkphozConfig.DEFAULT, List.of(), RandomSource.create(42), history);
		helper.assertValueEqual(history.visits(), 1, "visits");
		int offered = 0;
		for (Chapter chapter : PunkphozStock.EXCLUSIVE) {
			boolean inStock = stock.stream().anyMatch(s -> s.result().is(ModContent.ovve(chapter)));
			helper.assertValueEqual(history.lastOffered(chapter), inStock ? 0 : -1, "lastOffered(" + chapter.id + ")");
			if (inStock) offered++;
		}
		helper.assertValueEqual(offered, 3, "chapters offered");
		helper.succeed();
	}

	@GameTest
	public void fewerExclusivesThanAsked(GameTestHelper helper) {
		var stock = PunkphozStock.roll(withCounts(99, 0), List.of(), RandomSource.create(42), new PunkphozHistory());
		Set<Chapter> chapters = EnumSet.noneOf(Chapter.class);
		int ovves = 0;
		for (var s : stock) {
			Chapter chapter = chapterOf(s.result());
			if (chapter == null) continue;
			ovves++;
			chapters.add(chapter);
		}
		helper.assertValueEqual(ovves, PunkphozStock.EXCLUSIVE.size(), "ovve offers");
		helper.assertValueEqual(chapters, EnumSet.copyOf(PunkphozStock.EXCLUSIVE), "chapters offered");
		helper.succeed();
	}

	@GameTest
	public void poolSmallerThanPicks(GameTestHelper helper) {
		var stock = PunkphozStock.roll(withCounts(0, 4), SIX.subList(0, 2), RandomSource.create(42), new PunkphozHistory());
		var pool = stock.subList(PunkphozConfig.DEFAULT.patches(), stock.size()).stream().map(s -> id(s.result())).toList();
		helper.assertValueEqual(pool.size(), 2, "pool offers");
		helper.assertValueEqual(new HashSet<>(pool), Set.of("minecraft:apple", "minecraft:bread"), "pool offers");
		helper.succeed();
	}

	@GameTest
	public void nothingButPatches(GameTestHelper helper) {
		var stock = PunkphozStock.roll(withCounts(0, 4), List.of(), RandomSource.create(42), new PunkphozHistory());
		helper.assertValueEqual(stock.size(), 2, "offers");
		Set<String> loot = lootIds();
		for (var s : stock) if (!loot.contains(id(s.result()))) helper.fail("not a patch: " + id(s.result()));
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

	/** The default pool is kultur's 19 chapter patterns plus Pirkko: all there with kultur loaded, all warned about without. */
	@GameTest
	public void defaultPoolMatchesKultur(GameTestHelper helper) {
		var resolved = PunkphozConfig.resolve(PunkphozConfig.DEFAULT);
		if (FabricLoader.getInstance().isModLoaded("metacraft-kultur")) {
			helper.assertValueEqual(resolved.entries().size(), 20, "entries with kultur");
			helper.assertValueEqual(resolved.warnings(), List.<String>of(), "warnings with kultur");
		} else {
			helper.assertValueEqual(resolved.entries().size(), 0, "entries without kultur");
			helper.assertValueEqual(resolved.warnings().size(), 20, "warnings without kultur");
			for (String warning : resolved.warnings()) {
				if (!warning.contains("kultur:")) helper.fail("a warning that names no kultur id: " + warning);
			}
		}
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

	/** Vanilla's visits per attempt: the 25/50/75 ramp, then spawn's one-in-ten gate. */
	private static final double VANILLA_RATE = 1520.0 / 21487;

	private static PunkphozConfig spawning(boolean enabled, double share, int playersPerRoll, int maxLoaded) {
		var d = PunkphozConfig.DEFAULT;
		return new PunkphozConfig(enabled, share, d.chapterOvves(), d.ovvePrice(), d.patches(), d.patchPrice(),
				d.poolPicks(), playersPerRoll, maxLoaded, List.of());
	}

	private static PunkphozConfig share(double share) {
		return spawning(true, share, PunkphozConfig.DEFAULT.playersPerRoll(), PunkphozConfig.DEFAULT.maxLoaded());
	}

	private static void near(GameTestHelper helper, double actual, double expected, double tolerance, String what) {
		if (Math.abs(actual - expected) > tolerance) helper.fail(what + ": " + actual + ", expected " + expected + " ± " + tolerance);
	}

	@GameTest
	public void vanillaRateIsTheRamp(GameTestHelper helper) {
		near(helper, PunkphozSpawning.vanillaRate(), VANILLA_RATE, 1e-9, "vanillaRate()");
		helper.succeed();
	}

	@GameTest
	public void quarterShareChance(GameTestHelper helper) {
		near(helper, PunkphozSpawning.chanceOnAFailedRoll(share(0.25)), 0.0805896, 1e-6, "chance at share 0.25");
		near(helper, PunkphozSpawning.chanceOnAFailedRoll(share(0.5)), 0.2417687, 1e-6, "chance at share 0.5");
		near(helper, PunkphozSpawning.chanceOnAFailedRoll(share(0)), 0, 0, "chance at share 0");
		near(helper, PunkphozSpawning.chanceOnAFailedRoll(spawning(false, 0.5, 5, 3)), 0, 0, "chance when disabled");
		helper.succeed();
	}

	@GameTest
	public void shareIsCappedInConfig(GameTestHelper helper) {
		JsonObject json = new JsonObject();
		json.addProperty("share", 0.6);
		var result = PunkphozConfig.CODEC.codec().parse(JsonOps.INSTANCE, json);
		if (result.error().isEmpty()) {
			helper.fail("share 0.6 decoded without an error: " + result);
			return;
		}
		String message = result.error().get().message();
		// DFU's doubleRange: "Value 0.6 outside of range [0.0:0.5]".
		if (!message.contains("0.6") || !message.contains("[0.0:0.5]")) helper.fail("the error names neither the value nor the range: " + message);
		helper.succeed();
	}

	@GameTest
	public void vanillaRollIsUntouched(GameTestHelper helper) {
		RandomSource random = RandomSource.create(5);
		for (int chance : new int[] {25, 50, 75}) {
			for (int roll = 0; roll < 100; roll++) {
				var outcome = PunkphozSpawning.decide(chance, roll, share(0.5), random);
				if ((outcome == PunkphozSpawning.Outcome.VANILLA_ROLL) != (roll <= chance)) {
					helper.fail("chance " + chance + ", roll " + roll + ": " + outcome);
					return;
				}
				if (roll <= chance && outcome == PunkphozSpawning.Outcome.PUNKPHOZ) {
					helper.fail("Punkphöz on vanilla's passing roll: chance " + chance + ", roll " + roll);
					return;
				}
			}
		}
		helper.succeed();
	}

	/**
	 * {vanilla visits, Punkphöz visits} over {@code attempts} of vanilla's ramp; with players, his visits from targets().
	 * A passing vanilla roll is a visit only through spawn's one-in-ten gate, and only a visit resets the ramp; his
	 * spawns skip the gate.
	 */
	private static long[] simulate(PunkphozConfig config, long seed, int attempts, int players) {
		RandomSource random = RandomSource.create(seed);
		List<String> online = new ArrayList<>();
		for (int i = 0; i < players; i++) online.add("p" + i);
		long vanilla = 0, punkphoz = 0;
		int state = 25;
		for (int i = 0; i < attempts; i++) {
			int chance = state;
			state = Math.min(state + 25, 75);
			int roll = random.nextInt(100);
			if (roll <= chance) {
				if (random.nextInt(10) == 0) {
					vanilla++;
					state = 25;
				}
			} else if (players == 0) {
				if (PunkphozSpawning.decide(chance, roll, config, random) == PunkphozSpawning.Outcome.PUNKPHOZ) punkphoz++;
			} else {
				punkphoz += PunkphozSpawning.targets(online, 0, config, random).size();
			}
		}
		return new long[] {vanilla, punkphoz};
	}

	@GameTest
	public void vanillaRateIsUnchanged(GameTestHelper helper) {
		int attempts = 1000000;
		long[] quarter = simulate(share(0.25), 11, attempts, 0);
		near(helper, (double) quarter[0] / attempts, VANILLA_RATE, 0.002, "vanilla visits/attempt at share 0.25");
		near(helper, (double) quarter[1] / (quarter[0] + quarter[1]), 0.25, 0.01, "Punkphöz's share at 0.25");
		long[] off = simulate(spawning(false, 0.25, 5, 3), 11, attempts, 0);
		near(helper, (double) off[0] / attempts, VANILLA_RATE, 0.002, "vanilla visits/attempt when disabled");
		helper.assertValueEqual(off[1], 0L, "Punkphöz visits when disabled");
		long[] half = simulate(share(0.5), 11, attempts, 0);
		near(helper, (double) half[0] / attempts, VANILLA_RATE, 0.002, "vanilla visits/attempt at share 0.5");
		near(helper, (double) half[1] / (half[0] + half[1]), 0.5, 0.01, "Punkphöz's share at 0.5");
		helper.succeed();
	}

	@GameTest
	public void rollsPerPlayers(GameTestHelper helper) {
		var five = spawning(true, 0.25, 5, 3);
		int[] players = {0, 1, 5, 6, 20};
		int[] expected = {0, 1, 1, 2, 4};
		for (int i = 0; i < players.length; i++) {
			helper.assertValueEqual(PunkphozSpawning.rolls(players[i], five), expected[i], "rolls for " + players[i] + " players");
		}
		helper.assertValueEqual(PunkphozSpawning.rolls(3, spawning(true, 0.25, 1, 3)), 3, "rolls for 3 players at 1 per roll");
		helper.assertValueEqual(PunkphozSpawning.rolls(20, spawning(false, 0.25, 5, 3)), 0, "rolls when disabled");
		helper.succeed();
	}

	@GameTest
	public void distinctTargets(GameTestHelper helper) {
		List<String> players = new ArrayList<>();
		for (int i = 0; i < 20; i++) players.add("p" + i);
		var config = spawning(true, 0.5, 5, 20);
		RandomSource random = RandomSource.create(3);
		Set<String> picked = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			List<String> targets = PunkphozSpawning.targets(players, 0, config, random);
			if (new HashSet<>(targets).size() != targets.size()) {
				helper.fail("a player picked twice: " + targets);
				return;
			}
			if (targets.size() > 4) {
				helper.fail("more than 4 targets: " + targets);
				return;
			}
			picked.addAll(targets);
		}
		helper.assertValueEqual(picked.size(), 20, "players ever picked");
		helper.succeed();
	}

	@GameTest
	public void capRespected(GameTestHelper helper) {
		List<String> players = new ArrayList<>();
		for (int i = 0; i < 20; i++) players.add("p" + i);
		var config = spawning(true, 0.25, 1, 3);
		int[] loaded = {0, 2, 3, 5};
		int[] expected = {3, 1, 0, 0};
		for (int i = 0; i < loaded.length; i++) {
			int size = PunkphozSpawning.targets(players, loaded[i], config, 1.0, RandomSource.create(i)).size();
			helper.assertValueEqual(size, expected[i], "targets with " + loaded[i] + " loaded");
		}
		helper.succeed();
	}

	@GameTest
	public void vanillaRateIsUnchangedWithPlayers(GameTestHelper helper) {
		int attempts = 1000000;
		var config = spawning(true, 0.25, 5, 20);
		long[] visits = simulate(config, 11, attempts, 20);
		near(helper, (double) visits[0] / attempts, VANILLA_RATE, 0.002, "vanilla visits/attempt with 20 players");
		double x = PunkphozSpawning.chanceOnAFailedRoll(config);
		near(helper, (double) visits[1] / attempts, 4 * x * (1 - 10 * VANILLA_RATE), 0.01, "Punkphöz visits/attempt with 20 players");
		helper.succeed();
	}

	@GameTest
	public void spawnerMixinIsIn(GameTestHelper helper) {
		Set<String> names = new HashSet<>();
		for (var method : WanderingTraderSpawner.class.getDeclaredMethods()) names.add(method.getName());
		for (String handler : List.of("punkphozRoll", "punkphozType", "punkphozNear", "punkphozGate")) {
			if (names.stream().noneMatch(n -> n.contains(handler))) helper.fail("WanderingTraderSpawner has no " + handler + ": " + names);
		}
		helper.succeed();
	}

	@GameTest
	public void typeOnlySwapsWhileSpawning(GameTestHelper helper) {
		if (PunkphozSpawning.TARGET.get() != null) helper.fail("TARGET is set outside a spawn");
		var level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		var source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
		level.getServer().getCommands().performPrefixedCommand(source,
				"summon minecraft:wandering_trader " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
		var traders = level.getEntities(EntityTypeTest.forClass(WanderingTrader.class), new AABB(pos).inflate(2), e -> true);
		if (traders.isEmpty()) {
			helper.fail("/summon made no wandering trader");
			return;
		}
		for (WanderingTrader trader : traders) {
			if (trader instanceof Punkphoz) helper.fail("/summon wandering_trader made a Punkphöz");
			helper.assertValueEqual(trader.getType(), EntityTypes.WANDERING_TRADER, "the summoned trader's type");
			trader.discard();
		}
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
