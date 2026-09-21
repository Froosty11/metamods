package metacraft.ovvar.compat.danse.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import de.tomalbrc.danse.util.MinecraftSkinParser;
import de.tomalbrc.danse.util.TextureCache;
import metacraft.ovvar.compat.danse.DansePixels;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where Danse asks a stack what armour to draw on a body part, an ovve answers for itself.
 *
 * <p>Danse's own path reads the stack's equipment asset and samples the layer PNGs out of the pack
 * builder as 64×32 armour textures. For an ovve every step of that is wrong — see
 * {@link DansePixels} — so an ovve or a companion top is answered whole, and a chestplate worn over
 * an ovve gets the top merged in underneath it.
 */
@Mixin(value = TextureCache.class, remap = false)
public abstract class TextureCacheMixin {

	/** An ovve, a companion top or the virtual cuffs: ours to answer, Danse's asset never consulted. */
	@Inject(method = "armorCustomModelData", at = @At("HEAD"), cancellable = true)
	private static void ovvar$ownPixels(
			MinecraftSkinParser.BodyPart part, ItemStack stack, boolean inner,
			CallbackInfoReturnable<CustomModelData> cir
	) {
		if (!DansePixels.ours(stack)) return;
		CustomModelData pixels = DansePixels.of(stack, part, inner);
		cir.setReturnValue(pixels == null ? CustomModelData.EMPTY : pixels);
	}

	/**
	 * A real chestplate over an ovve: Danse's chestplate pixels stand, and the ovve top shows
	 * through wherever they do not — the open arms and neck of a vanilla chestplate, which is the
	 * same thing the composite equipment asset does for a client that is not gesturing.
	 */
	@ModifyReturnValue(method = "armorCustomModelData", at = @At("RETURN"))
	private static CustomModelData ovvar$topUnderChestplate(
			CustomModelData original,
			MinecraftSkinParser.BodyPart part, ItemStack stack, boolean inner
	) {
		CustomModelData ours = DansePixels.underChestplate(stack, part, inner);
		return ours == null ? original : DansePixels.under(ours, original);
	}
}
