import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.TestHelper;
import nu.metacraft.void_anchor.AnchorBinding;
import nu.metacraft.void_anchor.VoidAnchor;
import nu.metacraft.void_anchor.VoidAnchorBlocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

public class VoidAnchorTests {

	// ---- helpers ----

	static BlockState anchor(int charges) {
		return VoidAnchorBlocks.VOID_ANCHOR.value().defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, charges);
	}

	static int charge(ServerLevel level, BlockPos pos) {
		var state = level.getBlockState(pos);
		return state.is(VoidAnchorBlocks.VOID_ANCHOR.value()) ? state.getValue(RespawnAnchorBlock.CHARGE) : -1;
	}

	static ServerLevel end(GameTestHelper ctx) {
		return ctx.getLevel().getServer().getLevel(Level.END);
	}

	/** An anchor on a 3×3 obsidian pad at (x, 64, 0) in the End, with air above; each test uses its own x. */
	static BlockPos endAnchor(GameTestHelper ctx, int x, int charges) {
		var end = end(ctx);
		var pos = new BlockPos(x, 64, 0);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				end.setBlockAndUpdate(pos.offset(dx, -1, dz), Blocks.OBSIDIAN.defaultBlockState());
				for (int dy = 0; dy <= 3; dy++) {
					end.setBlockAndUpdate(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
		end.setBlockAndUpdate(pos, anchor(charges));
		return pos;
	}

	static ServerPlayer survivalPlayer(GameTestHelper ctx) {
		var player = TestHelper.addMockPlayer(ctx);
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	static void moveTo(ServerPlayer player, ServerLevel level, Vec3 pos) {
		player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
	}

	static InteractionResult use(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack inHand) {
		player.setItemInHand(InteractionHand.MAIN_HAND, inHand);
		return player.gameMode.useItemOn(
				player, level, inHand, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
		);
	}

	static void register(String name, Consumer<GameTestHelper> test) {
		Registry.register(BuiltInRegistries.TEST_FUNCTION, VoidAnchor.getID("void_anchor/" + name), test);
	}

	// ---- tests ----

	private static boolean initialised = false;

	/** Both test classes share one JVM and registries freeze on first init, so every class goes through here. */
	@BeforeAll
	public static synchronized void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TestHelper.init(
				() -> {
					register("charge_to_four", ctx -> {
						var level = ctx.getLevel();
						var pos = ctx.absolutePos(new BlockPos(2, 1, 2));
						level.setBlockAndUpdate(pos, anchor(0));
						var player = survivalPlayer(ctx);
						var pearls = new ItemStack(Items.ENDER_PEARL, 6);
						for (int i = 0; i < 5; i++) {
							use(player, level, pos, pearls);
						}
						ctx.assertTrue(charge(level, pos) == 4, "charge is " + charge(level, pos) + ", expected 4");
						ctx.assertTrue(pearls.getCount() == 2, pearls.getCount() + " pearls left, expected 2");
						ctx.succeed();
					});
					register("wrong_item_does_not_charge", ctx -> {
						var level = ctx.getLevel();
						var pos = ctx.absolutePos(new BlockPos(2, 1, 2));
						level.setBlockAndUpdate(pos, anchor(0));
						var player = survivalPlayer(ctx);
						var glowstone = new ItemStack(Items.GLOWSTONE, 4);
						use(player, level, pos, glowstone);
						ctx.assertTrue(charge(level, pos) == 0, "glowstone charged the anchor to " + charge(level, pos));
						ctx.assertTrue(glowstone.getCount() == 4, "glowstone was used up");
						ctx.succeed();
					});
					register("no_explode_outside_end", ctx -> {
						var level = ctx.getLevel();
						var pos = ctx.absolutePos(new BlockPos(2, 1, 2));
						level.setBlockAndUpdate(pos, anchor(2));
						var player = survivalPlayer(ctx);
						use(player, level, pos, ItemStack.EMPTY);
						use(player, level, pos, new ItemStack(Items.GLOWSTONE, 4));
						ctx.assertTrue(charge(level, pos) == 2, "the anchor is gone or changed: charge " + charge(level, pos));
						ctx.assertTrue(player.getRespawnConfig() == null, "using the anchor set the player's spawn");
						ctx.assertTrue(AnchorBinding.get(player) == null, "the anchor bound outside the End");
						ctx.succeed();
					});
					register("bind_in_end", ctx -> {
						var end = end(ctx);
						var pos = endAnchor(ctx, 100, 0);
						var player = survivalPlayer(ctx);
						moveTo(player, end, Vec3.atBottomCenterOf(pos.east()));
						use(player, end, pos, ItemStack.EMPTY);
						ctx.assertTrue(
								GlobalPos.of(Level.END, pos).equals(AnchorBinding.get(player)),
								"bound to " + AnchorBinding.get(player)
						);
						ctx.succeed();
					});
					register("resolve_states", ctx -> {
						var end = end(ctx);
						var pos = endAnchor(ctx, 120, 0);
						var player = survivalPlayer(ctx);
						ctx.assertTrue(AnchorBinding.resolve(player) instanceof AnchorBinding.Unbound, "not Unbound");
						AnchorBinding.bind(player, GlobalPos.of(Level.END, pos));
						ctx.assertTrue(AnchorBinding.resolve(player) instanceof AnchorBinding.Empty, "not Empty");
						end.setBlockAndUpdate(pos, anchor(1));
						if (!(AnchorBinding.resolve(player) instanceof AnchorBinding.Ready ready)) {
							throw ctx.assertionException("not Ready: " + AnchorBinding.resolve(player));
						}
						ctx.assertTrue(ready.standUp().distanceTo(Vec3.atCenterOf(pos)) < 2.5, "stands up at " + ready.standUp());
						end.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
						ctx.assertTrue(AnchorBinding.resolve(player) instanceof AnchorBinding.Gone, "not Gone");
						for (int dx = -1; dx <= 1; dx++) {
							for (int dz = -1; dz <= 1; dz++) {
								for (int dy = -1; dy <= 3; dy++) {
									end.setBlockAndUpdate(pos.offset(dx, dy, dz), Blocks.OBSIDIAN.defaultBlockState());
								}
							}
						}
						end.setBlockAndUpdate(pos, anchor(1));
						ctx.assertTrue(AnchorBinding.resolve(player) instanceof AnchorBinding.Blocked, "not Blocked: " + AnchorBinding.resolve(player));
						ctx.succeed();
					});
					register("dispenser_refill", ctx -> dispenserTest(ctx, 0, 1, 1));
					register("dispenser_full_anchor", ctx -> dispenserTest(ctx, 4, 4, 2));
				},
				METAcraftLib::new, METAcraftCore::new, VoidAnchor::new
		);
	}

	/** A dispenser holding two pearls faces an anchor; a redstone block fires it once. */
	static void dispenserTest(GameTestHelper ctx, int charges, int expectedCharge, int expectedPearls) {
		var level = ctx.getLevel();
		var dispenserPos = ctx.absolutePos(new BlockPos(1, 1, 2));
		var anchorPos = dispenserPos.east();
		level.setBlockAndUpdate(dispenserPos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
		level.setBlockAndUpdate(anchorPos, anchor(charges));
		var dispenser = (DispenserBlockEntity) level.getBlockEntity(dispenserPos);
		dispenser.setItem(0, new ItemStack(Items.ENDER_PEARL, 2));
		ctx.runAtTickTime(2, () -> level.setBlockAndUpdate(dispenserPos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState()));
		ctx.runAtTickTime(12, () -> {
			ctx.assertTrue(charge(level, anchorPos) == expectedCharge, "charge " + charge(level, anchorPos) + ", expected " + expectedCharge);
			int pearls = dispenser.getItem(0).getCount();
			ctx.assertTrue(pearls == expectedPearls, pearls + " pearls left, expected " + expectedPearls);
			ctx.succeed();
		});
	}

	@Test
	public void voidAnchor() throws Exception {
		TestHelper.runTestServer("metacraft", "void_anchor/*");
	}

}
