package nu.metacraft.nether_rifts;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import nu.metacraft.nether_rifts.rift.Rift;

/** What players near a rift are told. Plan 3 swaps the /guide link for the guidebook's Rifts page. */
public final class RiftMessages {

	private RiftMessages() {}

	public static void warn(ServerLevel level, Rift rift) {
		var message = Component.literal("The portal flickers but will not hold. The realm is unstable. ").withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(Component.literal("[Read more]").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
						.withClickEvent(new ClickEvent.RunCommand("/guide"))));
		send(level, rift, message);
	}

	public static void say(ServerLevel level, Rift rift, String text) {
		send(level, rift, Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private static void send(ServerLevel level, Rift rift, Component message) {
		double range = NetherRiftsConfig.getInstance().ranges().warning();
		var centre = rift.centre();
		for (var player : level.players()) {
			if (player.position().distanceToSqr(centre) <= range * range) {
				player.sendSystemMessage(message);
			}
		}
	}
}
