package nu.metacraft.void_anchor.rift;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.void_anchor.AnchorBinding;
import nu.metacraft.void_anchor.VoidAnchorConfig;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Catches players falling into the End void. Below the End's lowest Y there is nothing to stand
 * on, so a bound player there gets a rift beneath them, sinks into it slowly, and is taken to
 * their anchor. The anchor is checked again at the last moment, and a charge is spent only when
 * the player is actually moved.
 */
public final class RiftTracker {

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	/** Players whose current fall has been dealt with; forgotten once they are back above the trigger or out of the End. */
	private static final Set<UUID> HANDLED = new HashSet<>();

	private static final class Session {
		final Rift rift;
		final Vec3 centre;
		int ticks;

		Session(Rift rift, Vec3 centre) {
			this.rift = rift;
			this.centre = centre;
		}
	}

	private RiftTracker() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RiftTracker::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SESSIONS.clear();
			HANDLED.clear();
		});
	}

	public static boolean isRifting(UUID player) {
		return SESSIONS.containsKey(player);
	}

	private static void tick(MinecraftServer server) {
		var end = server.getLevel(Level.END);
		var config = VoidAnchorConfig.getInstance();
		advance(server, end, config);
		if (end == null) {
			return;
		}
		double trigger = end.getMinY() + config.triggerYOffset();
		HANDLED.removeIf(id -> {
			var player = server.getPlayerList().getPlayer(id);
			return player == null || player.level() != end || player.getY() >= trigger;
		});
		for (var player : end.players()) {
			var id = player.getUUID();
			if (player.getY() >= trigger || SESSIONS.containsKey(id) || HANDLED.contains(id)) {
				continue;
			}
			if (player.isSpectator() || player.getAbilities().flying || !player.isAlive() || player.isPassenger()) {
				continue;
			}
			start(player, end, config);
		}
	}

	private static void start(ServerPlayer player, ServerLevel end, VoidAnchorConfig config) {
		var resolution = AnchorBinding.resolve(player);
		if (resolution instanceof AnchorBinding.Ready) {
			var centre = new Vec3(player.getX(), player.getY() - config.riftDepth(), player.getZ());
			SESSIONS.put(player.getUUID(), new Session(Rift.open(end, centre, config.riftSize()), centre));
		} else {
			HANDLED.add(player.getUUID());
			hint(player, resolution);
		}
	}

	private static void advance(MinecraftServer server, @Nullable ServerLevel end, VoidAnchorConfig config) {
		var it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			var session = entry.getValue();
			var player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.level() != end) {
				// Gone, dead or elsewhere: close up and spend nothing.
				session.rift.close();
				it.remove();
				continue;
			}
			session.ticks++;
			if (session.ticks >= config.riftTicks() || player.getY() <= session.centre.y + 0.5) {
				it.remove();
				HANDLED.add(player.getUUID());
				session.rift.close();
				finish(player);
			} else {
				ease(player, session.centre, config);
			}
		}
	}

	/** Holds the fall to a slow drift toward the rift's centre; needsSync sends the motion to the client. */
	private static void ease(ServerPlayer player, Vec3 centre, VoidAnchorConfig config) {
		double dx = Mth.clamp((centre.x - player.getX()) * 0.25, -0.5, 0.5);
		double dz = Mth.clamp((centre.z - player.getZ()) * 0.25, -0.5, 0.5);
		player.setDeltaMovement(dx, -config.descentSpeed(), dz);
		player.needsSync = true;
		player.resetFallDistance();
	}

	/** Checks the anchor again: it may have been broken or drained while the player sank. */
	private static void finish(ServerPlayer player) {
		var resolution = AnchorBinding.resolve(player);
		if (resolution instanceof AnchorBinding.Ready ready && AnchorBinding.consume(ready.level(), ready.anchor())) {
			player.teleport(new TeleportTransition(
					ready.level(), ready.standUp(), Vec3.ZERO, ready.yaw(), 0, TeleportTransition.PLAY_PORTAL_SOUND
			));
			player.resetFallDistance();
			var at = ready.standUp();
			ready.level().sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 40, 0.4, 0.8, 0.4, 0.05);
		} else {
			hint(player, resolution instanceof AnchorBinding.Ready ? new AnchorBinding.Empty() : resolution);
		}
	}

	private static void hint(ServerPlayer player, AnchorBinding.Resolution resolution) {
		var message = switch (resolution) {
			case AnchorBinding.Empty empty -> Component.translatableWithFallback(
					"block.metacraft.void_anchor.hint.empty", "Your void anchor is empty"
			);
			case AnchorBinding.Gone gone -> Component.translatableWithFallback(
					"block.metacraft.void_anchor.hint.gone", "Your void anchor is gone"
			);
			case AnchorBinding.NotInEnd notInEnd -> Component.translatableWithFallback(
					"block.metacraft.void_anchor.hint.gone", "Your void anchor is gone"
			);
			case AnchorBinding.Blocked blocked -> Component.translatableWithFallback(
					"block.metacraft.void_anchor.hint.blocked", "Your void anchor is blocked"
			);
			case AnchorBinding.Unbound unbound -> null;
			case AnchorBinding.Ready ready -> null;
		};
		if (message != null) {
			player.sendOverlayMessage(message.withStyle(ChatFormatting.RED));
		}
	}

}
