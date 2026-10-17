package metacraft.kultur.gametest;

import eu.pb4.polymer.core.api.other.PolymerMobEffect;
import metacraft.kultur.drinks.Alcohol;
import metacraft.kultur.drinks.DrunkTicker;
import metacraft.kultur.drinks.Drunkenness;
import metacraft.kultur.drinks.KulturDamageTypes;
import metacraft.kultur.drinks.KulturDrinks;
import metacraft.kultur.drinks.ToClient;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The chapter drinks without Brewery ({@link KulturDrinks}). Every test here passes at once when
 * Brewery is loaded, as {@code KulturDrinkTests} does without it. Run the other mode with
 * {@code -PkulturNoBrewery}.
 *
 * <p>A mock player is not ticked as a real one is (no client drives it), so the tests tick its
 * drunkenness themselves ({@link #ticks}), counting its effects down as the server would.
 */
public final class KulturVanillaDrinkTests {
	@GameTest
	public void drinksAreActiveWithoutBrewery(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		helper.assertTrue(KulturDrinks.active(), "the drinks are not active without Brewery");
		helper.assertTrue(KulturDrinks.registered(), "the drinks were not registered");
		helper.succeed();
	}

	// --- The meter: kept with the player, cleared by death ---

	/** A restart mid-drunk: the meter comes back from the player's save, and Inebriated with it within a second. */
	@GameTest
	public void meterSurvivesSaveAndLoad(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 55);
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		player.saveWithoutId(out);
		CompoundTag saved = out.buildResult();
		saved.remove("UUID");   // loaded into another mock player, which keeps its own
		saved.remove("active_effects");   // so Inebriated has to come back from the meter, not from the save

		ServerPlayer loaded = drinker(helper);
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
		helper.assertValueEqual(Drunkenness.get(loaded), 55.0, "alcohol after save and load");
		ticks(helper, loaded, DrunkTicker.REFRESH_EVERY);
		MobEffectInstance inebriated = loaded.getEffect(KulturDrinks.INEBRIATED);
		helper.assertTrue(inebriated != null, "no Inebriated a second after loading");
		helper.assertValueEqual(inebriated.getAmplifier(), 3, "Inebriated's level after loading (IV)");
		sober(player, loaded);
		helper.succeed();
	}

	@GameTest
	public void deathClearsTheMeter(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 55);
		ticks(helper, player, 80);
		helper.assertTrue(player.hasEffect(KulturDrinks.INEBRIATED), "not Inebriated before dying");
		ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		ticks(helper, respawned, 20);
		helper.assertValueEqual(Drunkenness.get(respawned), 0.0, "alcohol after death");
		helper.assertTrue(respawned.getActiveEffects().isEmpty(), "effects after death: " + respawned.getActiveEffects());
		sober(respawned);
		helper.succeed();
	}

	/** A live respawn (leaving the End) is not a death: Brewery copies the meter then, and so does this. */
	@GameTest
	public void liveRespawnKeepsTheMeter(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 55);
		ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(player, true, Entity.RemovalReason.CHANGED_DIMENSION);
		helper.assertValueEqual(Drunkenness.get(respawned), 55.0, "alcohol after a live respawn");
		sober(respawned);
		helper.succeed();
	}

	// --- The steps ---

	@GameTest
	public void meterDrains(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		Drunkenness.set(player, 60);
		ticks(helper, player, 1200);
		helper.assertTrue(Math.abs(Drunkenness.get(player) - 45.6) < 1e-6, "alcohol after a minute from 60: " + Drunkenness.get(player));
		Drunkenness.set(player, 0.005);
		ticks(helper, player, 1);
		helper.assertValueEqual(Drunkenness.get(player), 0.0, "alcohol drained past zero");
		sober(player);
		helper.succeed();
	}

	@GameTest
	public void stepEffectsAtTheirLevels(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		player.setHealth(player.getMaxHealth());

		at(player, 35);
		ticks(helper, player, 80);
		shows(helper, player, MobEffects.WEAKNESS, "at 35");
		helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "slowness at 35");

		at(player, 45);
		ticks(helper, player, 80);
		shows(helper, player, MobEffects.WEAKNESS, "at 45");
		shows(helper, player, MobEffects.SLOWNESS, "at 45");
		helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "nausea at 45");

		at(player, 65);
		ticks(helper, player, 320);
		shows(helper, player, MobEffects.NAUSEA, "at 65");
		helper.assertFalse(player.hasEffect(MobEffects.DARKNESS), "darkness at 65");

		at(player, 85);
		player.setHealth(player.getMaxHealth());
		ticks(helper, player, 320);
		shows(helper, player, MobEffects.DARKNESS, "at 85");
		shows(helper, player, MobEffects.WEAKNESS, "at 85");
		shows(helper, player, MobEffects.SLOWNESS, "at 85");
		shows(helper, player, MobEffects.NAUSEA, "at 85");
		sober(player);
		helper.succeed();
	}

	@GameTest
	public void inebriatedShowsTheStep(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		double[] levels = {10, 35, 45, 55, 65, 75, 85};
		for (int i = 0; i < levels.length; i++) {
			at(player, levels[i]);
			ticks(helper, player, 20);
			MobEffectInstance inebriated = player.getEffect(KulturDrinks.INEBRIATED);
			helper.assertTrue(inebriated != null, "no Inebriated at " + levels[i]);
			helper.assertValueEqual(inebriated.getAmplifier(), i, "Inebriated's amplifier at " + levels[i]);
			helper.assertTrue(inebriated.showIcon(), "Inebriated hides its icon");
			int sober = Alcohol.ticksUntilSober(Drunkenness.get(player));
			helper.assertTrue(Math.abs(inebriated.getDuration() - sober) <= 20,
					"Inebriated's timer at " + levels[i] + ": " + inebriated.getDuration() + ", sober in " + sober);
		}
		// Sobering from 85 to 75 steps Inebriated down at once, timer and all.
		at(player, 75);
		ticks(helper, player, 20);
		helper.assertValueEqual(player.getEffect(KulturDrinks.INEBRIATED).getAmplifier(), 5, "Inebriated's amplifier after stepping down to 75");
		at(player, 0);
		ticks(helper, player, 1);
		helper.assertFalse(player.hasEffect(KulturDrinks.INEBRIATED), "Inebriated outlived the alcohol");
		sober(player);
		helper.succeed();
	}

	@GameTest
	public void inebriatedLooksLikeLuck(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		MobEffect effect = KulturDrinks.INEBRIATED.value();
		helper.assertTrue(effect instanceof PolymerMobEffect, "Inebriated is not a Polymer effect");
		MobEffect shown = ((PolymerMobEffect) effect).getPolymerReplacement(effect, PacketContext.get());
		helper.assertTrue(shown == MobEffects.LUCK.value(), "a vanilla client is shown " + shown + ", not Luck");
		helper.succeed();
	}

	/** The stagger is a push the player's own client is sent; the server does not move a player. */
	@GameTest
	public void staggerMovesThePlayer(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		at(player, 75);
		List<Vec3> pushes = pushesTo(player, () -> ticks(helper, player, 40));
		helper.assertValueEqual(pushes.size(), 10, "pushes in 40 ticks (one every 4)");
		for (Vec3 push : pushes) {
			helper.assertTrue(push.horizontalDistance() > 0, "a push with no sideways part: " + push);
			helper.assertTrue(push.length() <= 0.2 + 1e-9, "a push past Brewery's cap: " + push);
		}
		at(player, 45);
		helper.assertTrue(pushesTo(player, () -> ticks(helper, player, 40)).isEmpty(), "pushed at 45, below the stagger's step");
		sober(player);
		helper.succeed();
	}

	@GameTest
	public void staggerSkipsRidingAndFlying(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer rider = drinker(helper);
		Boat boat = EntityTypes.OAK_BOAT.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		boat.snapTo(rider.position());
		helper.getLevel().addFreshEntity(boat);
		helper.assertTrue(rider.startRiding(boat), "the drinker could not get in the boat");
		at(rider, 75);
		helper.assertTrue(pushesTo(rider, () -> ticks(helper, rider, 40)).isEmpty(), "a player in a boat was pushed");
		rider.stopRiding();
		boat.discard();

		ServerPlayer flier = drinker(helper);
		flier.setGameMode(GameType.CREATIVE);
		flier.getAbilities().flying = true;
		at(flier, 75);
		helper.assertTrue(pushesTo(flier, () -> ticks(helper, flier, 40)).isEmpty(), "a flying player was pushed");
		sober(rider, flier);
		helper.succeed();
	}

	@GameTest
	public void poisoningNeverKills(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		at(player, 75);
		player.setHealth(3);
		ticks(helper, player, 400);
		helper.assertTrue(player.isAlive(), "alcohol poisoning killed the drinker");
		helper.assertTrue(player.getHealth() >= 1, "alcohol poisoning took the drinker below half a heart: " + player.getHealth());
		helper.assertTrue(player.getHealth() < 3, "no alcohol poisoning at 75");
		DamageSource last = player.getLastDamageSource();
		helper.assertTrue(last != null && last.is(KulturDamageTypes.ALCOHOL_POISONING), "last damage was " + last);
		sober(player);
		helper.succeed();
	}

	/** An effect refreshed on its rate must outlast the rate, or its icon blinks out between refreshes. */
	@GameTest
	public void stepEffectsDoNotFlicker(GameTestHelper helper) {
		if (!vanilla(helper)) return;
		ServerPlayer player = drinker(helper);
		at(player, 99);   // still at step VII after the 960 ticks below
		player.setHealth(player.getMaxHealth());
		ticks(helper, player, 320);   // every step's effect has come once
		List<String> gaps = new ArrayList<>();
		for (int i = 0; i < 640; i++) {
			ticks(helper, player, 1);
			for (Holder<MobEffect> effect : List.of(MobEffects.WEAKNESS, MobEffects.SLOWNESS, MobEffects.NAUSEA, MobEffects.DARKNESS, KulturDrinks.INEBRIATED)) {
				if (!player.hasEffect(effect) && gaps.size() < 10) gaps.add(effect.getRegisteredName() + " at tick " + player.tickCount);
			}
		}
		helper.assertTrue(gaps.isEmpty(), "effects blinked out: " + gaps);
		sober(player);
		helper.succeed();
	}

	// --- Helpers ---

	/** True when this mode is on; otherwise the test has nothing to do and succeeds. */
	private static boolean vanilla(GameTestHelper helper) {
		if (KulturDrinks.active()) return true;
		helper.succeed();   // Brewery brews the drinks; KulturDrinkTests covers that
		return false;
	}

	/** A sober survival player who can take damage. */
	static ServerPlayer drinker(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// A player takes no damage until their client says it has loaded; a mock has no client to say it.
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.setGameMode(GameType.SURVIVAL);
		player.removeAllEffects();
		Drunkenness.set(player, 0);
		player.tickCount = 0;
		return player;
	}

	/** Sets the meter and starts the player's tick count over, so every rate comes round in a known number of ticks. */
	static void at(ServerPlayer player, double alcohol) {
		Drunkenness.set(player, alcohol);
		player.tickCount = 0;
	}

	/**
	 * {@code n} server ticks of a player's drunkenness: the tick count goes up, effects count down
	 * and the hurt cooldown wears off as on a real player, then {@link DrunkTicker} runs.
	 */
	static void ticks(GameTestHelper helper, ServerPlayer player, int n) {
		for (int i = 0; i < n; i++) {
			player.tickCount++;
			if (player.getInvulnerableTime() > 0) player.setInvulnerableTime(player.getInvulnerableTime() - 1);
			for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
				if (!effect.tickServer(helper.getLevel(), player, () -> {})) player.removeEffect(effect.getEffect());
			}
			DrunkTicker.tick(player);
		}
	}

	/** The stagger's pushes sent to this player's client while {@code during} runs. */
	static List<Vec3> pushesTo(ServerPlayer player, Runnable during) {
		List<Vec3> pushes = new ArrayList<>();
		try (var watch = ToClient.watch((to, packet) -> {
			if (to == player && packet instanceof ClientboundExplodePacket explode) explode.playerKnockback().ifPresent(pushes::add);
		})) {
			during.run();
		}
		return pushes;
	}

	/** Packets of a type sent to this player's client while {@code during} runs. */
	static <P extends Packet<?>> List<P> sentTo(ServerPlayer player, Class<P> type, Runnable during) {
		List<P> sent = new ArrayList<>();
		try (var watch = ToClient.watch((to, packet) -> {
			if (to == player && type.isInstance(packet)) sent.add(type.cast(packet));
		})) {
			during.run();
		}
		return sent;
	}

	static void shows(GameTestHelper helper, ServerPlayer player, Holder<MobEffect> effect, String when) {
		MobEffectInstance instance = player.getEffect(effect);
		helper.assertTrue(instance != null, "no " + effect.getRegisteredName() + " " + when);
		helper.assertTrue(instance.showIcon(), effect.getRegisteredName() + " hides its icon " + when);
		helper.assertValueEqual(instance.getAmplifier(), 0, effect.getRegisteredName() + "'s amplifier " + when);
	}

	/** Leaves the mock players sober, so the server's own tick has nothing to do to them after the test. */
	static void sober(ServerPlayer... players) {
		for (ServerPlayer player : players) {
			Drunkenness.set(player, 0);
			player.removeAllEffects();
		}
	}
}
