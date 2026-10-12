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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

	/**
	 * The Bombardier sits on top, above the ghast's own box, so the ghast moves as if its body reached up to the
	 * rider's head: floating about or pushed, it never presses the rider into a roof. (Its move control only checks
	 * the ghast's box; the bombing run plans for the rider itself.) Stopped short like that, it stops moving that
	 * way, as a bump of its own box would, and gives up its float target for another: its move control would keep
	 * flying at a point only the ghast fits at, and an idle ghast would hover under the roof for good.
	 */
	@Override
	public void move(MoverType type, Vec3 delta) {
		if (!noPhysics && isVehicle()) {
			var stack = getBoundingBox();
			for (var rider : getIndirectPassengers()) {
				stack = stack.minmax(rider.getBoundingBox());
			}
			var clamped = Entity.collideBoundingBox(this, delta, stack, level(), List.of());
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

	/** The Bombardier sits facing the way its ghast does (vanilla only turns the riders of pathfinding mounts). */
	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger instanceof GhastBombardier rider) {
			rider.setYRot(getYRot());
			rider.setYBodyRot(yBodyRot);
		}
	}

	/**
	 * A war ghast always flies itself, whoever rides it: rift ghasts are covered by MobMixin, and this covers a staff
	 * {@code /summon} with no rift (vanilla would hand control to the Bombardier, whose ground pathing can't fly it).
	 */
	@Nullable
	@Override
	public LivingEntity getControllingPassenger() {
		return null;
	}

	@Nullable
	public GhastBombardier bombardier() {
		return getFirstPassenger() instanceof GhastBombardier rider ? rider : null;
	}
}
