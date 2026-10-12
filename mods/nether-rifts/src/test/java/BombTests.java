import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.mob.BurningPatches;
import nu.metacraft.nether_rifts.mob.MagmaBomb;

public class BombTests {

	static void registerTests() {
		NetherRiftsTests.register("bomb/hurts_and_burns", ctx -> {
			var player = StageTests.player(ctx);
			// A mock player counts as "client not loaded yet", which makes it immune to all damage.
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			var at = ctx.absolutePos(new BlockPos(12, 1, 12));
			player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			var bomb = new MagmaBomb(ctx.getLevel(), player);
			bomb.setOwner(null);
			bomb.setPos(at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5);
			bomb.setDeltaMovement(0, -0.3, 0);
			ctx.getLevel().addFreshEntity(bomb);
			float before = player.getHealth();
			ctx.runAtTickTime(25, () -> {
				ctx.assertTrue(player.getHealth() < before, "the bomb's blast didn't hurt the player under it");
				ctx.assertTrue(player.getRemainingFireTicks() > 0, "the burning patch didn't set the player on fire");
				StageTests.remove(ctx, player);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bomb/no_block_changes", ctx -> {
			// Review focus 4: TNT, glass, crops, planks around the impact all stay put; nothing primes or burns.
			ctx.setBlock(new BlockPos(12, 1, 12), Blocks.TNT);
			ctx.setBlock(new BlockPos(13, 1, 12), Blocks.GLASS);
			ctx.setBlock(new BlockPos(11, 1, 12), Blocks.FARMLAND);
			ctx.setBlock(new BlockPos(11, 2, 12), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
			ctx.setBlock(new BlockPos(12, 1, 13), Blocks.OAK_PLANKS);
			var at = ctx.absolutePos(new BlockPos(12, 4, 12));
			var bomb = new MagmaBomb(ctx.getLevel(), null);
			bomb.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			bomb.setDeltaMovement(0, -0.3, 0);
			ctx.getLevel().addFreshEntity(bomb);
			ctx.runAtTickTime(60, () -> {
				ctx.assertBlockPresent(Blocks.TNT, new BlockPos(12, 1, 12));
				ctx.assertBlockPresent(Blocks.GLASS, new BlockPos(13, 1, 12));
				ctx.assertBlockPresent(Blocks.WHEAT, new BlockPos(11, 2, 12));
				ctx.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(12, 1, 13));
				ctx.assertTrue(ctx.getEntities(EntityTypes.TNT).isEmpty(), "the bomb primed the TNT");
				for (var pos : BlockPos.betweenClosed(ctx.absolutePos(new BlockPos(9, 1, 9)), ctx.absolutePos(new BlockPos(15, 4, 15)))) {
					ctx.assertFalse(ctx.getLevel().getBlockState(pos).is(Blocks.FIRE), "fire at " + pos.toShortString());
				}
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bomb/warning_then_gone", ctx -> {
			var at = Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(12, 1, 12)));
			BurningPatches.warn(ctx.getLevel(), at);
			ctx.assertTrue(BurningPatches.active(ctx.getLevel()).stream().anyMatch(p -> p.warning() && p.at().equals(at)), "no warning recorded");
			ctx.runAtTickTime(BurningPatches.WARN_TICKS + 2, () -> {
				ctx.assertFalse(BurningPatches.active(ctx.getLevel()).stream().anyMatch(p -> p.at().equals(at)), "the warning outlived its 20 ticks");
				ctx.succeed();
			});
		});
	}
}
