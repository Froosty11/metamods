import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.mob.BombingRunGoal;
import nu.metacraft.nether_rifts.mob.BurningPatches;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.WarGhast;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.RiftRaid;
import nu.metacraft.nether_rifts.raid.Slot;

import java.util.concurrent.atomic.AtomicInteger;

public class BombardierTests {

	static void registerTests() {
		NetherRiftsTests.register("bombardier/rides_a_war_ghast", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.BOMBARDIER, ctx.absolutePos(new BlockPos(12, 8, 12)), Vec3.ZERO);
				var ghast = spawned.stream().filter(e -> e instanceof WarGhast).map(e -> (WarGhast) e).findFirst().orElseThrow(() -> new AssertionError("no war ghast in " + spawned));
				ctx.assertTrue(ghast.bombardier() != null, "the war ghast has no Bombardier on it");
				ctx.assertTrue(RiftMobs.isRiftMob(ghast.bombardier()), "the Bombardier isn't a rift mob");
				ctx.assertTrue(ghast.getControllingPassenger() == null, "the Bombardier steers the ghast");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/run_drops_bombs", ctx -> {
			stoneFloor(ctx);
			var bombs = new AtomicInteger();
			ctx.onEachTick(() -> bombs.addAndGet(ctx.getEntities(RiftEntities.MAGMA_BOMB).size()));
			FormingTests.lightFrame(ctx, 2, 3);
			var player = StageTests.player(ctx);
			ctx.runAtTickTime(3, () -> {
				var floor = ctx.absolutePos(new BlockPos(16, 1, 16));
				player.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.BOMBARDIER, ctx.absolutePos(new BlockPos(8, 10, 8)), Vec3.ZERO);
				var ghast = (WarGhast) spawned.stream().filter(e -> e instanceof WarGhast).findFirst().orElseThrow();
				ghast.setTarget(player);
			});
			ctx.succeedWhen(() -> {
				ctx.assertTrue(bombs.get() > 0, "no bomb dropped yet");
				// a dropped bomb is not yet a blast: wait for one to land, or the floor check below proves nothing
				ctx.assertTrue(BurningPatches.active(ctx.getLevel()).stream().anyMatch(p -> !p.warning() && ctx.getBounds().contains(p.at())), "no bomb landed yet");
				for (int x = 0; x < 24; x++) {
					for (int z = 0; z < 24; z++) {
						ctx.assertBlockPresent(Blocks.STONE, new BlockPos(x, 0, z));
					}
				}
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
			});
		});
		NetherRiftsTests.register("bombardier/rider_clear_of_low_roof", ctx -> {
			// A roof 10 blocks over the target: room for the ghast alone to run and climb, not for the rider on top of it.
			stoneFloor(ctx);
			for (int x = 0; x < 24; x++) {
				for (int z = 0; z < 24; z++) {
					for (int y = 11; y <= 13; y++) {
						ctx.setBlock(new BlockPos(x, y, z), Blocks.STONE);
					}
				}
			}
			var buried = new AtomicInteger();
			var bombs = new AtomicInteger();
			ctx.onEachTick(() -> {
				bombs.addAndGet(ctx.getEntities(RiftEntities.MAGMA_BOMB).size());
				ctx.getEntities(RiftEntities.GHAST_BOMBARDIER).forEach(rider -> {
					if (rider.isInWall()) {
						buried.incrementAndGet();
					}
				});
			});
			FormingTests.lightFrame(ctx, 2, 3);
			var player = StageTests.player(ctx);
			ctx.runAtTickTime(3, () -> {
				var floor = ctx.absolutePos(new BlockPos(16, 1, 16));
				player.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.BOMBARDIER, ctx.absolutePos(new BlockPos(8, 1, 8)), Vec3.ZERO);
				var ghast = (WarGhast) spawned.stream().filter(e -> e instanceof WarGhast).findFirst().orElseThrow();
				ghast.setTarget(player);
			});
			ctx.runAtTickTime(300, () -> {
				ctx.assertTrue(buried.get() == 0, "the Bombardier's head was in the roof for " + buried.get() + " ticks");
				// the stack fits at run height (4 above the target), so the run goes ahead, below the roof
				ctx.assertTrue(bombs.get() > 0, "no bombing run under the roof");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/rider_does_not_pathfind", ctx -> {
			// Riding, the Bombardier leaves the moving to its ghast: no walking goal makes paths for it.
			stoneFloor(ctx);
			var pathing = new AtomicInteger();
			ctx.onEachTick(() -> ctx.getEntities(RiftEntities.GHAST_BOMBARDIER).forEach(rider -> {
				if (rider.isPassenger() && !rider.getNavigation().isDone()) {
					pathing.incrementAndGet();
				}
			}));
			FormingTests.lightFrame(ctx, 2, 3);
			var player = StageTests.player(ctx);
			ctx.runAtTickTime(3, () -> {
				var floor = ctx.absolutePos(new BlockPos(20, 1, 20));
				player.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.BOMBARDIER, ctx.absolutePos(new BlockPos(10, 1, 10)), Vec3.ZERO);
				var ghast = (WarGhast) spawned.stream().filter(e -> e instanceof WarGhast).findFirst().orElseThrow();
				ghast.setNoAi(true);
				ghast.bombardier().setTarget(player);
			});
			ctx.runAtTickTime(60, () -> {
				ctx.assertTrue(pathing.get() == 0, "the riding Bombardier had a path for " + pathing.get() + " ticks");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/summoned_ghast_steers_itself", ctx -> {
			// A staff /summon: a war ghast and its Bombardier with no rift. The ghast still flies itself.
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 2, 12));
			var rider = ctx.spawn(RiftEntities.GHAST_BOMBARDIER, new BlockPos(12, 2, 12));
			rider.startRiding(ghast, true, false);
			ctx.assertTrue(ghast.bombardier() == rider, "the Bombardier didn't mount");
			ctx.assertFalse(RiftMobs.isRiftMob(ghast), "a summoned war ghast is a rift mob");
			ctx.assertTrue(ghast.getControllingPassenger() == null, "a summoned war ghast's Bombardier steers it");
			ctx.killAllEntities();
			ctx.succeed();
		});
		NetherRiftsTests.register("bombardier/rider_survives_ghast", ctx -> {
			// Review focus 2.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var spawned = RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.BOMBARDIER, ctx.absolutePos(new BlockPos(12, 5, 12)), Vec3.ZERO);
				var ghast = (WarGhast) spawned.stream().filter(e -> e instanceof WarGhast).findFirst().orElseThrow();
				var rider = ghast.bombardier();
				ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
				// the ghast's 20-tick death, then a slow-falling glide down
				ctx.runAfterDelay(100, () -> {
					ctx.assertTrue(rider.isAlive(), "the Bombardier died with its ghast");
					ctx.assertTrue(rider.getVehicle() == null, "the Bombardier still rides a dead ghast");
					ctx.assertTrue(rider.onGround(), "the Bombardier hangs in the air");
					ctx.assertTrue(RiftRaid.aliveMobs(ctx.getLevel(), rift).contains(rider), "the dismounted Bombardier no longer counts toward the wave");
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("bombardier/warning_leads_impact", ctx -> {
			// The ground warning shows a full second before the blast, where the bomb lands, even from a moving ghast.
			stoneFloor(ctx);
			long[] warnedAt = {-1};
			long[] burntAt = {-1};
			Vec3[] warning = new Vec3[1];
			Vec3[] burn = new Vec3[1];
			ctx.onEachTick(() -> {
				for (var patch : BurningPatches.active(ctx.getLevel())) {
					if (!ctx.getBounds().contains(patch.at())) {
						continue;
					}
					if (patch.warning() && warning[0] == null) {
						warning[0] = patch.at();
					} else if (!patch.warning() && burn[0] == null) {
						burn[0] = patch.at();
						burntAt[0] = ctx.getTick();
					}
				}
			});
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				// run height: DIVE_HEIGHT above the floor
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.BOMBARDIER,
						ctx.absolutePos(new BlockPos(12, 1 + (int) BombingRunGoal.DIVE_HEIGHT, 12)), Vec3.ZERO);
				var ghast = (WarGhast) spawned.stream().filter(e -> e instanceof WarGhast).findFirst().orElseThrow();
				ghast.setNoAi(true);
				ghast.setDeltaMovement(0.4, 0, 0);
			});
			ctx.runAtTickTime(5, () -> {
				var ghast = ctx.getEntities(RiftEntities.WAR_GHAST).getFirst();
				// the warning goes up with the drop; per-tick checks would see it a tick late (they run before this)
				ghast.bombardier().dropBomb(ctx.getLevel());
				warnedAt[0] = ctx.getTick();
			});
			ctx.succeedWhen(() -> {
				ctx.assertTrue(warning[0] != null, "no ground warning");
				ctx.assertTrue(burn[0] != null, "the bomb hasn't landed");
				ctx.assertTrue(burntAt[0] - warnedAt[0] >= BurningPatches.WARN_TICKS, "the warning led the blast by only " + (burntAt[0] - warnedAt[0]) + " ticks");
				double off = Math.hypot(burn[0].x - warning[0].x, burn[0].z - warning[0].z);
				ctx.assertTrue(off <= BurningPatches.RADIUS, "the bomb landed " + off + " blocks from its warning");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
			});
		});
		NetherRiftsTests.register("bombardier/rider_lands_softly", ctx -> {
			// Unhorsed high up, the Bombardier glides down and fights on: a fall that would kill it doesn't.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.BOMBARDIER, ctx.absolutePos(new BlockPos(12, 10, 12)), Vec3.ZERO);
				var ghast = (WarGhast) spawned.stream().filter(e -> e instanceof WarGhast).findFirst().orElseThrow();
				var rider = ghast.bombardier();
				// about 14 blocks up: 11 fall damage, so 6 health dies of an unbroken fall
				rider.setHealth(6f);
				ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
				ctx.runAfterDelay(120, () -> {
					ctx.assertTrue(rider.isAlive(), "the Bombardier died of the fall from its ghast");
					ctx.assertTrue(rider.onGround(), "the Bombardier hangs in the air");
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("bombardier/joins_late_large_waves", ctx -> {
			// A 10x10 frame is Large; wave 3 heavies are plain ghasts, wave 5 heavies are war ghasts.
			var inside = ShapeTests.frame(ctx, 10, 10, Blocks.OBSIDIAN);
			ctx.setBlock(inside, Blocks.FIRE);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				rift.tear(ctx.getLevel());
				RiftRaid.startWave(ctx.getLevel(), rift, 3);
				var wave3 = RiftRaid.aliveMobs(ctx.getLevel(), rift);
				ctx.assertTrue(wave3.stream().anyMatch(e -> e.getType() == EntityTypes.GHAST), "no plain ghast in Large wave 3");
				ctx.assertFalse(wave3.stream().anyMatch(e -> e instanceof WarGhast), "a war ghast came in wave 3");
				wave3.forEach(e -> e.discard());
				RiftRaid.startWave(ctx.getLevel(), rift, 5);
				var wave5 = RiftRaid.aliveMobs(ctx.getLevel(), rift);
				ctx.assertTrue(wave5.stream().anyMatch(e -> e instanceof WarGhast w && w.bombardier() != null), "no Bombardier in Large wave 5");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
	}

	/** The test room comes with no floor (the structure's stone row isn't placed; the encasing barrier is the ground), so lay one. */
	static void stoneFloor(net.minecraft.gametest.framework.GameTestHelper ctx) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				ctx.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}
}
