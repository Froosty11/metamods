import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.tomalbrc.bil.core.model.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.mob.BombingRunGoal;
import nu.metacraft.nether_rifts.mob.BurningPatches;
import nu.metacraft.nether_rifts.mob.GhastBombardier;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.WarGhast;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.RiftRaid;
import nu.metacraft.nether_rifts.raid.Slot;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BombardierTests {

	static void registerTests() {
		NetherRiftsTests.register("bombardier/crew_on_deck", ctx -> {
			// The crew are part of the war ghast's model: it carries no riders, and its model has the three figures.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.BOMBARDIER, ctx.absolutePos(new BlockPos(12, 8, 12)), Vec3.ZERO);
				ctx.assertTrue(spawned.size() == 1 && spawned.getFirst() instanceof WarGhast, "the Bombardier slot spawned " + spawned);
				var ghast = (WarGhast) spawned.getFirst();
				ctx.assertFalse(ghast.isVehicle(), "the war ghast carries " + ghast.getPassengers());
				ctx.assertTrue(RiftMobs.isRiftMob(ghast), "the war ghast isn't a rift mob");
				var bones = ghast.getHolder().getModel().nodeMap().values().stream().map(Node::name).collect(Collectors.toSet());
				for (var bone : List.of("bomber", "bomber_arm", "crossbow_left", "crossbow_left_arms", "crossbow_right", "crossbow_right_arms", "deck")) {
					ctx.assertTrue(bones.contains(bone), "the war ghast's model has no " + bone + ": " + bones);
				}
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/run_drops_bombs", ctx -> {
			stoneFloor(ctx);
			var bombs = new AtomicInteger();
			ctx.onEachTick(() -> bombs.addAndGet(ctx.getEntities(RiftEntities.MAGMA_BOMB).size()));
			FormingTests.lightFrame(ctx, 2, 3);
			var player = StageTests.player(ctx);
			ctx.runAtTickTime(3, () -> {
				var floor = ctx.absolutePos(new BlockPos(16, 1, 16));
				player.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
				var ghast = warGhast(ctx, new BlockPos(8, 10, 8));
				ghast.setTarget(player);
			});
			ctx.succeedWhen(() -> {
				ctx.assertTrue(bombs.get() > 0, "no bomb dropped yet");
				// a dropped bomb is not yet a blast: wait for one to land, or the floor check below proves nothing
				ctx.assertTrue(BurningPatches.active(ctx.getLevel()).stream().anyMatch(p -> !p.warning() && ctx.getBounds().contains(p.at())), "no bomb landed yet");
				for (int x = 0; x < 24; x++) {
					for (int z = 0; z < 24; z++) {
						ctx.assertBlockPresent(Blocks.STONE, new BlockPos(x, 0, z));
					}
				}
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
			});
		});
		NetherRiftsTests.register("bombardier/deck_clear_of_low_roof", ctx -> {
			// A roof 13 blocks over the target: room for the ghast and its deck to run at run height (the deck 4 above
			// the target), not to climb all the way. The deck never goes into the roof, the floor or anything else.
			stoneFloor(ctx);
			roof(ctx, 14);
			var buried = new AtomicInteger();
			var bombs = new AtomicInteger();
			ctx.onEachTick(() -> {
				bombs.addAndGet(ctx.getEntities(RiftEntities.MAGMA_BOMB).size());
				ctx.getEntities(RiftEntities.WAR_GHAST).forEach(ghast -> {
					if (!ctx.getLevel().noBlockCollision(ghast, ghast.stackBox())) {
						buried.incrementAndGet();
					}
				});
			});
			FormingTests.lightFrame(ctx, 2, 3);
			var player = StageTests.player(ctx);
			ctx.runAtTickTime(3, () -> {
				var floor = ctx.absolutePos(new BlockPos(16, 1, 16));
				player.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
				warGhast(ctx, new BlockPos(8, 6, 8)).setTarget(player);
			});
			ctx.runAtTickTime(300, () -> {
				ctx.assertTrue(buried.get() == 0, "the war ghast's deck was in blocks for " + buried.get() + " ticks");
				ctx.assertTrue(bombs.get() > 0, "no bombing run under the roof");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/idle_ghast_not_stuck_over_floor", ctx -> {
			// A float target just over the floor: room for the ghast alone, not for the deck hanging under it. The ghast
			// stops short of it, its deck clear of the floor, and floats off elsewhere, rather than hovering there for good.
			stoneFloor(ctx);
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 8, 12));
			// the ghast's feet a hair over the floor; it stops more than a block short (closer, vanilla would pick anew)
			var wanted = ghast.position().add(0, 1 - 8 + 0.05, 0);
			ghast.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, 1.0);
			var buried = new AtomicInteger();
			var gaveUp = new AtomicBoolean();
			ctx.onEachTick(() -> {
				ghast.setTarget(null);
				if (!ctx.getLevel().noBlockCollision(ghast, ghast.stackBox())) {
					buried.incrementAndGet();
				}
				var move = ghast.getMoveControl();
				if (!move.hasWanted() || move.getWantedY() != wanted.y) {
					gaveUp.set(true);
				}
			});
			ctx.runAtTickTime(100, () -> {
				ctx.assertTrue(gaveUp.get(), "the ghast still hovers over the floor, flying at a point its deck doesn't fit at");
				ctx.assertTrue(buried.get() == 0, "the war ghast's deck was in the floor for " + buried.get() + " ticks");
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/summoned_ghast_sheds_riders", ctx -> {
			// A /summon with an old-style Bombardier rider, no rift: the rider goes (the crew are the ghast's own), and the
			// ghast still flies itself.
			var at = Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(12, 6, 12)));
			var ghast = EntityType.loadEntityRecursive(nbt("{id:\"nether_rifts:war_ghast\",Passengers:[{id:\"nether_rifts:ghast_bombardier\"}]}"),
					ctx.getLevel(), new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), e -> {
						e.snapTo(at.x, at.y, at.z, 0, 0);
						return e;
					});
			ctx.assertTrue(ghast instanceof WarGhast && ghast.isVehicle(), "the summon didn't load a war ghast with a rider");
			ctx.getLevel().tryAddFreshEntityWithPassengers(ghast);
			ctx.runAfterDelay(3, () -> {
				ctx.assertFalse(ghast.isVehicle(), "the war ghast still carries " + ghast.getPassengers());
				ctx.assertTrue(ctx.getEntities(RiftEntities.GHAST_BOMBARDIER).isEmpty(), "the summoned rider is still about");
				ctx.assertFalse(RiftMobs.isRiftMob(ghast), "a summoned war ghast is a rift mob");
				ctx.assertTrue(((WarGhast) ghast).getControllingPassenger() == null, "something steers the war ghast");
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/crew_bail_out", ctx -> {
			// Review focus 2: the ghast's death puts its crew on the deck as real mobs, of its rift, gliding down unhurt.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var ghast = warGhast(ctx, new BlockPos(12, 9, 12));
				var deck = ghast.stackBox().setMaxY(ghast.getY());
				ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
				var crew = crew(ctx, deck);
				ctx.assertTrue(crew.size() == 3, "the ghast's death let off " + crew);
				ctx.assertTrue(crew.stream().filter(m -> m instanceof GhastBombardier).count() == 1, "no one Bombardier in " + crew);
				ctx.assertTrue(crew.stream().filter(m -> m instanceof Piglin p && p.getMainHandItem().is(Items.CROSSBOW)).count() == 2,
						"no two crossbow piglins in " + crew);
				for (var mob : crew) {
					ctx.assertTrue(mob.hasEffect(MobEffects.SLOW_FALLING), mob + " doesn't glide");
					ctx.assertTrue(rift.id().equals(mob.getAttached(RiftMobs.RIFT)), mob + " isn't of the ghast's rift");
					ctx.assertTrue(Mth.degreesDifferenceAbs(mob.getYRot(), ghast.yBodyRot) < 1, mob + " doesn't face the ghast's way");
				}
				// the ghast's 20-tick death, then a slow-falling glide down
				ctx.runAfterDelay(100, () -> {
					for (var mob : crew) {
						ctx.assertTrue(mob.isAlive(), mob + " died");
						ctx.assertTrue(mob.onGround(), mob + " hangs in the air");
						ctx.assertTrue(mob.getHealth() == mob.getMaxHealth(), mob + " was hurt coming down: " + mob.getHealth());
						ctx.assertTrue(RiftRaid.aliveMobs(ctx.getLevel(), rift).contains(mob), mob + " doesn't count toward the wave");
					}
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("bombardier/crew_lands_softly", ctx -> {
			// Bailing out high up, the crew glide down: a fall that would kill them doesn't.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var ghast = warGhast(ctx, new BlockPos(12, 12, 12));
				var deck = ghast.stackBox().setMaxY(ghast.getY());
				ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
				var crew = crew(ctx, deck);
				ctx.assertTrue(crew.size() == 3, "the ghast's death let off " + crew);
				// about 8 blocks up: 5 fall damage, so 4 health dies of an unbroken fall
				crew.forEach(mob -> mob.setHealth(4f));
				ctx.runAfterDelay(120, () -> {
					for (var mob : crew) {
						ctx.assertTrue(mob.isAlive(), mob + " died of the fall from its ghast");
						ctx.assertTrue(mob.onGround(), mob + " hangs in the air");
					}
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("bombardier/summoned_crew_plain", ctx -> {
			// A summoned war ghast (no rift) lets off a plain crew: no rift, no rift rules.
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 6, 12));
			var deck = ghast.stackBox().setMaxY(ghast.getY());
			ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
			var crew = crew(ctx, deck);
			ctx.assertTrue(crew.size() == 3, "the ghast's death let off " + crew);
			ctx.assertTrue(crew.stream().noneMatch(RiftMobs::isRiftMob), "a summoned ghast's crew joined a rift");
			ctx.killAllEntities();
			ctx.succeed();
		});
		NetherRiftsTests.register("bombardier/warning_leads_impact", ctx -> {
			// The ground warning shows a full second before the blast, where the bomb lands, even from a moving ghast.
			stoneFloor(ctx);
			long[] warnedAt = {-1};
			long[] burntAt = {-1};
			Vec3[] warning = new Vec3[1];
			Vec3[] burn = new Vec3[1];
			ctx.onEachTick(() -> {
				for (var patch : BurningPatches.active(ctx.getLevel())) {
					if (!ctx.getBounds().contains(patch.at())) {
						continue;
					}
					if (patch.warning() && warning[0] == null) {
						warning[0] = patch.at();
					} else if (!patch.warning() && burn[0] == null) {
						burn[0] = patch.at();
						burntAt[0] = ctx.getTick();
					}
				}
			});
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				// run height: the deck DIVE_HEIGHT above the floor
				var ghast = warGhast(ctx, new BlockPos(12, 1 + (int) Math.ceil(BombingRunGoal.DIVE_HEIGHT + WarGhast.DECK_DROP), 12));
				ghast.setNoAi(true);
				ghast.setDeltaMovement(0.4, 0, 0);
			});
			ctx.runAtTickTime(5, () -> {
				var ghast = ctx.getEntities(RiftEntities.WAR_GHAST).getFirst();
				// the warning goes up with the drop; per-tick checks would see it a tick late (they run before this)
				ghast.dropBomb(ctx.getLevel());
				warnedAt[0] = ctx.getTick();
			});
			ctx.succeedWhen(() -> {
				ctx.assertTrue(warning[0] != null, "no ground warning");
				ctx.assertTrue(burn[0] != null, "the bomb hasn't landed");
				ctx.assertTrue(burntAt[0] - warnedAt[0] >= BurningPatches.WARN_TICKS, "the warning led the blast by only " + (burntAt[0] - warnedAt[0]) + " ticks");
				double off = Math.hypot(burn[0].x - warning[0].x, burn[0].z - warning[0].z);
				ctx.assertTrue(off <= BurningPatches.RADIUS, "the bomb landed " + off + " blocks from its warning");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
			});
		});
		NetherRiftsTests.register("bombardier/joins_late_large_waves", ctx -> {
			// A 10x10 frame is Large; wave 3 heavies are plain ghasts, wave 5 heavies are war ghasts.
			var inside = ShapeTests.frame(ctx, 10, 10, Blocks.OBSIDIAN);
			ctx.setBlock(inside, Blocks.FIRE);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				rift.tear(ctx.getLevel());
				RiftRaid.startWave(ctx.getLevel(), rift, 3);
				var wave3 = RiftRaid.aliveMobs(ctx.getLevel(), rift);
				ctx.assertTrue(wave3.stream().anyMatch(e -> e.getType() == EntityTypes.GHAST), "no plain ghast in Large wave 3");
				ctx.assertFalse(wave3.stream().anyMatch(e -> e instanceof WarGhast), "a war ghast came in wave 3");
				wave3.forEach(e -> e.discard());
				RiftRaid.startWave(ctx.getLevel(), rift, 5);
				var wave5 = RiftRaid.aliveMobs(ctx.getLevel(), rift);
				ctx.assertTrue(wave5.stream().anyMatch(e -> e instanceof WarGhast w && !w.isVehicle()), "no crewed war ghast in Large wave 5");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
	}

	/** A war ghast of the room's rift (lit beforehand), its feet at {@code pos}. */
	static WarGhast warGhast(GameTestHelper ctx, BlockPos pos) {
		UUID rift = FormingTests.riftIn(ctx).id();
		var spawned = RiftMobs.spawn(ctx.getLevel(), rift, Slot.BOMBARDIER, ctx.absolutePos(pos), Vec3.ZERO);
		return (WarGhast) spawned.stream().filter(e -> e instanceof WarGhast).findFirst().orElseThrow();
	}

	/** The mobs standing in a war ghast's deck space (the box from its deck's underside to its feet) other than ghasts. */
	static List<Mob> crew(GameTestHelper ctx, net.minecraft.world.phys.AABB deck) {
		return ctx.getLevel().getEntitiesOfClass(Mob.class, deck, mob -> !(mob instanceof WarGhast));
	}

	/** Stone at {@code y} and {@code y + 1} across the room. */
	static void roof(GameTestHelper ctx, int y) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				ctx.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				ctx.setBlock(new BlockPos(x, y + 1, z), Blocks.STONE);
			}
		}
	}

	static net.minecraft.nbt.CompoundTag nbt(String snbt) {
		try {
			return TagParser.parseCompoundFully(snbt);
		} catch (CommandSyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The test room comes with no floor (the structure's stone row isn't placed; the encasing barrier is the ground), so lay one. */
	static void stoneFloor(GameTestHelper ctx) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				ctx.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}
}
