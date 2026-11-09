package se.metacraft.ovvar.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.content.MobOvvar;

/**
 * Mob ovvar: the armour vanilla's spawn roll hands a zombie, skeleton or the like is sometimes an
 * ovve (leggings) or a frack (chestplate) instead, rolled with the same random the spawn uses.
 */
@Mixin(Mob.class)
public abstract class MobOvvarMixin {
	@WrapOperation(method = "populateDefaultEquipmentSlots", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/world/entity/Mob;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"))
	private void ovvar$mobOvvar(Mob self, EquipmentSlot slot, ItemStack stack, Operation<Void> original, @Local(argsOnly = true) RandomSource random) {
		original.call(self, slot, MobOvvar.spawnPiece((Mob) (Object) this, slot, stack, random, OvvarConfig.get().mobOvvar()));
	}
}
