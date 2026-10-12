package nu.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.raid.RiftMobs;

/** A goggled piglin who rides a war ghast and drops magma bombs; unhorsed, it fights on foot. */
public class GhastBombardier extends Monster implements AnimatedEntity {

	private final LivingEntityHolder<GhastBombardier> holder;

	public GhastBombardier(EntityType<? extends GhastBombardier> type, Level level) {
		super(type, level);
		this.holder = new LivingEntityHolder<>(this, RiftModels.GHAST_BOMBARDIER);
		EntityAttachment.ofTicking(this.holder, this);
		this.holder.getAnimator().playAnimation("idle", 0, true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 4.0);
	}

	@Override
	public LivingEntityHolder<GhastBombardier> getHolder() {
		return holder;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		// riding, it leaves the moving to its ghast: walking goals would only make paths from the saddle
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {
			@Override
			public boolean canUse() {
				return !GhastBombardier.this.isPassenger() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !GhastBombardier.this.isPassenger() && super.canContinueToUse();
			}
		});
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
			@Override
			public boolean canUse() {
				return !GhastBombardier.this.isPassenger() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !GhastBombardier.this.isPassenger() && super.canContinueToUse();
			}
		});
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
		targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/**
	 * Lets a magma bomb go from under the rider, straight down onto the ground warning, lobbed up first if need
	 * be: the warning shows at least the configured warning time (default {@link BurningPatches#WARN_TICKS}) before
	 * the blast, where the bomb lands.
	 */
	public void dropBomb(ServerLevel level) {
		holder.getAnimator().playAnimation("drop_bomb", 10, true);
		var vehicle = getVehicle();
		var from = (vehicle != null ? vehicle.position() : position()).add(0, -0.5, 0);
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
}
