package nu.metacraft.void_anchor;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.void_anchor.rift.Rift;

/** {@code /voidanchor rift [pos]}: opens a rift that only looks, for testing how it renders. */
public final class VoidAnchorCommand {

	private static final int DEBUG_RIFT_TICKS = 60;

	private VoidAnchorCommand() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("voidanchor").requires(Permissions.require("metacraft.voidanchor", 2)).then(
						Commands.literal("rift")
								.executes(ctx -> openRift(ctx.getSource(), inFrontOf(ctx.getSource().getPlayerOrException())))
								.then(Commands.argument("pos", Vec3Argument.vec3()).executes(
										ctx -> openRift(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"))
								))
				)
		));
	}

	private static Vec3 inFrontOf(ServerPlayer player) {
		var look = player.getLookAngle();
		return player.position().add(look.x * 3, -1.5, look.z * 3);
	}

	private static int openRift(CommandSourceStack source, Vec3 pos) {
		Rift.open(source.getLevel(), pos, VoidAnchorConfig.getInstance().riftSize()).closeAfter(DEBUG_RIFT_TICKS);
		source.sendSuccess(() -> Component.literal(
				"Opened a rift at %.1f %.1f %.1f".formatted(pos.x, pos.y, pos.z)
		), true);
		return 1;
	}

}
