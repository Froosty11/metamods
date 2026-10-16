package nu.metacraft.nether_rifts.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.nether_rifts.SoulLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While rift mode is on, nether wart takes no ground: it can't be planted (see {@link SoulLock}). */
@Mixin(NetherWartBlock.class)
public abstract class NetherWartBlockMixin {

	@Inject(method = "mayPlaceOn", at = @At("HEAD"), cancellable = true)
	private void netherRifts$noWartInRiftMode(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (SoulLock.locked(level)) {
			cir.setReturnValue(false);
		}
	}
}
