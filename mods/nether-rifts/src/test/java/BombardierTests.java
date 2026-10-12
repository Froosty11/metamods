import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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
			// the test room comes with no floor (the structure's stone row isn't placed; the encasing barrier is the ground), so lay one
			for (int x = 0; x < 24; x++) {
				for (int z = 0; z < 24; z++) {
					ctx.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				}
			}
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
		NetherRiftsTests.register("bombardier/rider_survives_ghast", ctx -> {
			// Review focus 2.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var spawned = RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.BOMBARDIER, ctx.absolutePos(new BlockPos(12, 5, 12)), Vec3.ZERO);
				var ghast = (WarGhast) spawned.stream().filter(e -> e instanceof WarGhast).findFirst().orElseThrow();
				var rider = ghast.bombardier();
				ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
				ctx.runAfterDelay(60, () -> {
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
}
