package nu.metacraft.qol.silence_mobs;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Silence mobs: a name tag named "silence me" silences the mob it's used on, "unsilence me" undoes
 * it. Case, spaces and underscores don't matter. The mob keeps whatever name it had; the tag is
 * used up as naming would use it. The Vanilla Tweaks datapack this replaces left every silenced mob
 * named "silenced" and silenced the nearest mob with the name, not the one clicked.
 */
public final class SilenceMobs {

	private static final int GLOW_TICKS = 60;

	private SilenceMobs() {}

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof LivingEntity mob)) {
				return InteractionResult.PASS;
			}
			if (mob instanceof Player || !mob.isAlive() || !SilenceMobsConfig.getInstance().enabled()) {
				return InteractionResult.PASS;
			}
			var stack = player.getItemInHand(hand);
			var silence = command(stack);
			if (silence == null) {
				return InteractionResult.PASS;
			}
			apply(serverPlayer, mob, silence);
			stack.consume(1, player);
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	/** True for a "silence me" tag, false for "unsilence me", null for anything else. */
	private static Boolean command(ItemStack stack) {
		var name = stack.is(Items.NAME_TAG) ? stack.get(DataComponents.CUSTOM_NAME) : null;
		if (name == null) {
			return null;
		}
		return switch (name.getString().trim().toLowerCase(Locale.ROOT).replace('_', ' ')) {
			case "silence me" -> true;
			case "unsilence me" -> false;
			default -> null;
		};
	}

	private static void apply(ServerPlayer player, LivingEntity mob, boolean silence) {
		mob.setSilent(silence);
		mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, true, false));
		mob.level().playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.8f, 2.0f);
		player.sendOverlayMessage(Component.translatableWithFallback(
				silence ? "qol.metacraft.silence_mobs.silenced" : "qol.metacraft.silence_mobs.unsilenced",
				silence ? "Silenced" : "Unsilenced"
		).withStyle(ChatFormatting.GRAY));
	}

}
