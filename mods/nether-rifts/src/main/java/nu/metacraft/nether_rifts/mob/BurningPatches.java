package nu.metacraft.nether_rifts.mob;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The ground under a falling magma bomb: a 1 s warning (a ring of ember particles and a whistle) where it
 * will land, then a 2 s burning patch where it did (lava particles; players standing in it catch fire).
 * Neither touches blocks.
 */
public final class BurningPatches {

	public static final int WARN_TICKS = 20;
	public static final int BURN_TICKS = 40;
	public static final int PLAYER_FIRE_TICKS = 60;
	public static final double RADIUS = 1.5;

	public record Patch(Vec3 at, long endsAt, boolean warning) {}

	private static final Map<ServerLevel, List<Patch>> PATCHES = new WeakHashMap<>();

	private BurningPatches() {}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(BurningPatches::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PATCHES.clear());
	}

	public static void warn(ServerLevel level, Vec3 at) {
		add(level, new Patch(at, level.getGameTime() + WARN_TICKS, true));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.0f, 0.5f);
	}

	public static void burn(ServerLevel level, Vec3 at) {
		add(level, new Patch(at, level.getGameTime() + BURN_TICKS, false));
	}

	/** Where something falling straight down from {@code from} lands. */
	public static Vec3 groundBelow(ServerLevel level, Vec3 from) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(from.x), Mth.floor(from.z));
		return new Vec3(from.x, Math.min(from.y, y), from.z);
	}

	public static List<Patch> active(ServerLevel level) {
		return List.copyOf(PATCHES.getOrDefault(level, List.of()));
	}

	private static void add(ServerLevel level, Patch patch) {
		PATCHES.computeIfAbsent(level, l -> new ArrayList<>()).add(patch);
	}

	private static void tick(ServerLevel level) {
		var patches = PATCHES.get(level);
		if (patches == null || patches.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		patches.removeIf(p -> p.endsAt() <= now);
		for (var p : patches) {
			if (p.warning()) {
				if (now % 2 == 0) {
					for (int i = 0; i < 12; i++) {
						float angle = i * Mth.TWO_PI / 12;
						level.sendParticles(ParticleTypes.SMALL_FLAME, p.at().x + Mth.cos(angle) * RADIUS, p.at().y + 0.1,
								p.at().z + Mth.sin(angle) * RADIUS, 1, 0, 0, 0, 0);
					}
				}
				continue;
			}
			level.sendParticles(ParticleTypes.LAVA, p.at().x, p.at().y + 0.1, p.at().z, 2, RADIUS / 2, 0.05, RADIUS / 2, 0);
			var box = new AABB(p.at(), p.at()).inflate(RADIUS, 1.0, RADIUS);
			for (var player : level.getEntitiesOfClass(Player.class, box, pl -> !pl.isSpectator() && !pl.isCreative())) {
				player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), PLAYER_FIRE_TICKS));
			}
		}
	}
}
