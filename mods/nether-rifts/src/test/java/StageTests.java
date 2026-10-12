import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.lib.util.helper.TestHelper;
import nu.metacraft.nether_rifts.rift.Stage;

public class StageTests {

	static void registerTests() {
		NetherRiftsTests.register("stages/light_twice_tears", ctx -> {
			var inside = FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var player = player(ctx);
				var flint = new ItemStack(Items.FLINT_AND_STEEL);
				use(ctx, player, inside.below(), flint);
				ctx.assertTrue(FormingTests.riftIn(ctx).stage() == Stage.UNSTABLE, "after one light: " + FormingTests.riftIn(ctx).stage());
				use(ctx, player, inside.below(), player.getMainHandItem());
				ctx.assertTrue(FormingTests.riftIn(ctx).stage() == Stage.TORN, "after two lights: " + FormingTests.riftIn(ctx).stage());
				ctx.assertTrue(player.getMainHandItem().getDamageValue() == 2, "flint and steel took " + player.getMainHandItem().getDamageValue() + " damage");
				remove(ctx, player);
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("stages/fire_charge_lights", ctx -> {
			var inside = FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var player = player(ctx);
				use(ctx, player, inside.below(), new ItemStack(Items.FIRE_CHARGE, 3));
				ctx.assertTrue(FormingTests.riftIn(ctx).stage() == Stage.UNSTABLE, "fire charge didn't light it");
				ctx.assertTrue(player.getMainHandItem().getCount() == 2, "fire charges left: " + player.getMainHandItem().getCount());
				remove(ctx, player);
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("stages/unstable_falls_back", ctx -> {
			var inside = FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var player = player(ctx);
				use(ctx, player, inside.below(), new ItemStack(Items.FLINT_AND_STEEL));
				remove(ctx, player);
			});
			// fast timers: unstable_fallback = 40
			ctx.runAtTickTime(50, () -> {
				ctx.assertTrue(FormingTests.riftIn(ctx).stage() == Stage.LIT, "still " + FormingTests.riftIn(ctx).stage());
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("stages/countdown_to_raid", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> FormingTests.riftIn(ctx).tear(ctx.getLevel()));
			// fast timers: countdown = 20
			ctx.runAtTickTime(30, () -> {
				ctx.assertTrue(FormingTests.riftIn(ctx).stage() == Stage.RAID, "still " + FormingTests.riftIn(ctx).stage());
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("stages/cap_refuses", ctx -> {
			// cap_one: one raid at a time. Two frames side by side, inside at x 1..2 and 7..8.
			var first = FormingTests.lightFrame(ctx, 2, 3);
			var second = first.offset(6, 0, 0);
			for (int x = 6; x <= 9; x++) {
				for (int y = 1; y <= 5; y++) {
					boolean edge = x == 6 || x == 9 || y == 1 || y == 5;
					ctx.setBlock(new BlockPos(x, y, 5), edge ? net.minecraft.world.level.block.Blocks.OBSIDIAN : net.minecraft.world.level.block.Blocks.AIR);
				}
			}
			ctx.setBlock(second, net.minecraft.world.level.block.Blocks.FIRE);
			ctx.runAtTickTime(3, () -> {
				var rifts = FormingTests.riftsIn(ctx);
				ctx.assertTrue(rifts.size() == 2, rifts.size() + " rifts");
				var player = player(ctx);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
				use(ctx, player, first.below(), player.getMainHandItem());
				use(ctx, player, first.below(), player.getMainHandItem());
				use(ctx, player, second.below(), player.getMainHandItem());
				use(ctx, player, second.below(), player.getMainHandItem());
				ctx.assertTrue(stageAt(ctx, first) == Stage.TORN, "first rift: " + stageAt(ctx, first));
				ctx.assertTrue(stageAt(ctx, second) == Stage.UNSTABLE, "second rift tore past the cap: " + stageAt(ctx, second));
				remove(ctx, player);
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
	}

	static Stage stageAt(GameTestHelper ctx, BlockPos relative) {
		return nu.metacraft.nether_rifts.rift.RiftData.get(ctx.getLevel()).at(ctx.absolutePos(relative)).orElseThrow().stage();
	}

	static ServerPlayer player(GameTestHelper ctx) {
		var player = TestHelper.addMockPlayer(ctx);
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	static void remove(GameTestHelper ctx, ServerPlayer player) {
		ctx.getLevel().getServer().getPlayerList().remove(player);
	}

	/** Uses {@code stack} on the block at {@code relative} (a frame block), clicking its top face. */
	static void use(GameTestHelper ctx, ServerPlayer player, BlockPos relative, ItemStack stack) {
		var pos = ctx.absolutePos(relative);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.gameMode.useItemOn(player, ctx.getLevel(), stack, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
	}
}
