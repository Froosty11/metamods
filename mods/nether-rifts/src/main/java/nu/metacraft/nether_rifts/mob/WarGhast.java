package nu.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.component.AnimationComponent;
import de.tomalbrc.bil.core.holder.wrapper.Bone;
import de.tomalbrc.bil.core.holder.wrapper.Locator;
import de.tomalbrc.bil.core.model.Node;
import de.tomalbrc.bil.core.model.Pose;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
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
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Chained Ghast: a ghast in a harness with a deck slung under it on chains, crewed by a Piglin Drummer, a Piglin
 * Grenadier and two piglins (one with a spear, one with a crossbow). One model (war_ghast.bbmodel, the artist's, brought
 * in by tools/import_ghast.py), crew and all. It floats and shoots as any ghast and makes bombing runs, the Grenadier
 * dropping magma bombs from the deck. When it dies the crew jump off as real mobs and glide down.
 *
 * <p>Its gear shows on the crew ({@link HeldItems}, {@link ArmourPieces}): the main hand in the spear piglin's right
 * hand, the off hand in the crossbow piglin's left, the head slot on the Grenadier's head (worn armour on the spear
 * piglin's instead), and chest, legs and feet armour on the spear piglin. Every Chained Ghast gets a golden spear, a
 * crossbow and the Grenadier Goggles, none of which drop. The armour is only for show: it gives the ghast no armour
 * points, toughness or enchantment effects. The crew take the gear with them when they jump.
 */
public class WarGhast extends Ghast implements AnimatedEntity {

	/** How far the deck's underside hangs below the ghast's feet: the model's lowest cube, 94 units under them. */
	public static final double DECK_DROP = 94 / 16.0;

	/** Crew glide time: enough to come down from a bombing run's climb and well beyond. */
	public static final int CREW_SLOW_FALL_TICKS = 200;

	/** The faces: one shows at a time. */
	public static final String FACE = "face_sad";
	public static final String SHOOTING_FACE = "face_shooting";

	/** The armour slots, worn for show only. */
	private static final List<EquipmentSlot> ARMOUR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	/**
	 * The figures on the deck: their group in the model, and the mob each becomes. The import marks where each stands
	 * ({@code <figure>_feet}) and which way it faces ({@code <figure>_facing}, a little ahead of its feet).
	 */
	public enum Crew {
		DRUMMER("drummer_piglin"),
		SPEAR("piglin1"),
		CROSSBOW("piglin2"),
		BOMBARDIER("bombardier");

		public final String figure;

		Crew(String figure) {
			this.figure = figure;
		}
	}

	/** Where a figure stands in the world, and the yaw it faces. */
	public record Spot(Vec3 feet, float yaw) {}

	private final HeldItems.Holder<WarGhast> holder;
	/** Each figure's bones, under its group. */
	private final Map<Crew, List<Bone<?>>> crewBones;
	private boolean crewGone = false;

	public WarGhast(EntityType<? extends WarGhast> type, Level level) {
		super(type, level);
		this.holder = new HeldItems.Holder<>(this, RiftModels.WAR_GHAST) {
			// the deck hangs below the box BIL culls the bones by: give them room down to its underside
			@Override
			protected void updateCullingBox() {
				float width = getScale() * dimensions.width() * 2;
				float height = -dimensions.height() - (float) DECK_DROP - 1;
				for (var bone : bones) {
					bone.element().setDisplaySize(width, height);
				}
			}

			// BIL shows every bone again whenever the ghast's flags change
			@Override
			protected void updateInvisibility(boolean invisible) {
				super.updateInvisibility(invisible);
				showParts();
			}
		};
		this.crewBones = new java.util.EnumMap<>(Crew.class);
		for (var crew : Crew.values()) {
			var bones = new ArrayList<Bone<?>>();
			for (var bone : holder.getBones()) {
				if (inside(bone.node(), crew.figure)) {
					bones.add(bone);
				}
			}
			crewBones.put(crew, bones);
		}
		EntityAttachment.ofTicking(this.holder, this);
		this.holder.getAnimator().playAnimation("idle", 0, true);
		showParts();
		keepKit();
	}

	@Override
	public HeldItems.Holder<WarGhast> getHolder() {
		return holder;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(4, new BombingRunGoal(this));
	}

	/** Whether a node is the model's group {@code name} or inside it. */
	private static boolean inside(@Nullable Node node, String name) {
		for (; node != null; node = node.parent()) {
			if (node.name().equals(name)) {
				return true;
			}
		}
		return false;
	}

	/** A figure's bones, armour and all. */
	public List<Bone<?>> crewBones(Crew crew) {
		return crewBones.get(crew);
	}

	/**
	 * Shows the face for what the ghast is doing and hides the crew once they've jumped. An invisible ghast keeps every
	 * bone hidden (BIL hides them).
	 */
	private void showParts() {
		if (isInvisible()) {
			return;
		}
		boolean shooting = isCharging();
		var face = holder.getBone(FACE);
		var shootingFace = holder.getBone(SHOOTING_FACE);
		if (face != null && shootingFace != null) {
			face.setInvisible(shooting);
			shootingFace.setInvisible(!shooting);
		}
		if (crewGone) {
			crewBones.values().forEach(bones -> bones.forEach(bone -> bone.setInvisible(true)));
		}
	}

	/** The ghast's shooting face, while vanilla's fireball goal charges a shot (half a second before it fires). */
	@Override
	public void setCharging(boolean charging) {
		super.setCharging(charging);
		showParts();
	}

	/**
	 * The kit every Chained Ghast has, in any slot left empty: the golden spear the rift's spear piglins carry, a
	 * crossbow and the Grenadier Goggles. Its gear never drops (the crew take it), and it's right-handed, so the main hand
	 * shows on the spear piglin. Set again on spawning and loading (a save or a summon without equipment has none).
	 */
	private void keepKit() {
		fill(EquipmentSlot.MAINHAND, Items.GOLDEN_SPEAR);
		fill(EquipmentSlot.OFFHAND, Items.CROSSBOW);
		fill(EquipmentSlot.HEAD, RiftItems.BOMBARDIER_HELMET);
		for (var slot : EquipmentSlot.values()) {
			setDropChance(slot, 0f);
		}
		setLeftHanded(false);
	}

	private void fill(EquipmentSlot slot, Item item) {
		if (getItemBySlot(slot).isEmpty()) {
			setItemSlot(slot, new ItemStack(item));
		}
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		var data = super.finalizeSpawn(level, difficulty, reason, groupData);
		keepKit();
		return data;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		keepKit();
	}

	/** Whether a slot of a mob is worn for show only: a Chained Ghast's armour. */
	public static boolean wornForShow(LivingEntity entity, EquipmentSlot slot) {
		return entity instanceof WarGhast && ARMOUR.contains(slot);
	}

	/**
	 * Armour put on gives the ghast nothing: its attribute modifiers (armour, toughness, knockback resistance) come
	 * straight off again. Its enchantments do nothing either (mixin/EnchantmentHelperMixin).
	 */
	@Override
	protected Map<EquipmentSlot, ItemStack> collectEquipmentChanges(Map<EquipmentSlot, ItemStack> last) {
		var changed = super.collectEquipmentChanges(last);
		if (changed != null) {
			changed.forEach((slot, stack) -> {
				if (ARMOUR.contains(slot)) {
					stack.forEachModifier(slot, (attribute, modifier) -> {
						var instance = getAttributes().getInstance(attribute);
						if (instance != null) {
							instance.removeModifier(modifier.id());
						}
					});
				}
			});
		}
		return changed;
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
	 * The Grenadier throws a magma bomb off the deck, straight down onto the ground warning, lobbed up first if need
	 * be: the warning shows at least the configured warning time (default {@link BurningPatches#WARN_TICKS}) before
	 * the blast, where the bomb lands. Plays the model's {@code drop_bomb} animation, if it has one.
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
	 * The crew jump off where they stand in the model, facing as they do, gliding down (slow falling also spares them
	 * the fall) to fight on foot: the drummer a Piglin Drummer, the Grenadier a Piglin Grenadier, the spear piglin with
	 * the ghast's main hand and armour, the crossbow piglin with its off hand. Their figures go from the model. A rift
	 * ghast's crew join its rift (its wave, its rules, their roles' loot); a summoned one's are plain mobs.
	 */
	@Override
	public void die(DamageSource source) {
		if (!isRemoved() && !dead && level() instanceof ServerLevel level) {
			for (var mob : crew(level)) {
				level.addFreshEntity(mob);
			}
			crewGone = true;
			showParts();
		}
		super.die(source);
	}

	private List<Mob> crew(ServerLevel level) {
		var crew = new ArrayList<Mob>();
		for (var figure : Crew.values()) {
			var mob = switch (figure) {
				case DRUMMER -> RiftEntities.WAR_DRUMMER.create(level, EntitySpawnReason.EVENT);
				case BOMBARDIER -> RiftEntities.GHAST_BOMBARDIER.create(level, EntitySpawnReason.EVENT);
				case SPEAR, CROSSBOW -> EntityTypes.PIGLIN.create(level, EntitySpawnReason.EVENT);
			};
			if (mob == null) {
				continue;
			}
			if (figure == Crew.SPEAR) {
				give(mob, EquipmentSlot.MAINHAND, take(EquipmentSlot.MAINHAND, Items.GOLDEN_SPEAR));
				for (var slot : ARMOUR) {
					// the goggles stay with the ghast's Grenadier, who has its own
					if (ArmourPieces.isWorn(getItemBySlot(slot), slot)) {
						give(mob, slot, take(slot, null));
					}
				}
			} else if (figure == Crew.CROSSBOW) {
				give(mob, EquipmentSlot.MAINHAND, take(EquipmentSlot.OFFHAND, Items.CROSSBOW));
			}
			crew.add(jumpOff(mob, spot(figure)));
		}
		// what the crew didn't take (the goggles: the Grenadier has its own) goes with their figures
		for (var slot : EquipmentSlot.values()) {
			setItemSlot(slot, ItemStack.EMPTY);
		}
		return crew;
	}

	/** Takes what's in a slot off the ghast; the fallback when it's empty. */
	private ItemStack take(EquipmentSlot slot, @Nullable Item fallback) {
		var stack = getItemBySlot(slot);
		setItemSlot(slot, ItemStack.EMPTY);
		return stack.isEmpty() && fallback != null ? new ItemStack(fallback) : stack;
	}

	private static void give(Mob mob, EquipmentSlot slot, ItemStack stack) {
		mob.setItemSlot(slot, stack);
		mob.setDropChance(slot, 0f);
	}

	private Mob jumpOff(Mob mob, Spot spot) {
		mob.snapTo(spot.feet().x, spot.feet().y, spot.feet().z, spot.yaw(), 0);
		mob.setYBodyRot(spot.yaw());
		mob.setYHeadRot(spot.yaw());
		mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, CREW_SLOW_FALL_TICKS, 0));
		RiftMobs.riftOf(this).ifPresent(rift -> RiftMobs.enlist(mob, rift, Slot.BOMBARDIER));
		return mob;
	}

	/**
	 * Where a figure stands now and the way it faces: its markers' places in the playing animation (else at rest),
	 * turned with the ghast's body as BIL turns the model. The model faces -z and BIL turns it half round, so the
	 * markers' +z is the ghast's front.
	 */
	public Spot spot(Crew crew) {
		var feet = markerPlace(crew.figure + "_feet");
		var ahead = markerPlace(crew.figure + "_facing").subtract(feet);
		float turn = (float) Math.toDegrees(Math.atan2(-ahead.x, ahead.z));
		var at = position().add(feet.scale(holder.getScale()).yRot(-yBodyRot * Mth.DEG_TO_RAD));
		return new Spot(at, Mth.wrapDegrees(yBodyRot + turn));
	}

	/** A marker's place in the model now, in blocks from the ghast's feet before the body's turn. */
	private Vec3 markerPlace(String name) {
		var model = holder.getModel();
		var node = HeldItems.marker(model, name);
		if (node == null) {
			throw new IllegalStateException("war_ghast.bbmodel has no " + name + ": import it with tools/import_ghast.py");
		}
		Pose rest = model.defaultPose().get(node.uuid());
		var found = ((AnimationComponent) holder.getAnimator()).findPose(null, Locator.of(node, rest));
		var t = (found != null && found.pose() != null ? found.pose() : rest).translation();
		return new Vec3(t.x, t.y, t.z);
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
