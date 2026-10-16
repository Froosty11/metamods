import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Soul sand and nether wart in rift mode ({@code nether_rifts.SoulLock}): no soul sand from breaking it, no wart planted. */
public class SoulLockTests {

	static void registerTests() {
		NetherRiftsTests.register("soul/locked", ctx -> {
			ctx.assertFalse(breakSoulSand(ctx), "soul sand dropped in rift mode");
			ctx.assertFalse(wartFits(ctx), "nether wart can be planted in rift mode");
			ctx.succeed();
		});
		NetherRiftsTests.register("soul/unlocked", ctx -> {
			ctx.assertTrue(breakSoulSand(ctx), "soul sand dropped nothing after stabilising");
			ctx.assertTrue(wartFits(ctx), "nether wart can't be planted after stabilising");
			ctx.succeed();
		});
	}

	/** Breaks a soul sand block as a player's bare hand would; whether a soul sand item came of it. */
	private static boolean breakSoulSand(GameTestHelper ctx) {
		var pos = new BlockPos(4, 1, 4);
		ctx.setBlock(pos, Blocks.SOUL_SAND);
		ctx.getLevel().destroyBlock(ctx.absolutePos(pos), true);
		boolean dropped = ctx.getEntities(EntityTypes.ITEM).stream().anyMatch(item -> item.getItem().is(Items.SOUL_SAND));
		ctx.killAllEntities();
		return dropped;
	}

	/** Whether nether wart could stand on soul sand here. */
	private static boolean wartFits(GameTestHelper ctx) {
		var pos = new BlockPos(8, 1, 4);
		ctx.setBlock(pos, Blocks.SOUL_SAND);
		return Blocks.NETHER_WART.defaultBlockState().canSurvive(ctx.getLevel(), ctx.absolutePos(pos.above()));
	}
}
