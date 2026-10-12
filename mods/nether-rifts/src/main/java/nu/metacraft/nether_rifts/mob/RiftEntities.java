package nu.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import nu.metacraft.nether_rifts.NetherRifts;

/** The rift's own entities: server-side (Polymer), shown to vanilla clients as display models or vanilla mobs. */
public final class RiftEntities {

	public static final EntityType<WarDrummer> WAR_DRUMMER = living("war_drummer",
			EntityType.Builder.of(WarDrummer::new, MobCategory.MONSTER).sized(0.6f, 2.3f).clientTrackingRange(10),
			WarDrummer.createAttributes());

	public static final EntityType<MagmaBomb> MAGMA_BOMB = register("magma_bomb",
			EntityType.Builder.<MagmaBomb>of(MagmaBomb::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10));

	private RiftEntities() {}

	public static void init() {
	}

	static <T extends LivingEntity> EntityType<T> living(String name, EntityType.Builder<T> builder, AttributeSupplier.Builder attributes) {
		var type = register(name, builder);
		FabricDefaultAttributeRegistry.register(type, attributes);
		return type;
	}

	static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, NetherRifts.getID(name));
		var type = builder.build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
