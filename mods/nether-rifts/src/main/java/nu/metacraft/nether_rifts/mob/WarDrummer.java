package nu.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.raid.RiftMobs;

/**
 * Leads every rift wave from wave 2. It keeps away from players and drums as it goes: a beat every 10 ticks;
 * every 4th is the big beat, for which it stands its ground, winds up (arms raised) and slams
 * {@link #BIG_BEAT_SLAM_TICK} ticks later. War Beat gives rift piglins within 12 blocks Speed I + Strength I for 6 s,
 * and Shock Beat knocks back players within 4 blocks. The beat is on a fixed tempo, so players can learn to hit
 * between beats. The numbers are the defaults of the config's {@code mobs.drummer} section.
 *
 * <p>The animations play in layers: BIL poses each bone by the highest-priority animation that moves it. The
 * {@code beat} (a 1 s loop of two strikes, so it lines up with the sound only at the default {@code beat_ticks})
 * moves the arms, head and body and leaves the legs to {@code walk} under it, so it drums as it goes;
 * {@code big_beat} (2.8 s, the slam at 1.8 s) moves every bone, and {@code idle} is under them all.
 */
public class WarDrummer extends Monster implements AnimatedEntity {

	/** Defaults for {@link NetherRiftsConfig.DrummerTuning}. */
	public static final int BEAT_TICKS = 10;
	public static final int BIG_BEAT_EVERY = 4;
	public static final double BUFF_RANGE = 12;
	public static final int BUFF_TICKS = 120;
	public static final double SHOCK_RANGE = 4;
	public static final double SHOCK_STRENGTH = 1.2;

	/** The big_beat animation's length, and the tick in it where both hands come down on the drum. */
	public static final int BIG_BEAT_TICKS = 56;
	public static final int BIG_BEAT_SLAM_TICK = 36;

	/** Moving faster than this (blocks a tick, across), it walks. */
	private static final double WALKING = 0.01;

	private final LivingEntityHolder<WarDrummer> holder;
	private boolean walking = false;
	private boolean drumming = false;
	/** Ticks into the big beat; -1 outside it. */
	private int bigBeatClock = -1;
	private int tempo = 0;
	private int beats = 0;

	public WarDrummer(EntityType<? extends WarDrummer> type, Level level) {
		super(type, level);
		this.holder = new LivingEntityHolder<>(this, RiftModels.WAR_DRUMMER);
		EntityAttachment.ofTicking(this.holder, this);
		this.holder.getAnimator().playAnimation("idle", 0, true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	public LivingEntityHolder<WarDrummer> getHolder() {
		return holder;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 6.0f, 1.0, 1.2,
				target -> target instanceof Player player && !player.isCreative() && !player.isSpectator()));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		var animator = holder.getAnimator();
		boolean moving = getDeltaMovement().horizontalDistanceSqr() > WALKING * WALKING;
		if (moving != walking) {
			walking = moving;
			if (moving) {
				animator.playAnimation("walk", 5, true);
			} else {
				animator.stopAnimation("walk");
			}
		}
		if (bigBeatClock >= 0) {
			getNavigation().stop();
			if (++bigBeatClock == BIG_BEAT_SLAM_TICK) {
				bigBeat(level);
			}
			if (bigBeatClock >= BIG_BEAT_TICKS) {
				// back on the tempo: the next beat comes on the next tick
				animator.stopAnimation("big_beat");
				bigBeatClock = -1;
				tempo = 0;
			}
			return;
		}
		var tuning = tuning();
		if (tempo++ % tuning.beatTicks() != 0) {
			return;
		}
		if (++beats % tuning.bigBeatEvery() == 0) {
			// it stands its ground for the big beat: the windup is the tell
			animator.stopAnimation("beat");
			drumming = false;
			animator.playAnimation("big_beat", 10, true);
			bigBeatClock = 0;
			return;
		}
		if (!drumming) {
			// the loop starts on a beat, so its strikes stay on the tempo
			animator.playAnimation("beat", 10, true);
			drumming = true;
		}
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.HOSTILE, 1.2f, 0.8f);
	}

	/** The big beat's slam: War Beat (buff rift piglins) and Shock Beat (push players back). */
	public void bigBeat(ServerLevel level) {
		var tuning = tuning();
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.HOSTILE, 2.0f, 0.6f);
		for (int i = 0; i < 24; i++) {
			float angle = i * Mth.TWO_PI / 24;
			level.sendParticles(ParticleTypes.FLAME, getX() + Mth.cos(angle) * 3, getY() + 0.2, getZ() + Mth.sin(angle) * 3, 1, 0, 0, 0, 0);
		}
		double buffRange = tuning.buffRange();
		for (var piglin : level.getEntitiesOfClass(AbstractPiglin.class, getBoundingBox().inflate(buffRange),
				p -> RiftMobs.isRiftMob(p) && p.distanceTo(this) <= buffRange)) {
			piglin.addEffect(new MobEffectInstance(MobEffects.SPEED, tuning.buffTicks(), 0));
			piglin.addEffect(new MobEffectInstance(MobEffects.STRENGTH, tuning.buffTicks(), 0));
		}
		double shockRange = tuning.shockRange();
		for (var player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(shockRange),
				p -> !p.isSpectator() && !p.isCreative() && p.distanceTo(this) <= shockRange)) {
			// knockback() pushes away from the (x, z) it is given and flags the player for a velocity sync
			player.knockback(tuning.shockStrength(), getX() - player.getX(), getZ() - player.getZ(), level.damageSources().mobAttack(this), 0f);
		}
	}

	private static NetherRiftsConfig.DrummerTuning tuning() {
		return NetherRiftsConfig.getInstance().mobs().drummer();
	}
}
