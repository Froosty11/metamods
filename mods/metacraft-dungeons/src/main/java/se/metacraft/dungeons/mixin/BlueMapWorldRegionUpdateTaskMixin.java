package se.metacraft.dungeons.mixin;

import de.bluecolored.bluemap.common.rendermanager.WorldRegionUpdateTask;
import de.bluecolored.bluemap.core.map.BmMap;
import de.bluecolored.bluemap.core.world.mca.MCAWorld;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.dungeons.compat.BlueMapCompat;

@Mixin(WorldRegionUpdateTask.class)
public abstract class BlueMapWorldRegionUpdateTaskMixin {

	@Shadow @Final private BmMap map;

	@Shadow public abstract void cancel();

	@Inject(
		method = "doWork",
		at = @At("HEAD"),
		cancellable = true
	)
	private void doWork(CallbackInfo ci) {
		if (map.getWorld() instanceof MCAWorld w && BlueMapCompat.isDisabled(w.getDimension())) {
			cancel();
			ci.cancel();
		}
	}

}
