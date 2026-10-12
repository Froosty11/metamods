package nu.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import org.jetbrains.annotations.Nullable;

/**
 * Dropped from a war ghast's deck by its Ghast Bombardier. Clients see a thrown magma cream (a snowball carrying that item).
 * On impact it blasts (damage and knockback, power 1.5) with ExplosionInteraction.NONE, so no block changes
 * and nothing is set alight, and leaves a burning patch for 2 s. The power is config ({@code mobs.bombardier.bomb_power}).
 */
public class MagmaBomb extends ThrowableItemProjectile implements PolymerEntity {

	/** Default for {@link NetherRiftsConfig.BombardierTuning#bombPower()}. */
	public static final float POWER = 1.5f;

	/** ThrowableProjectile's pull and air drag, applied in that order each tick before the move. */
	private static final double GRAVITY = 0.03;
	private static final double DRAG = 0.99f;
	/** A plain drop: let go with a little downward push. */
	private static final double DROP_SPEED = -0.15;
	private static final double MAX_LOB = 1.0;
	private static final int MAX_FLIGHT = 600;

	/**
	 * Bombs are for players (and their pets and mounts). Bombing runs must not thin their own waves, so rift mobs
	 * take no blast damage or knockback; nor do things that aren't alive (loot on the ground, item frames, paintings)
	 * or armour stands.
	 */
	private static final ExplosionDamageCalculator SPARE_ALL_BUT_PLAYERS = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return !spared(entity) && super.shouldDamageEntity(explosion, entity);
		}

		@Override
		public float getKnockbackMultiplier(Entity entity) {
			return spared(entity) ? 0f : super.getKnockbackMultiplier(entity);
		}
	};

	static boolean spared(Entity entity) {
		return !(entity instanceof LivingEntity) || entity instanceof ArmorStand || RiftMobs.isRiftMob(entity);
	}

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

	/** Ticks until a bomb let go {@code height} blocks above the ground, moving up at {@code vy}, hits it. */
	public static int ticksToLand(double height, double vy) {
		double y = height;
		double v = vy;
		for (int tick = 1; tick < MAX_FLIGHT; tick++) {
			v = (v - GRAVITY) * DRAG;
			if (y + v <= 0) {
				return tick;
			}
			y += v;
		}
		return MAX_FLIGHT;
	}

	/** The vertical speed for a bomb let go {@code height} blocks up: a plain drop, or the gentlest lob that keeps it up {@code ticks}. */
	public static double launchSpeed(double height, int ticks) {
		for (double vy = DROP_SPEED; vy < MAX_LOB; vy += 0.025) {
			if (ticksToLand(height, vy) >= ticks) {
				return vy;
			}
		}
		return MAX_LOB;
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
			float power = NetherRiftsConfig.getInstance().mobs().bombardier().bombPower();
			level.explode(this, null, SPARE_ALL_BUT_PLAYERS, getX(), getY(), getZ(), power, false, Level.ExplosionInteraction.NONE);
			BurningPatches.burn(level, BurningPatches.groundBelow(level, position()), getAttached(RiftMobs.RIFT));
			discard();
		}
	}
}
