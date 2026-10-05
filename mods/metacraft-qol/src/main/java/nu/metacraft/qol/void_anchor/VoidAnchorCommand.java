package nu.metacraft.qol.void_anchor;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.qol.void_anchor.rift.Rift;
import nu.metacraft.qol.void_anchor.rift.RiftStyle;

/**
 * {@code /voidanchor rift [crack|shatter] [pos]}: opens a rift that only looks, for testing how it
 * renders; in the configured style unless one is named.
 */
public final class VoidAnchorCommand {

	private static final int DEBUG_RIFT_TICKS = 60;

	private VoidAnchorCommand() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			var rift = Commands.literal("rift")
					.executes(ctx -> openRift(ctx.getSource(), inFrontOf(ctx.getSource().getPlayerOrException()), null))
					.then(Commands.argument("pos", Vec3Argument.vec3()).executes(
							ctx -> openRift(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"), null)
					));
			for (var style : RiftStyle.values()) {
				rift.then(Commands.literal(style.getSerializedName())
						.executes(ctx -> openRift(ctx.getSource(), inFrontOf(ctx.getSource().getPlayerOrException()), style))
						.then(Commands.argument("pos", Vec3Argument.vec3()).executes(
								ctx -> openRift(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"), style)
						)));
			}
			dispatcher.register(Commands.literal("voidanchor").requires(Permissions.require("metacraft.qol.voidanchor", 2)).then(rift));
		});
	}

	private static Vec3 inFrontOf(ServerPlayer player) {
		var look = player.getLookAngle();
		return player.position().add(look.x * 3, -1.5, look.z * 3);
	}

	private static int openRift(CommandSourceStack source, Vec3 pos, RiftStyle style) {
		var config = VoidAnchorConfig.getInstance();
		Rift.open(source.getLevel(), pos, config.riftSize(), style != null ? style : config.riftStyle()).closeAfter(DEBUG_RIFT_TICKS);
		source.sendSuccess(() -> Component.literal(
				"Opened a rift at %.1f %.1f %.1f".formatted(pos.x, pos.y, pos.z)
		), true);
		return 1;
	}

}
