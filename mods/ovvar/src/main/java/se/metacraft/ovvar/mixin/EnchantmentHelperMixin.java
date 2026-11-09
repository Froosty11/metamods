package se.metacraft.ovvar.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;

import java.util.stream.Stream;

/**
 * A switched-off ovve enchantment (or the curse without Brewery) is never picked from a pool of
 * candidates: the enchanting table and {@code enchant_with_levels} both draw from
 * {@code getAvailableEnchantmentResults}.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@ModifyVariable(method = "getAvailableEnchantmentResults", at = @At("HEAD"), argsOnly = true)
	private static Stream<Holder<Enchantment>> ovvar$onlyOffered(Stream<Holder<Enchantment>> source) {
		return source.filter(OvveEnchantments::offered);
	}
}
