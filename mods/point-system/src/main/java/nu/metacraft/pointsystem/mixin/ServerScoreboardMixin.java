package nu.metacraft.pointsystem.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.ScoreHolder;
import nu.metacraft.pointsystem.PointSystemMod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerScoreboard.class)
public abstract class ServerScoreboardMixin extends ScoreboardMixin {

	@Shadow
	@Final
	private MinecraftServer server;

	@Unique
	private boolean visibleSlotForPlayer(DisplaySlot slot, ServerPlayer player) {
		if (displayObjectives().containsKey(slot)) {
			var team = getPlayersTeam(player.getScoreboardName());
			if (team != null) {
				var colour = team.getColor();
				if (colour.isPresent() && slot == colour.get().displaySlot()) {
					return true;
				}
			}

			return slot == DisplaySlot.SIDEBAR;
		}
		return false;
	}

	@Override
	public void onDisplayObjectiveChange(DisplaySlot slot, Objective objective, CallbackInfo ci) {
		var pointsystem = PointSystemMod.getPointSystem(server);
		if (pointsystem != null) {
			for (var player : server.getPlayerList().getPlayers()) {
				if (visibleSlotForPlayer(slot, player)) {
					pointsystem.updatePlayerScore(player);
				}
			}
		}
	}

	@Unique
	private DisplaySlot getSlot(Objective objective) {
		for (var entry : displayObjectives().entrySet()) {
			if (objective == entry.getValue()) {
				return entry.getKey();
			}
		}
		return null;
	}

	@Inject(method = "onScoreChanged", at = @At("RETURN"))
	protected void onScoreChanged(ScoreHolder owner, Objective objective, Score score, CallbackInfo ci) {
		var slot = getSlot(objective);
		var pointsystem = PointSystemMod.getPointSystem(server);
		if (pointsystem != null && slot != null) {
			for (var player : server.getPlayerList().getPlayers()) {
				if (visibleSlotForPlayer(slot, player)) {
					pointsystem.updatePlayerScore(player);
				}
			}
		}
	}

}
