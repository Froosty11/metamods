package nu.metacraft.nether_rifts.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.RiftMessages;
import nu.metacraft.nether_rifts.rift.Rift;
import nu.metacraft.nether_rifts.rift.RiftData;
import nu.metacraft.nether_rifts.rift.Stage;

import java.util.List;
import java.util.function.BooleanSupplier;

/** A torn rift's raid: its waves, who takes part, the boss bar, strays, and how it ends. */
public final class RiftRaid {

	/** How long a wave may look cleared with its surroundings not entity-loaded before it counts as cleared anyway. */
	public static final int UNLOADED_GRACE = 200;

	private RiftRaid() {}

	public static void tick(ServerLevel level, Rift rift, RiftData data) {
		var config = NetherRiftsConfig.getInstance();
		var players = participants(level, rift);
		var bar = bossBar(rift);
		if (level.getGameTime() % 20 == 0) {
			for (var shown : List.copyOf(bar.getPlayers())) {
				if (!players.contains(shown)) {
					bar.removePlayer(shown);
				}
			}
			players.forEach(bar::addPlayer);
		}
		if (players.isEmpty()) {
			if (++rift.abandonTicks >= config.timers().abandonAfter()) {
				RiftMessages.say(level, rift, "Nobody held the rift. It collapses.");
				collapse(level, rift, data);
				return;
			}
		} else {
			rift.abandonTicks = 0;
		}
		if (rift.stage() != Stage.RAID) {
			return;
		}
		var alive = aliveMobs(level, rift);
		rift.waveTicks++;
		if (rift.waveTicks == config.timers().glowAfter()) {
			alive.forEach(mob -> mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false)));
		}
		if (level.getGameTime() % 20 == 0) {
			tether(level, rift, alive, config.ranges().tether());
		}
		bar.setProgress(rift.waveSize == 0 ? 0f : Mth.clamp(alive.size() / (float) rift.waveSize, 0f, 1f));
		advanceIfCleared(level, rift, data, alive, () -> surroundingsLoaded(level, rift, config.ranges().tether()));
	}

	/**
	 * When no mob of the wave is left, the next wave (or victory). An unloaded mob isn't a dead one, so with
	 * part of the tether range not entity-loaded the wave holds, but only for {@link #UNLOADED_GRACE} ticks:
	 * a cleared raid must never stall.
	 */
	public static void advanceIfCleared(ServerLevel level, Rift rift, RiftData data, List<LivingEntity> alive, BooleanSupplier surroundingsLoaded) {
		if (rift.stage() != Stage.RAID) {
			return;
		}
		if (!alive.isEmpty()) {
			rift.clearedTicks = 0;
			return;
		}
		if (!surroundingsLoaded.getAsBoolean() && ++rift.clearedTicks < UNLOADED_GRACE) {
			return;
		}
		rift.clearedTicks = 0;
		if (rift.wave() >= rift.tier().waves()) {
			victory(level, rift, data);
		} else {
			startWave(level, rift, rift.wave() + 1);
		}
	}

	public static void startWave(ServerLevel level, Rift rift, int wave) {
		var config = NetherRiftsConfig.getInstance();
		int players = Math.max(1, participants(level, rift).size());
		var counts = WaveTable.counts(rift.tier(), wave, players, level.getRandom()::nextInt, config.trooperScalingPerPlayer(), config.trooperScalingCap());
		int spawned = 0;
		for (var entry : counts.entrySet()) {
			for (int i = 0; i < entry.getValue(); i++) {
				spawned += spawnOne(level, rift, entry.getKey());
			}
		}
		rift.setWave(wave, spawned);
		bossBar(rift).setName(waveName(rift));
		bossBar(rift).setProgress(1f);
	}

	private static Component waveName(Rift rift) {
		return Component.literal("Rift Raid: Wave " + rift.wave() + "/" + rift.tier().waves());
	}

	private static int spawnOne(ServerLevel level, Rift rift, Slot slot) {
		var random = level.getRandom();
		var normal = rift.shape().axis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
		var side = random.nextBoolean() ? normal : normal.getOpposite();
		BlockPos pos;
		if (slot == Slot.HEAVY) {
			pos = ghastSpot(level, rift, side);
		} else {
			pos = rift.randomBlock(random).relative(side);
		}
		var push = new Vec3(side.getStepX() * 0.35, 0.2, side.getStepZ() * 0.35);
		return RiftMobs.spawn(level, rift.id(), slot, pos, push).size();
	}

	/** A 4×4×4 clear box: in front of the rift on either side, then nearby, then straight above. */
	private static BlockPos ghastSpot(ServerLevel level, Rift rift, Direction side) {
		var centre = rift.shape().centreBlock();
		for (var dir : new Direction[] {side, side.getOpposite()}) {
			for (int out = 4; out <= 8; out += 2) {
				var pos = centre.relative(dir, out).above(2);
				if (level.noCollision(EntityTypes.GHAST.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
					return pos;
				}
			}
		}
		return centre.above(6);
	}

	private static void tether(ServerLevel level, Rift rift, List<LivingEntity> alive, int range) {
		var centre = rift.centre();
		for (var mob : alive) {
			if (mob.getVehicle() == null && mob.position().distanceToSqr(centre) > (double) range * range) {
				var back = clearSpot(level, rift, mob);
				level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.4, 0.8, 0.4, 0.5);
				mob.teleportTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
			}
		}
	}

	/** In front of the rift, on either side and a few blocks out, where the mob's whole body fits. */
	private static BlockPos clearSpot(ServerLevel level, Rift rift, LivingEntity mob) {
		var random = level.getRandom();
		var normal = rift.shape().axis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
		var side = random.nextBoolean() ? normal : normal.getOpposite();
		if (mob.getType() == EntityTypes.GHAST) {
			return ghastSpot(level, rift, side);
		}
		for (var dir : new Direction[] {side, side.getOpposite()}) {
			for (int out = 1; out <= 4; out++) {
				var pos = rift.randomBlock(random).relative(dir, out);
				if (level.noCollision(mob.getType().getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
					return pos;
				}
			}
		}
		return rift.randomBlock(random).relative(side);
	}

	/** Whether entities are loaded across the tether range (they follow view distance, not simulation distance). */
	private static boolean surroundingsLoaded(ServerLevel level, Rift rift, int range) {
		var c = rift.shape().centreBlock();
		var min = ChunkPos.containing(c.offset(-range, 0, -range));
		var max = ChunkPos.containing(c.offset(range, 0, range));
		for (int x = min.x(); x <= max.x(); x++) {
			for (int z = min.z(); z <= max.z(); z++) {
				if (!level.areEntitiesLoaded(ChunkPos.pack(x, z))) {
					return false;
				}
			}
		}
		return true;
	}

	public static List<LivingEntity> aliveMobs(ServerLevel level, Rift rift) {
		double range = NetherRiftsConfig.getInstance().ranges().tether() + 48;
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(rift.centre(), rift.centre()).inflate(range),
				e -> e.isAlive() && rift.id().equals(e.getAttached(RiftMobs.RIFT)));
	}

	public static List<ServerPlayer> participants(ServerLevel level, Rift rift) {
		double range = NetherRiftsConfig.getInstance().ranges().participants();
		var centre = rift.centre();
		return level.players().stream()
				.filter(p -> p.isAlive() && !p.isSpectator() && p.position().distanceToSqr(centre) <= range * range)
				.toList();
	}

	public static void victory(ServerLevel level, Rift rift, RiftData data) {
		var c = rift.centre();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE, 2.0f, 0.7f);
		RiftMessages.say(level, rift, "The rift closes. The realm takes back what it lost.");
		var key = ResourceKey.create(Registries.LOOT_TABLE, NetherRifts.getID("victory/" + rift.tier().getSerializedName()));
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, c).create(LootContextParamSets.CHEST);
		for (var stack : table.getRandomItems(params)) {
			var item = new ItemEntity(level, c.x, c.y, c.z, stack);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		// A natural victory has no mobs left; /rifts close may.
		end(level, rift, data, true);
	}

	public static void collapse(ServerLevel level, Rift rift, RiftData data) {
		var c = rift.centre();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
		end(level, rift, data, true);
	}

	/**
	 * Takes the rift down: its mobs (if asked), blocks, frame (as config says), boss bar and visual. Only frame
	 * positions still holding a frame material change: whatever a player put in a broken frame stays.
	 */
	public static void end(ServerLevel level, Rift rift, RiftData data, boolean discardMobs) {
		if (discardMobs) {
			for (var mob : aliveMobs(level, rift)) {
				level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.4, 0.8, 0.4, 0.5);
				mob.discard();
			}
		}
		rift.clearBlocks(level);
		var collapse = NetherRiftsConfig.getInstance().frameCollapse();
		for (var pos : rift.shape().frame()) {
			var block = level.getBlockState(pos).getBlock();
			if (!rift.shape().frameMaterials().contains(block)) {
				continue;
			}
			var to = rift.crying() ? Blocks.AIR : collapse.getOrDefault(block, Blocks.AIR);
			level.setBlockAndUpdate(pos, to.defaultBlockState());
		}
		if (rift.bossBar() != null) {
			rift.bossBar().removeAllPlayers();
		}
		rift.destroyVisual();
		data.remove(rift);
	}

	/** The rift's boss bar; after a restart (bars aren't saved) it comes back already naming the wave. */
	private static ServerBossEvent bossBar(Rift rift) {
		if (rift.bossBar() == null) {
			var name = rift.stage() == Stage.RAID ? waveName(rift) : Component.literal("Rift Raid");
			rift.setBossBar(new ServerBossEvent(Mth.createInsecureUUID(RandomSource.create()), name, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10));
		}
		return rift.bossBar();
	}
}
