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
 * Leads every rift wave from wave 2. It keeps away from players and drums: a beat every 10 ticks; every
 * 4th is the big beat. War Beat gives rift piglins within 12 blocks Speed I + Strength I for 6 s, and
 * Shock Beat knocks back players within 4 blocks. The beat is on a fixed tempo, so players can learn to
 * hit between beats. The numbers are the defaults of the config's {@code mobs.drummer} section.
 */
public class WarDrummer extends Monster implements AnimatedEntity {

	/** Defaults for {@link NetherRiftsConfig.DrummerTuning}. */
	public static final int BEAT_TICKS = 10;
	public static final int BIG_BEAT_EVERY = 4;
	public static final double BUFF_RANGE = 12;
	public static final int BUFF_TICKS = 120;
	public static final double SHOCK_RANGE = 4;
	public static final double SHOCK_STRENGTH = 1.2;

	private final LivingEntityHolder<WarDrummer> holder;
	private int beatClock = 0;
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
		var tuning = tuning();
		if (++beatClock < tuning.beatTicks()) {
			return;
		}
		beatClock = 0;
		beats++;
		if (beats % tuning.bigBeatEvery() == 0) {
			bigBeat(level);
		} else {
			holder.getAnimator().playAnimation("beat", 10, true);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.HOSTILE, 1.2f, 0.8f);
		}
	}

	/** The 4th beat: War Beat (buff rift piglins) and Shock Beat (push players back). */
	public void bigBeat(ServerLevel level) {
		var tuning = tuning();
		holder.getAnimator().playAnimation("big_beat", 10, true);
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
