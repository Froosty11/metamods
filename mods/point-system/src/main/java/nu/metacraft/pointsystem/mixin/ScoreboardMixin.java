package nu.metacraft.pointsystem.mixin;

import net.minecraft.world.scores.*;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(Scoreboard.class)
public abstract class ScoreboardMixin {

	@Shadow
	@Final
	private Map<DisplaySlot, Objective> displayObjectives;

	@Unique
	protected Map<DisplaySlot, Objective> displayObjectives() {
		return displayObjectives;
	}

	@Shadow
	public abstract @Nullable PlayerTeam getPlayersTeam(String name);

	@Inject(method = "setDisplayObjective", at = @At("RETURN"))
	public void onDisplayObjectiveChange(DisplaySlot slot, Objective objective, CallbackInfo ci) {

	}
	
}
