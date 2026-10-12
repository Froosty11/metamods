package nu.metacraft.nether_rifts;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.rift.*;

import java.util.Optional;

/** {@code /rifts}: staff controls. Later tasks add subcommands to {@link #root()}. */
public final class RiftsCommand {

	public static final String PERMISSION = "metacraft.nether_rifts.admin";

	private RiftsCommand() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(root()));
	}

	static LiteralArgumentBuilder<CommandSourceStack> root() {
		return Commands.literal("rifts").requires(Permissions.require(PERMISSION, 2))
				.then(Commands.literal("stabilise").executes(ctx -> {
					Stabilisation.stabilise(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal("The realm steadies. Nether portals work again."), true);
					return 1;
				}))
				.then(Commands.literal("destabilise").executes(ctx -> {
					Stabilisation.destabilise(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal("The realm is unstable again. Portals open rifts."), true);
					return 1;
				}))
				.then(Commands.literal("reload").executes(ctx -> {
					NetherRiftsConfig.reload();
					ctx.getSource().sendSuccess(() -> Component.literal("Reloaded nether-rifts.json5"), true);
					return 1;
				}))
				.then(Commands.literal("stage").then(Commands.argument("pos", BlockPosArgument.blockPos())
						.then(Commands.literal("lit").executes(ctx -> stage(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"), Stage.LIT)))
						.then(Commands.literal("unstable").executes(ctx -> stage(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"), Stage.UNSTABLE)))
						.then(Commands.literal("torn").executes(ctx -> stage(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"), Stage.TORN)))))
				.then(Commands.literal("form").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
					var level = ctx.getSource().getLevel();
					var pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
					var shape = RiftShape.fill(level, pos, RiftShape.FrameTest.of(s -> !s.isAir() && !s.is(net.minecraft.tags.BlockTags.FIRE)));
					if (shape.isEmpty()) {
						ctx.getSource().sendFailure(Component.literal("No closed frame around " + pos.toShortString()));
						return 0;
					}
					RiftData.get(level).open(level, shape.get(), false);
					ctx.getSource().sendSuccess(() -> Component.literal("Opened a rift of " + shape.get().size() + " blocks"), true);
					return 1;
				})));
	}

	static Optional<Rift> riftNear(CommandSourceStack source, BlockPos pos) {
		var data = RiftData.get(source.getLevel());
		return data.at(pos).or(() -> data.all().stream()
				.filter(r -> r.centre().distanceToSqr(Vec3.atCenterOf(pos)) <= 16 * 16)
				.min(java.util.Comparator.comparingDouble(r -> r.centre().distanceToSqr(Vec3.atCenterOf(pos)))));
	}

	private static int stage(CommandSourceStack source, BlockPos pos, Stage stage) {
		var rift = riftNear(source, pos);
		if (rift.isEmpty()) {
			source.sendFailure(Component.literal("No rift within 16 blocks of " + pos.toShortString()));
			return 0;
		}
		if (stage == Stage.TORN) {
			rift.get().tear(source.getLevel());
		} else {
			rift.get().setStage(source.getLevel(), stage);
		}
		source.sendSuccess(() -> Component.literal("Rift is now " + stage.getSerializedName()), true);
		return 1;
	}
}
