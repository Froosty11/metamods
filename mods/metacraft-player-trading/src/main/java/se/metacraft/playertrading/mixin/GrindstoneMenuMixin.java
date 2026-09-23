package se.metacraft.playertrading.mixin;

import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.playertrading.item.items.ShopItem;

@Mixin(GrindstoneMenu.class)
public class GrindstoneMenuMixin {

	@Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
	public void computeResult(
		ItemStack input, ItemStack additional, CallbackInfoReturnable<ItemStack> cir
	) {
		if (input.getItem() instanceof ShopItem) {
			cir.setReturnValue(new ItemStack(input.getItem(), input.count()));
		}
	}

	@Mixin(targets = "net/minecraft/world/inventory/GrindstoneMenu$2")
	public static class FirstSlotMixin {
		@Inject(
			method = "mayPlace",
			at = @At("HEAD"),
			cancellable = true
		)
		public void mayPlace(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
			if (itemStack.getItem() instanceof ShopItem) {
				cir.setReturnValue(true);
			}
		}
	}

}
