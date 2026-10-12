import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.mob.WarDrummer;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.Slot;

public class DrummerTests {

	static void registerTests() {
		NetherRiftsTests.register("drummer/leads_waves", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO);
				ctx.assertTrue(spawned.size() == 1 && spawned.getFirst() instanceof WarDrummer, "the leader slot spawned " + spawned);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/buffs_only_rift_piglins", ctx -> {
			// Review focus 3.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var drummer = (WarDrummer) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				var near = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(15, 1, 12)), Vec3.ZERO).getFirst();
				var far = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(12, 1, 12)).offset(0, 0, 0), Vec3.ZERO).getFirst();
				far.teleportTo(drummer.getX() + 13.5, drummer.getY(), drummer.getZ());
				var vanilla = ctx.spawn(EntityTypes.PIGLIN, new BlockPos(12, 1, 15));
				drummer.bigBeat(ctx.getLevel());
				ctx.assertTrue(near.hasEffect(MobEffects.SPEED) && near.hasEffect(MobEffects.STRENGTH), "a rift piglin 3 blocks away wasn't buffed");
				ctx.assertFalse(far.hasEffect(MobEffects.SPEED), "a rift piglin 13.5 blocks away was buffed");
				ctx.assertFalse(vanilla.hasEffect(MobEffects.SPEED), "a vanilla piglin was buffed");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/buff_range_from_config", ctx -> {
			// The drummer_short preset sets the War Beat's reach to 6 blocks.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var drummer = (WarDrummer) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				var near = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(15, 1, 12)), Vec3.ZERO).getFirst();
				var far = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				far.teleportTo(drummer.getX() + 8, drummer.getY(), drummer.getZ());
				drummer.bigBeat(ctx.getLevel());
				ctx.assertTrue(near.hasEffect(MobEffects.SPEED), "a rift piglin 3 blocks away wasn't buffed");
				ctx.assertFalse(far.hasEffect(MobEffects.SPEED), "a rift piglin 8 blocks away was buffed with a buff range of 6");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/shock_beat_pushes_players", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var drummer = (WarDrummer) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				var close = StageTests.player(ctx);
				close.teleportTo(drummer.getX() + 2, drummer.getY(), drummer.getZ());
				var distant = StageTests.player(ctx);
				distant.teleportTo(drummer.getX() - 6, drummer.getY(), drummer.getZ());
				close.setDeltaMovement(Vec3.ZERO);
				distant.setDeltaMovement(Vec3.ZERO);
				drummer.bigBeat(ctx.getLevel());
				ctx.assertTrue(close.getDeltaMovement().x > 0.3, "a player 2 blocks east wasn't pushed east: " + close.getDeltaMovement());
				ctx.assertTrue(distant.getDeltaMovement().horizontalDistance() < 0.01, "a player 6 blocks away was pushed: " + distant.getDeltaMovement());
				StageTests.remove(ctx, close);
				StageTests.remove(ctx, distant);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/shock_beat_spares_staff", ctx -> {
			// Review focus 5.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var drummer = (WarDrummer) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				var creative = StageTests.player(ctx);
				creative.setGameMode(GameType.CREATIVE);
				creative.teleportTo(drummer.getX() + 2, drummer.getY(), drummer.getZ());
				var spectator = StageTests.player(ctx);
				spectator.setGameMode(GameType.SPECTATOR);
				spectator.teleportTo(drummer.getX() - 2, drummer.getY(), drummer.getZ());
				creative.setDeltaMovement(Vec3.ZERO);
				spectator.setDeltaMovement(Vec3.ZERO);
				drummer.bigBeat(ctx.getLevel());
				ctx.assertTrue(creative.getDeltaMovement().horizontalDistance() < 0.01, "a creative player was pushed");
				ctx.assertTrue(spectator.getDeltaMovement().horizontalDistance() < 0.01, "a spectator was pushed");
				StageTests.remove(ctx, creative);
				StageTests.remove(ctx, spectator);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/drums_on_its_own", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO);
				RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(14, 1, 12)), Vec3.ZERO);
			});
			// 4 beats of 10 ticks: the big beat lands by tick ~45
			ctx.runAtTickTime(60, () -> {
				var piglin = ctx.getEntities(EntityTypes.PIGLIN).stream().filter(RiftMobs::isRiftMob).findFirst().orElseThrow();
				ctx.assertTrue(piglin.hasEffect(MobEffects.SPEED), "no big beat within 60 ticks");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/missing_slot_uses_default", ctx -> {
			// Review focus 1: the roster_partial preset has no HEAVY entry.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.HEAVY, ctx.absolutePos(new BlockPos(12, 6, 12)), Vec3.ZERO);
				ctx.assertTrue(spawned.stream().anyMatch(e -> e.getType() == EntityTypes.GHAST), "a missing HEAVY entry spawned " + spawned);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
	}
}
