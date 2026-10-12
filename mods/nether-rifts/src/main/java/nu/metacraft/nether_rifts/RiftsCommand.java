package nu.metacraft.nether_rifts;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

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
				}));
	}
}
