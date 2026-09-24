package nu.metacraft.info_commands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "Info Commands", description = "Custom chat-message commands and a periodic broadcast message.")
public record InfoConfig(
	@Option(description = "Custom commands, each with a message and optional subcommands.", key = "commands") Map<String, InfoNode> commands,
	@Option(description = "Resends the command tree to all players when /reload runs.", key = "resendCommandTreeOnReload") boolean resendCommandTreeOnReload,
	@Option(description = "Registers the /meta-info-resend-command-tree command.", key = "enableResendCommandTreeCommand", restart = true) boolean enableResendCommandTreeCommand,
	@Option(description = "How often, in ticks, a periodic message is broadcast to all players.", key = "infoMessageIntervalTicks") int infoMessageIntervalTicks,
	@Option(description = "Text prepended to each periodic info message.", key = "infoMessagePrefix") Component infoMessagePrefix,
	@Option(description = "Messages broadcast in rotation, one every info_message_interval_ticks.", key = "infoMessages") List<InfoMessage> infoMessages
) {

	public static final MapCodec<InfoConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, InfoNode.CODEC).fieldOf("commands").forGetter(InfoConfig::commands),
			Codec.BOOL.fieldOf("resendCommandTreeOnReload").forGetter(InfoConfig::resendCommandTreeOnReload),
			Codec.BOOL.fieldOf("enableResendCommandTreeCommand").forGetter(InfoConfig::enableResendCommandTreeCommand),
			Codec.INT.fieldOf("infoMessageIntervalTicks").forGetter(c -> c.infoMessageIntervalTicks),
			ComponentSerialization.CODEC.fieldOf("infoMessagePrefix").forGetter(c -> c.infoMessagePrefix),
			Codec.list(InfoMessage.CODEC).fieldOf("infoMessages").forGetter(c -> c.infoMessages)
	).apply(instance, InfoConfig::new));

	public InfoConfig() {
		this(
				new HashMap<>(),
				true,
				false,
				15 * 60 * 20,
				Component.literal(" \uD83D\uDEC8 ").withStyle(style -> style.withColor(ChatFormatting.AQUA)),
				new ArrayList<>()
		);
	}

	/** The example commands and message the mod ships with, reused as the config's DEFAULT. */
	public static InfoConfig createDefault() {
		var config = new InfoConfig();
		var sub = new HashMap<String, InfoNode>();
		config.commands().put("example1", new InfoNode(Component.literal("Test"), sub));
		sub.put("example3", new InfoNode(Component.literal("Look")));
		var sub2 = new HashMap<String, InfoNode>();
		sub2.put("example5", new InfoNode(Component.literal("Without limits")));
		sub.put("example4", new InfoNode(Component.literal("It's ").append(Component.literal("recursive.")), sub2));
		config.commands().put("example2", new InfoNode(
				Component.literal("And it supports JSON text! ").setStyle(
						Style.EMPTY.withBold(true).withItalic(true).withColor(0xccffff)
				).append(Component.literal("Cool right?").setStyle(Style.EMPTY.withObfuscated(true)))
		));
		config.infoMessages().add(new InfoMessage("example", Component.literal("Did you know? You can use /example1 to see information!")));
		return config;
	}

	public static final InfoConfig DEFAULT = createDefault();

}
