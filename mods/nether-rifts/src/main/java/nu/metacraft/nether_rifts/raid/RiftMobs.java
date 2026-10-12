package nu.metacraft.nether_rifts.raid;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.mixin.MobAccessor;
import nu.metacraft.nether_rifts.rift.RiftData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Mobs that come through a rift, and what sets them apart from the nether's own. */
public final class RiftMobs {

	/** The rift a mob came through; saved with the mob, so a raid survives a restart. */
	public static final AttachmentType<UUID> RIFT = AttachmentRegistry.create(
			NetherRifts.getID("rift"), builder -> builder.persistent(UUIDUtil.CODEC)
	);

	private RiftMobs() {}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			var id = entity.getAttached(RIFT);
			if (id != null && RiftData.get(level).byId(id).isEmpty()) {
				entity.discard();
			}
		});
	}

	public static Optional<UUID> riftOf(Entity entity) {
		return Optional.ofNullable(entity.getAttached(RIFT));
	}

	public static boolean isRiftMob(@Nullable Entity entity) {
		return entity != null && entity.hasAttached(RIFT);
	}

	/** One pick from the slot's roster entry at {@code pos}, pushed by {@code push}; returns every entity spawned. */
	public static List<Entity> spawn(ServerLevel level, UUID rift, Slot slot, BlockPos pos, Vec3 push) {
		var entry = NetherRiftsConfig.getInstance().roster().get(slot);
		if (entry == null) {
			return List.of();
		}
		var spawned = new ArrayList<Entity>();
		entry.mobs().getRandom(level.getRandom()).ifPresent(pick -> pick.spawnMobFromNBT(level, pos, entity -> {
			apply(entity, rift, slot);
			if (entity.getVehicle() == null) {
				entity.setDeltaMovement(push);
			}
			spawned.add(entity);
		}));
		return spawned;
	}

	static void apply(Entity entity, UUID rift, Slot slot) {
		entity.setAttached(RIFT, rift);
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
			mob.setCanPickUpLoot(false);
			for (var equipmentSlot : EquipmentSlot.values()) {
				mob.setDropChance(equipmentSlot, 0f);
			}
			lootTable(mob, slot).ifPresent(table -> ((MobAccessor) mob).netherRifts$setLootTable(Optional.of(table)));
		}
		if (entity instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true);
		}
		if (entity instanceof Hoglin hoglin) {
			hoglin.setImmuneToZombification(true);
		}
	}

	/** Piglins and brutes drop rift loot by role; ghasts, hoglins and striders keep their own. */
	static Optional<ResourceKey<LootTable>> lootTable(Mob mob, Slot slot) {
		String role;
		if (mob instanceof PiglinBrute) {
			role = slot == Slot.CAPTAIN ? "captain" : "elite";
		} else if (mob instanceof AbstractPiglin) {
			role = slot == Slot.LEADER ? "leader" : "trooper";
		} else {
			return Optional.empty();
		}
		return Optional.of(ResourceKey.create(Registries.LOOT_TABLE, NetherRifts.getID("entities/" + role)));
	}
}
