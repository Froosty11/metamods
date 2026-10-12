import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.Slot;

import java.util.UUID;

public class MobTests {

	static void registerTests() {
		NetherRiftsTests.register("mobs/rules_applied", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var at = ctx.absolutePos(new BlockPos(12, 1, 12));
				var piglin = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, at, Vec3.ZERO).getFirst();
				ctx.assertTrue(RiftMobs.riftOf(piglin).orElseThrow().equals(rift.id()), "not tagged with its rift");
				ctx.assertTrue(piglin.isPersistenceRequired(), "not persistent");
				ctx.assertFalse(piglin.canPickUpLoot(), "can pick up loot");
				ctx.assertTrue(piglin.getDropChances().byEquipment(EquipmentSlot.MAINHAND) == 0f, "can drop its crossbow");
			});
			// zombification happens after 300 ticks in the overworld
			ctx.runAtTickTime(330, () -> {
				ctx.assertTrue(ctx.getEntities(EntityTypes.ZOMBIFIED_PIGLIN).isEmpty(), "the rift piglin zombified");
				ctx.assertTrue(ctx.getEntities(EntityTypes.PIGLIN).size() == 1, "the rift piglin is gone");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/ignore_gold_armour", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var player = StageTests.player(ctx);
				var spot = ctx.absolutePos(new BlockPos(14, 1, 12));
				player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
				var rift = FormingTests.riftIn(ctx);
				var riftPiglin = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(10, 1, 12)), Vec3.ZERO).getFirst();
				var plainPiglin = ctx.spawn(EntityTypes.PIGLIN, new BlockPos(10, 1, 14));
				ctx.runAfterDelay(60, () -> {
					ctx.assertTrue(riftPiglin.getBrain().getMemory(MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD).isPresent(),
							"the rift piglin respects the gold truce");
					ctx.assertTrue(plainPiglin.getBrain().getMemory(MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD).isEmpty(),
							"a plain piglin ignores gold too: the mixin is too broad");
					StageTests.remove(ctx, player);
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("mobs/no_pickup", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var piglin = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				ctx.spawnItem(Items.GOLD_INGOT, new BlockPos(12, 1, 12));
				ctx.runAfterDelay(60, () -> {
					ctx.assertItemEntityPresent(Items.GOLD_INGOT);
					ctx.assertTrue(piglin.getOffhandItem().isEmpty(), "the rift piglin took the gold");
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("mobs/fireball_no_fire", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			for (int x = 8; x < 16; x++) {
				for (int z = 8; z < 16; z++) {
					ctx.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
				}
			}
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var ghast = (Ghast) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.HEAVY, ctx.absolutePos(new BlockPos(12, 8, 12)), Vec3.ZERO).getFirst();
				var fireball = new LargeFireball(ctx.getLevel(), ghast, new Vec3(0, -1, 0), 1);
				var from = ctx.absolutePos(new BlockPos(12, 4, 12));
				fireball.setPos(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
				ctx.getLevel().addFreshEntity(fireball);
			});
			ctx.runAtTickTime(40, () -> {
				for (var pos : BlockPos.betweenClosed(ctx.absolutePos(new BlockPos(6, 1, 6)), ctx.absolutePos(new BlockPos(18, 5, 18)))) {
					ctx.assertFalse(ctx.getLevel().getBlockState(pos).is(Blocks.FIRE), "fire at " + pos.toShortString());
				}
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/ghast_steers_itself", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var spawned = RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.HEAVY, ctx.absolutePos(new BlockPos(12, 6, 12)), Vec3.ZERO);
				var ghast = (Ghast) spawned.stream().filter(e -> e instanceof Ghast).findFirst().orElseThrow();
				ctx.assertTrue(ghast.getFirstPassenger() instanceof Piglin, "the rift ghast has no piglin rider");
				ctx.assertTrue(ghast.getControllingPassenger() == null, "the rider steers the rift ghast");
				var plain = ctx.spawn(EntityTypes.GHAST, new BlockPos(4, 6, 18));
				var rider = ctx.spawn(EntityTypes.PIGLIN, new BlockPos(4, 6, 18));
				rider.startRiding(plain, true, false);
				ctx.assertTrue(plain.getControllingPassenger() == rider, "vanilla changed: a plain ghast's mob rider doesn't steer it");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/orphan_discarded", ctx -> {
			// Review focus 3: a rift mob whose rift no longer exists goes away when it (re)loads.
			var piglin = EntityTypes.PIGLIN.create(ctx.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
			piglin.setAttached(RiftMobs.RIFT, UUID.randomUUID());
			var at = ctx.absolutePos(new BlockPos(12, 1, 12));
			piglin.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			ctx.getLevel().addFreshEntity(piglin);
			ctx.runAtTickTime(3, () -> {
				ctx.assertTrue(piglin.isRemoved(), "a mob of a missing rift stayed");
				ctx.succeed();
			});
		});
	}
}
