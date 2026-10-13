package nu.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A ghast in a war harness with a deck slung under it on chains, crewed by a Ghast Bombardier and two crossbow
 * piglins: one model (war_ghast.bbmodel), crew and all. It floats and shoots as any ghast and makes bombing runs,
 * the Bombardier dropping magma bombs from the deck. When it dies the crew jump off as real mobs and glide down.
 */
public class WarGhast extends Ghast implements AnimatedEntity {

	/** How far the deck's underside hangs below the ghast's feet (the model's y = -56). */
	public static final double DECK_DROP = 3.5;

	/** Where the crew stand, in model units from the ghast's feet (+x its right, -z its front); as gen_models.py. */
	private static final Vec3 BOMBARDIER_SPOT = new Vec3(5, -52, -28);
	private static final List<Vec3> CROSSBOW_SPOTS = List.of(new Vec3(-15, -52, -28), new Vec3(25.5, -52, -28));

	/** Crew glide time: enough to come down from a bombing run's climb and well beyond. */
	public static final int CREW_SLOW_FALL_TICKS = 200;

	private final LivingEntityHolder<WarGhast> holder;

	public WarGhast(EntityType<? extends WarGhast> type, Level level) {
		super(type, level);
		this.holder = new LivingEntityHolder<>(this, RiftModels.WAR_GHAST) {
			// the deck hangs below the box BIL culls the bones by: give them room down to its underside
			@Override
			protected void updateCullingBox() {
				float width = getScale() * dimensions.width() * 2;
				float height = -dimensions.height() - (float) DECK_DROP - 1;
				for (var bone : bones) {
					bone.element().setDisplaySize(width, height);
				}
			}
		};
		EntityAttachment.ofTicking(this.holder, this);
		this.holder.getAnimator().playAnimation("idle", 0, true);
	}

	@Override
	public LivingEntityHolder<WarGhast> getHolder() {
		return holder;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(4, new BombingRunGoal(this));
	}

	/** The ghast's shooting face, while vanilla's fireball goal charges a shot (half a second before it fires). */
	@Override
	public void setCharging(boolean charging) {
		if (charging && !isCharging()) {
			holder.getAnimator().playAnimation("shoot", 5, true);
		}
		super.setCharging(charging);
	}

	/**
	 * The ghast's box stretched down to the deck's underside: what has to fit wherever it goes. The deck and crew
	 * keep within the ghast's footprint.
	 */
	public AABB stackBox() {
		var box = getBoundingBox();
		return box.setMinY(box.minY - DECK_DROP);
	}

	/**
	 * The Bombardier throws a magma bomb off the deck, straight down onto the ground warning, lobbed up first if need
	 * be: the warning shows at least the configured warning time (default {@link BurningPatches#WARN_TICKS}) before
	 * the blast, where the bomb lands.
	 */
	public void dropBomb(ServerLevel level) {
		holder.getAnimator().playAnimation("drop_bomb", 10, true);
		var from = position().add(0, -DECK_DROP - 0.5, 0);
		var ground = BurningPatches.groundBelow(level, from);
		var bomb = new MagmaBomb(level, this);
		bomb.setPos(from.x, from.y, from.z);
		int warnTicks = NetherRiftsConfig.getInstance().mobs().bombardier().warnTicks();
		bomb.setDeltaMovement(0, MagmaBomb.launchSpeed(from.y - ground.y, warnTicks), 0);
		var rift = RiftMobs.riftOf(this).orElse(null);
		if (rift != null) {
			bomb.setAttached(RiftMobs.RIFT, rift);
		}
		level.addFreshEntity(bomb);
		BurningPatches.warn(level, ground, rift);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 1.0f, 1.6f);
	}

	/**
	 * The crew jump off where they stood: the Bombardier and two crossbow piglins, facing the way the ghast faced,
	 * gliding down (slow falling also spares them the fall) to fight on foot. A rift ghast's crew join its rift
	 * (its wave, its rules, their roles' loot); a summoned one's are plain mobs.
	 */
	@Override
	public void die(DamageSource source) {
		if (!isRemoved() && !dead && level() instanceof ServerLevel level) {
			holder.getAnimator().playAnimation("crew_gone", 20, true);
			for (var mob : crew(level)) {
				level.addFreshEntity(mob);
			}
		}
		super.die(source);
	}

	private List<Mob> crew(ServerLevel level) {
		var crew = new ArrayList<Mob>();
		var bombardier = RiftEntities.GHAST_BOMBARDIER.create(level, EntitySpawnReason.EVENT);
		if (bombardier != null) {
			crew.add(jumpOff(bombardier, BOMBARDIER_SPOT));
		}
		for (var spot : CROSSBOW_SPOTS) {
			var piglin = EntityTypes.PIGLIN.create(level, EntitySpawnReason.EVENT);
			if (piglin != null) {
				piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
				crew.add(jumpOff(piglin, spot));
			}
		}
		return crew;
	}

	private Mob jumpOff(Mob mob, Vec3 spot) {
		// the model faces -z with +x on its right, and is turned to the ghast's body yaw
		var at = position().add(new Vec3(-spot.x, spot.y, -spot.z).scale(1 / 16.0).yRot(-yBodyRot * Mth.DEG_TO_RAD));
		mob.snapTo(at.x, at.y, at.z, yBodyRot, 0);
		mob.setYBodyRot(yBodyRot);
		mob.setYHeadRot(yBodyRot);
		mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, CREW_SLOW_FALL_TICKS, 0));
		RiftMobs.riftOf(this).ifPresent(rift -> RiftMobs.enlist(mob, rift, Slot.BOMBARDIER));
		return mob;
	}

	/**
	 * The ghast moves as if its body reached down to its deck: floating about or pushed, it never drags the deck into
	 * the ground or a wall. (Its move control only checks the ghast's own box; the bombing run plans for the deck
	 * itself.) Stopped short like that, it stops moving that way, as a bump of its own box would, and gives up its float
	 * target for another: its move control would keep flying at a point only the ghast fits at, and an idle ghast would
	 * hover over the ground for good.
	 */
	@Override
	public void move(MoverType type, Vec3 delta) {
		if (!noPhysics) {
			var clamped = Entity.collideBoundingBox(this, delta, stackBox(), level(), List.of());
			if (!clamped.equals(delta)) {
				var v = getDeltaMovement();
				setDeltaMovement(clamped.x != delta.x ? 0 : v.x, clamped.y != delta.y ? 0 : v.y, clamped.z != delta.z ? 0 : v.z);
				// a bombing run sets its point again every tick, before the move control looks
				getMoveControl().setWait();
			}
			delta = clamped;
		}
		super.move(type, delta);
	}

	/**
	 * It flashes red while hurt ({@link RiftModels#hurtTint}). The crew are part of the ghast; riders from an old
	 * config's roster entry or a {@code /summon} with passengers
	 * would make a doubled crew, so they're sent away.
	 */
	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			RiftModels.hurtTint(this, holder);
			if (isVehicle()) {
				getPassengers().forEach(Entity::discard);
			}
		}
	}

	/** A war ghast always flies itself, whatever might be put on it. */
	@Nullable
	@Override
	public LivingEntity getControllingPassenger() {
		return null;
	}
}
