package metacraft.moredyes.mixin;

import metacraft.moredyes.armor.AnimatedArmor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.DyeRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's dye recipe would average the RGB armour's static colour into whatever dye it is
 * given. Armour dyed with an animated colour is dyed by {@link metacraft.moredyes.recipe.ModDyeRecipe}
 * instead, which starts from the new dyes alone.
 */
@Mixin(DyeRecipe.class)
public abstract class DyeRecipeMixin {
	@Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"), cancellable = true)
	private void moredyes$leaveAnimatedArmourToUs(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
		for (ItemStack stack : input.items()) {
			if (AnimatedArmor.animatedColour(stack).isPresent()) {
				cir.setReturnValue(false);
				return;
			}
		}
	}
}
