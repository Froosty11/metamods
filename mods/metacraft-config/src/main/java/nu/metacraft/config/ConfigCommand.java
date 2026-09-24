package nu.metacraft.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import nu.metacraft.config.screen.Actions;
import nu.metacraft.config.source.ConfigSource;
import nu.metacraft.config.source.Sources;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** {@code /config} opens the list; {@code /config <id>} one config. */
public final class ConfigCommand {
	private ConfigCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("config")
				.requires(Permissions.require(Actions.PERMISSION, Actions.LEVEL))
				.executes(ctx -> {
					Actions.openMain(ctx.getSource().getPlayerOrException());
					return 1;
				})
				.then(argument("config", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Sources.all().stream().map(ConfigSource::id), builder))
						.executes(ctx -> {
							String id = StringArgumentType.getString(ctx, "config");
							ConfigSource source = Sources.find(id).orElse(null);
							if (source == null) {
								ctx.getSource().sendFailure(Component.literal("No config called " + id));
								return 0;
							}
							Actions.openConfig(ctx.getSource().getPlayerOrException(), source, List.of());
							return 1;
						})));
	}
}
