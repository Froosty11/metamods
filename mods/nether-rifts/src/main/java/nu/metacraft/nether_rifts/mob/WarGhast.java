package nu.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.core.holder.base.SimpleAnimatedHolder;
import de.tomalbrc.bil.core.holder.wrapper.DisplayWrapper;
import de.tomalbrc.bil.core.model.Pose;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A ghast in a war harness. Clients see a vanilla ghast wearing the harness model. It floats and shoots
 * as any ghast; carrying a Bombardier, it also makes bombing runs.
 */
public class WarGhast extends Ghast implements PolymerEntity {

	private final SimpleAnimatedHolder harness;

	public WarGhast(EntityType<? extends WarGhast> type, Level level) {
		super(type, level);
		var ghast = this;
		this.harness = new SimpleAnimatedHolder(RiftModels.GHAST_HARNESS) {
			@Override
			public void updateElement(@Nullable ServerPlayer player, DisplayWrapper<?> display, @Nullable Pose pose) {
				display.element().setYaw(ghast.yBodyRot);
				super.updateElement(player, display, pose);
			}
		};
		EntityAttachment.ofTicking(this.harness, this);
		this.harness.getAnimator().playAnimation("idle", 0, true);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.GHAST;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(4, new BombingRunGoal(this));
	}

	/** Rider glide time: enough to come down from a bombing run's climb and well beyond. */
	public static final int RIDER_SLOW_FALL_TICKS = 200;

	/** Unhorsed high up, the Bombardier glides down (slow falling also spares it the fall) and fights on foot. */
	@Override
	public void die(DamageSource source) {
		var rider = bombardier();
		if (rider != null) {
			rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, RIDER_SLOW_FALL_TICKS, 0));
		}
		super.die(source);
	}

	/** The Bombardier sits facing the way its ghast does (vanilla only turns the riders of pathfinding mounts). */
	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger instanceof GhastBombardier rider) {
			rider.setYRot(getYRot());
			rider.setYBodyRot(yBodyRot);
		}
	}

	@Nullable
	public GhastBombardier bombardier() {
		return getFirstPassenger() instanceof GhastBombardier rider ? rider : null;
	}
}
