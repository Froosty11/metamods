package nu.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import org.jetbrains.annotations.Nullable;

/**
 * Dropped by the Ghast Bombardier. Clients see a thrown magma cream (a snowball carrying that item).
 * On impact it blasts (damage and knockback, power 1.5) with ExplosionInteraction.NONE, so no block changes
 * and nothing is set alight, and leaves a burning patch for 2 s.
 */
public class MagmaBomb extends ThrowableItemProjectile implements PolymerEntity {

	public static final float POWER = 1.5f;

	/** Bombing runs must not thin their own waves: rift mobs take no blast damage or knockback. */
	private static final ExplosionDamageCalculator SPARE_RIFT_MOBS = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return !RiftMobs.isRiftMob(entity) && super.shouldDamageEntity(explosion, entity);
		}
	};

	public MagmaBomb(EntityType<? extends MagmaBomb> type, Level level) {
		super(type, level);
	}

	public MagmaBomb(ServerLevel level, @Nullable LivingEntity thrower) {
		super(RiftEntities.MAGMA_BOMB, level);
		if (thrower != null) {
			setOwner(thrower);
		}
		setItem(new ItemStack(Items.MAGMA_CREAM));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.MAGMA_CREAM;
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.SNOWBALL;
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel level) {
			level.explode(this, null, SPARE_RIFT_MOBS, getX(), getY(), getZ(), POWER, false, Level.ExplosionInteraction.NONE);
			BurningPatches.burn(level, BurningPatches.groundBelow(level, position()));
			discard();
		}
	}
}
