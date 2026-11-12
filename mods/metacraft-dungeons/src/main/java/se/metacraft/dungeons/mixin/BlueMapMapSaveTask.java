package se.metacraft.dungeons.mixin;

import de.bluecolored.bluemap.common.rendermanager.MapSaveTask;
import de.bluecolored.bluemap.core.map.BmMap;
import de.bluecolored.bluemap.core.world.mca.MCAWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.dungeons.compat.BlueMapCompat;

@Pseudo
@Mixin(MapSaveTask.class)
public class BlueMapMapSaveTask {

	@Shadow @Final private BmMap map;

	@Inject(
		method = "doWork",
		at = @At(
			value = "INVOKE",
			target = "Lde/bluecolored/bluemap/core/map/BmMap;save()V"
		),
		cancellable = true
	)
	private void doWork(CallbackInfo ci) {
		if (map.getWorld() instanceof MCAWorld w && BlueMapCompat.isDisabled(w.getDimension())) {
			ci.cancel();
		}
	}

}
