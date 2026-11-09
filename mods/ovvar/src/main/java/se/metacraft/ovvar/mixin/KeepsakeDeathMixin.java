package se.metacraft.ovvar.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.ovvar.content.enchantment.Keepsake;

/**
 * Keepsake: just before a dying player's inventory is dropped, the Keepsake ovvar are set aside. That
 * point is only reached with {@code keepInventory} off, which is exactly when they would be lost.
 */
@Mixin(Player.class)
public abstract class KeepsakeDeathMixin {
	@Inject(method = "dropEquipment", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;dropAll()V"))
	private void ovvar$keepsake(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) Keepsake.stash(player);
	}
}
