import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.RiftRaid;
import nu.metacraft.nether_rifts.raid.Slot;
import nu.metacraft.nether_rifts.rift.Rift;
import nu.metacraft.nether_rifts.rift.RiftData;
import nu.metacraft.nether_rifts.rift.Stage;

import java.util.List;

public class RaidTests {

	static void registerTests() {
		NetherRiftsTests.register("raid/first_wave_spawns", ctx -> torn(ctx, (rift, player) -> ctx.runAfterDelay(25, () -> {
			ctx.assertTrue(rift.stage() == Stage.RAID && rift.wave() == 1, "stage " + rift.stage() + " wave " + rift.wave());
			var alive = RiftRaid.aliveMobs(ctx.getLevel(), rift);
			// small wave 1: 4 crossbow piglins +0..1, and 0..1 melee picks (a hoglin may carry a baby piglin)
			long ranged = alive.stream().filter(e -> e instanceof Piglin p && !p.isBaby() && p.getMainHandItem().is(Items.CROSSBOW)).count();
			ctx.assertTrue(ranged >= 4 && ranged <= 5, ranged + " crossbow piglins in wave 1");
			ctx.assertTrue(alive.size() - ranged <= 2, (alive.size() - ranged) + " other mobs in wave 1");
			ctx.assertTrue(alive.stream().allMatch(e -> rift.id().equals(e.getAttached(RiftMobs.RIFT))), "untagged mob in the wave");
			ctx.assertTrue(rift.bossBar() != null && rift.bossBar().getName().getString().contains("Wave 1/3"), "boss bar: " + (rift.bossBar() == null ? "none" : rift.bossBar().getName().getString()));
			ctx.assertTrue(rift.bossBar().getPlayers().contains(player), "the participant can't see the boss bar");
			finish(ctx, player);
		})));
		NetherRiftsTests.register("raid/next_wave_when_cleared", ctx -> torn(ctx, (rift, player) -> ctx.runAfterDelay(25, () -> {
			killAll(ctx, rift, player);
			ctx.runAfterDelay(3, () -> {
				ctx.assertTrue(rift.wave() == 2, "wave " + rift.wave() + " after clearing wave 1");
				finish(ctx, player);
			});
		})));
		NetherRiftsTests.register("raid/victory_converts_frame", ctx -> {
			var inside = FormingTests.lightFrame(ctx, 2, 3);
			var torn = new Rift[1];
			var participant = new ServerPlayer[1];
			// Up front: 26.3's onEachTick schedules one runnable per remaining tick, and adding that many from
			// inside a scheduled runnable rehashes the schedule mid-iteration and crashes the test server.
			ctx.onEachTick(() -> {
				if (torn[0] != null && torn[0].stage() == Stage.RAID) {
					killAll(ctx, torn[0], participant[0]);
				}
			});
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var player = participant(ctx);
				participant[0] = player;
				torn[0] = rift;
				rift.tear(ctx.getLevel());
				ctx.succeedWhen(() -> {
					ctx.assertTrue(FormingTests.riftsIn(ctx).isEmpty(), "the rift is still open");
					ctx.assertBlockPresent(Blocks.CRYING_OBSIDIAN, inside.below());
					ctx.assertBlockPresent(Blocks.CRYING_OBSIDIAN, inside.offset(-1, 0, 0));
					FormingTests.assertInside(ctx, inside, 2, 3, Blocks.AIR);
					ctx.assertItemEntityPresent(Items.GOLD_INGOT);
					StageTests.remove(ctx, player);
					ctx.killAllEntities();
				});
			});
		});
		NetherRiftsTests.register("raid/abandon_collapses", ctx -> torn(ctx, (rift, player) -> ctx.runAfterDelay(25, () -> {
			StageTests.remove(ctx, player);
			// fast: abandon_after = 60
			ctx.runAfterDelay(70, () -> {
				ctx.assertTrue(FormingTests.riftsIn(ctx).isEmpty(), "an abandoned raid is still open");
				ctx.assertTrue(RiftRaid.aliveMobs(ctx.getLevel(), rift).isEmpty(), "an abandoned raid's mobs stayed");
				ctx.assertTrue(ctx.getEntities(net.minecraft.world.entity.EntityTypes.ITEM).isEmpty(), "an abandoned raid paid out");
				ctx.succeed();
			});
		})));
		NetherRiftsTests.register("raid/glow_after_delay", ctx -> torn(ctx, (rift, player) ->
				// countdown 20, then glow_after = 100
				ctx.runAfterDelay(130, () -> {
					var alive = RiftRaid.aliveMobs(ctx.getLevel(), rift);
					ctx.assertFalse(alive.isEmpty(), "no mobs left to glow");
					ctx.assertTrue(alive.stream().allMatch(e -> e.hasEffect(MobEffects.GLOWING)), "stragglers aren't glowing");
					finish(ctx, player);
				})));
		NetherRiftsTests.register("raid/broken_frame_collapses", ctx -> {
			// Review focus 2 (raiding half).
			var inside = FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var player = participant(ctx);
				rift.tear(ctx.getLevel());
				ctx.runAfterDelay(25, () -> {
					// A player's block in the broken frame: the collapse converts the frame around it, not it.
					ctx.setBlock(inside.below(), Blocks.CHEST);
					ctx.runAfterDelay(25, () -> {
						ctx.assertTrue(FormingTests.riftsIn(ctx).isEmpty(), "the raid outlived its frame");
						ctx.assertTrue(RiftRaid.aliveMobs(ctx.getLevel(), rift).isEmpty(), "mobs stayed after the collapse");
						ctx.assertBlockPresent(Blocks.CHEST, inside.below());
						ctx.assertBlockPresent(Blocks.CRYING_OBSIDIAN, inside.offset(1, -1, 0));
						ctx.assertBlockPresent(Blocks.CRYING_OBSIDIAN, inside.offset(-1, 0, 0));
						ctx.assertBlockPresent(Blocks.CRYING_OBSIDIAN, inside.offset(0, 3, 0));
						FormingTests.assertInside(ctx, inside, 2, 3, Blocks.AIR);
						StageTests.remove(ctx, player);
						ctx.succeed();
					});
				});
			});
		});
		NetherRiftsTests.register("raid/tether_pulls_back", ctx -> torn(ctx, (rift, player) -> ctx.runAfterDelay(25, () -> {
			// A mob on its own (riders follow their mount), sent to the room's far corner: 26 blocks out, but
			// still in this room. Anywhere 30 blocks out is in a neighbouring test's room, whose clean-up discards it.
			var mob = RiftRaid.aliveMobs(ctx.getLevel(), rift).stream().filter(e -> !e.isPassenger() && !e.isVehicle()).findFirst().orElseThrow();
			var far = ctx.absolutePos(new BlockPos(22, 1, 22));
			mob.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
			ctx.assertTrue(mob.position().distanceTo(rift.centre()) > 24, "the stray only went " + mob.position().distanceTo(rift.centre()) + " blocks out");
			// A ghast (4×4×4) strays too: pulled back where it fits, not into the frame.
			var ghast = (LivingEntity) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.HEAVY, ctx.absolutePos(new BlockPos(20, 2, 20)), Vec3.ZERO).getFirst();
			ctx.assertTrue(ghast.position().distanceTo(rift.centre()) > 20, "the ghast only went " + ghast.position().distanceTo(rift.centre()) + " blocks out");
			ctx.runAfterDelay(25, () -> {
				// fast: tether = 16
				ctx.assertTrue(mob.position().distanceTo(rift.centre()) <= 16, "a stray is " + mob.position().distanceTo(rift.centre()) + " blocks out");
				ctx.assertTrue(ghast.position().distanceTo(rift.centre()) <= 16, "the ghast is " + ghast.position().distanceTo(rift.centre()) + " blocks out");
				ctx.assertFalse(ghast.isInWall(), "the ghast was pulled back into a wall");
				ctx.assertTrue(ctx.getLevel().noCollision(ghast), "the ghast was pulled back overlapping blocks");
				finish(ctx, player);
			});
		})));
		NetherRiftsTests.register("raid/state_round_trips", ctx -> torn(ctx, (rift, player) -> ctx.runAfterDelay(25, () -> {
			// Review focus 3: what a restart reads back is what was saved.
			var data = RiftData.get(ctx.getLevel());
			var json = RiftData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
			var back = RiftData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().byId(rift.id()).orElseThrow();
			ctx.assertTrue(back.stage() == Stage.RAID && back.wave() == 1, "read back " + back.stage() + " wave " + back.wave());
			ctx.assertTrue(back.shape().equals(rift.shape()), "the shape changed in the round trip");
			ctx.assertTrue(back.tier() == rift.tier(), "the tier changed in the round trip");
			finish(ctx, player);
		})));
		NetherRiftsTests.register("raid/unloaded_does_not_advance", ctx -> torn(ctx, (rift, player) -> ctx.runAfterDelay(25, () -> {
			// Review focus 4: with no mobs found because the area isn't entity-loaded, the wave holds.
			RiftRaid.advanceIfCleared(ctx.getLevel(), rift, RiftData.get(ctx.getLevel()), List.of(), () -> false);
			ctx.assertTrue(rift.wave() == 1, "the wave moved on while the area was unloaded: " + rift.wave());
			finish(ctx, player);
		})));
		NetherRiftsTests.register("raid/unloaded_advances_after_grace", ctx -> torn(ctx, (rift, player) -> ctx.runAfterDelay(25, () -> {
			// A cleared wave whose surroundings never load still moves on once the grace runs out: no stalled raid.
			var data = RiftData.get(ctx.getLevel());
			for (int i = 1; i < RiftRaid.UNLOADED_GRACE; i++) {
				RiftRaid.advanceIfCleared(ctx.getLevel(), rift, data, List.of(), () -> false);
			}
			ctx.assertTrue(rift.wave() == 1, "the wave moved on before the grace ran out: " + rift.wave());
			RiftRaid.advanceIfCleared(ctx.getLevel(), rift, data, List.of(), () -> false);
			ctx.assertTrue(rift.wave() == 2, "the wave held past the grace: " + rift.wave());
			finish(ctx, player);
		})));
	}

	interface WithRift {
		void run(Rift rift, ServerPlayer player);
	}

	/** A small rift, a participant two blocks in front of it, torn open; then the body. */
	static void torn(GameTestHelper ctx, WithRift body) {
		FormingTests.lightFrame(ctx, 2, 3);
		ctx.runAtTickTime(3, () -> {
			var rift = FormingTests.riftIn(ctx);
			var player = participant(ctx);
			rift.tear(ctx.getLevel());
			body.run(rift, player);
		});
	}

	static ServerPlayer participant(GameTestHelper ctx) {
		var player = StageTests.player(ctx);
		player.setPermanentlyInvulnerable(true);
		var spot = ctx.absolutePos(new BlockPos(2, 1, 8));
		player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		return player;
	}

	static void killAll(GameTestHelper ctx, Rift rift, ServerPlayer player) {
		for (LivingEntity mob : RiftRaid.aliveMobs(ctx.getLevel(), rift)) {
			mob.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().playerAttack(player), 1000f);
		}
	}

	static void finish(GameTestHelper ctx, ServerPlayer player) {
		StageTests.remove(ctx, player);
		ctx.killAllEntities();
		FormingTests.cleanUp(ctx);
		ctx.succeed();
	}
}
