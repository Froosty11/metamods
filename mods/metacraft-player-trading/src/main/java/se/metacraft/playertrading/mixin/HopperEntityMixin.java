package se.metacraft.playertrading.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;

@Mixin(HopperBlockEntity.class)
public class HopperEntityMixin {

	@Shadow private Direction facing;

	@Unique
	private static void cancelMoveIfConnectedToShop(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (ShopBlockEntity.hasShop(level, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "ejectItems", at = @At("HEAD"), cancellable = true)
	private static void ejectItems(
			Level level, BlockPos blockPos, HopperBlockEntity hopperBlockEntity, CallbackInfoReturnable<Boolean> cir
	) {
		var facing = ((HopperEntityMixin) (Object) hopperBlockEntity).facing;
		cancelMoveIfConnectedToShop(level, blockPos.relative(facing), cir);
	}

	@Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
	private static void suckInItems(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> callback) {
		var pos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ());
		cancelMoveIfConnectedToShop(level, pos.relative(Direction.UP), callback);
	}
}
