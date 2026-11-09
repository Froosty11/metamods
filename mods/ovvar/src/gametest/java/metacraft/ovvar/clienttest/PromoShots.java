package metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldBuilder;

import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.SpotPlacements;
import metacraft.ovvar.store.Wardrobe;
import metacraft.ovvar.store.Wardrobes;

import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Promotional screenshots of an ovve, taken by a real client against a real server — not a test.
 *
 * <p>It is off unless the environment names it, so an ordinary {@code runClientGameTest} is
 * untouched by it:
 *
 * <pre>OVVAR_PROMO=1 ./gradlew :mods:ovvar:runClientGameTest</pre>
 *
 * <p>The images land in {@code /tmp/ovvar-promo/shots} (or {@code $OVVAR_PROMO_OUT}), at 4K unless
 * {@code OVVAR_PROMO_HD} asks for 1080p. The world is an ordinary generated overworld on a fixed
 * seed, so a run can be repeated.
 *
 * <p>Two stages are cut into that terrain, both chosen by {@link #findStages}, which reads the
 * generator's noise (no chunk generation) over a quarter of a million blocks and scores every
 * candidate by <em>what stands west of it</em>: the figures face east into the morning sun, every
 * scene camera is east of them looking west, and so the backdrop of a shot is the western skyline.
 * The group's stage wants hills or peaks there; the cover model's wants water, and has to be far
 * enough away to be somewhere else entirely. The one camera that walks round to the west shoots in
 * the late afternoon, so the sun stays over the camera's shoulder.
 *
 * <p>The figures are {@code minecraft:mannequin}s, which render as players and so wear an ovve the
 * way a person does, and each one is given a different vanilla default skin through the
 * {@code minecraft:profile} component — a static texture patch, so the client never asks a session
 * server anything. Their ovves have no owner, so the design lives on the item
 * ({@code Looks.setSewn}) and no wardrobe store is involved. A design with a dozen patches is more
 * than the dye colour and the trim can carry, so the shots are only taken after {@code /ovvar reload}
 * has built the pack that holds those combinations and the client has applied it.
 *
 * <p>Four chapters are shown, and only those four: Data's cerise, IT's purple, the silicon-blue IT
 * and the black Media tailcoat. The two PolymITer chapters are another artist's reference textures,
 * kept for comparison and due to leave the mod, so nothing promotional may show them — see
 * {@link #SHOWN}. Six figures out of four chapters, then, the repeats told apart by their design,
 * their skin and whether the top is up or rolled down, and ordered so that no two neighbours are
 * the same colour.
 */
public final class PromoShots implements FabricClientGameTest {
	private static final boolean ENABLED = System.getenv("OVVAR_PROMO") != null;
	private static final Path OUT = Path.of(System.getenv().getOrDefault("OVVAR_PROMO_OUT", "/tmp/ovvar-promo/shots"));
	private static final int W = System.getenv("OVVAR_PROMO_HD") != null ? 1920 : 3840;
	private static final int H = W * 9 / 16;
	/** Screens lay themselves out for the window, so GUI shots are taken at a window this size. */
	private static final int GUI_W = 2560, GUI_H = 1440;

	/** Mid-morning: the sun is up and east, so a camera looking west has it over its shoulder. */
	private static final String MORNING = "3000";
	/** Just after sunrise, for the warm low light and the coloured sky the hero frames want. */
	private static final String GOLDEN = "900";
	/** Late afternoon: the sun is low in the west, for the camera that has walked round to that side. */
	private static final String AFTERNOON = "11000";

	/** How far from spawn a stage is looked for, and the grid the terrain is sampled on. */
	private static final int REACH = 512, STEP = 16;

	/**
	 * A label when this run is a shader run: Iris and Sodium are in the run directory's {@code mods}
	 * folder and {@code config/iris.properties} names a pack, so all that is wanted is a handful of
	 * frames of the group under it, named after the pack. Everything else is skipped, which keeps a
	 * run short enough to do one per pack.
	 */
	private static final @Nullable String SHADER = System.getenv("OVVAR_PROMO_SHADERS");

	/** The pack's zip, somewhere outside the run directory, since that directory is made fresh each run. */
	private static final @Nullable String SHADER_PACK = System.getenv("OVVAR_PROMO_SHADERPACK");

	/** Fixed, so the same two stages come back run after run. */
	private static final String SEED = System.getenv().getOrDefault("OVVAR_PROMO_SEED", "ovvar-promo");

	/** A place to stand the figures: its middle, and the height its lawn is cut at. */
	private static final class Stage {
		int x, y, z;
		String biome = "?", backdrop = "?";

		double x(double dx) { return x + 0.5 + dx; }

		double z(double dz) { return z + 0.5 + dz; }

		/** The height a figure's feet are at. */
		double ground() { return y + 1; }

		@Override public String toString() { return x + " " + y + " " + z + " (" + biome + ", west: " + backdrop + ")"; }
	}

	private final Stage hero = new Stage();
	private final Stage cover = new Stage();

	/**
	 * One figure of the group: its chapter, whether the top is up, how far it is turned off square,
	 * the vanilla default skin it wears, and its design. Every chapter is here once, no two designs
	 * are the same, and no two figures share a skin.
	 */
	private record Figure(Chapter chapter, boolean topUp, float turn, String skin, boolean slim, List<String> patches) { }

	/**
	 * Where each figure stands, as an offset from the stage's middle. Not a rank: two loose rows, so
	 * the oblique camera at the south-east corner sees them overlap in depth, while the straight-on
	 * camera still sees six whole garments.
	 */
	private static final double[][] SPOTS = {
			{ 2.15,  3.95},   // nearest the corner camera, and so the figure the hero frame is built on
			{ 0.25,  2.45},
			{ 1.55,  0.80},
			{-0.55, -0.70},
			{ 1.15, -2.30},
			{-0.85, -3.90},
	};

	/**
	 * The chapters a camera is ever pointed at. {@code Chapter.values()} also holds DATA_POLYMITER and
	 * IT_POLYMITER, which are reference textures kept for comparison and due to be removed; nothing
	 * promotional may show them.
	 */
	private static final List<Chapter> SHOWN = List.of(Chapter.DATA, Chapter.IT, Chapter.IT_KISEL, Chapter.MEDIA);

	private static final List<Figure> LINE_UP = List.of(
			// cerise, up
			new Figure(Chapter.DATA, true, -16, "ari", false, List.of(
					"front_top_left.itk", "front_top_right.nyckeln0x0", "front_low_left.data", "front_low_right.spiken",
					"back_big.it",
					"sleeve_out_top_r.slaggan", "sleeve_out_mid_r.jgs", "sleeve_out_top_l.ticket_to_my_heart",
					"shoulder_r.itk", "shoulder_l.in",
					"leg_out_top_r.ticket_to_my_heart", "leg_front_top_l.data", "seat.rivals")),
			// silicon blue, up — the biggest jump from the cerise beside it
			new Figure(Chapter.IT_KISEL, true, 11, "noor", true, List.of(
					"front_top_left.data", "front_top_right.maid", "front_low_left.tmeit", "front_low_right.itk",
					"back_big.in_gold",
					"sleeve_out_top_r.it", "sleeve_out_mid_l.spiken", "sleeve_front_top_l.nyckeln0x1",
					"shoulder_l.itk", "shoulder_r.slaggan",
					"leg_out_top_l.slaggan", "leg_front_top_r.ticket_to_my_heart", "seat.pung")),
			// purple, rolled down: the top hangs at the waist and the legs carry the whole design
			new Figure(Chapter.IT, false, -23, "efe", false, List.of(
					"leg_out_top_r.itk", "leg_out_mid_r.kommn", "leg_out_top_l.data", "leg_out_mid_l.spiken",
					"leg_front_top_r.it", "leg_front_top_l.maid",
					"leg_front_mid_r.tmeit-marshal", "leg_front_mid_l.ticket_to_my_heart", "seat.rivals")),
			// black tailcoat, always up
			new Figure(Chapter.MEDIA, true, 18, "sunny", true, List.of(
					"front_top_left.maid", "front_top_right.itk", "front_low_left.spiken", "front_low_right.slaggan",
					"back_big.data",
					"sleeve_out_top_r.nyckeln0x2", "sleeve_out_top_l.it", "sleeve_out_mid_r.ticket_to_my_heart",
					"shoulder_r.in",
					"leg_out_top_r.data", "leg_out_top_l.it", "seat.pung")),
			// cerise, rolled down
			new Figure(Chapter.DATA, false, -9, "steve", false, List.of(
					"leg_out_top_r.spiken", "leg_out_mid_r.maid", "leg_out_top_l.itk", "leg_out_mid_l.jgs",
					"leg_front_top_r.ticket_to_my_heart", "leg_front_top_l.data", "leg_front_mid_l.in_gold",
					"seat.rivals")),
			// purple, up
			new Figure(Chapter.IT, true, 24, "zuri", true, List.of(
					"front_top_left.it", "front_top_right.data", "front_low_left.slaggan", "front_low_right.tmeit",
					"back_big.maid", "back_top_right.itk",
					"sleeve_out_top_r.maid", "sleeve_out_mid_r.kommn", "sleeve_out_top_l.spiken",
					"shoulder_r.itk", "shoulder_l.nyckeln0x0",
					"leg_out_top_l.it", "seat.pung")));

	/**
	 * The figures that show what real armour does to an ovve: one wearing nothing over it, so the
	 * ovve's own top is on show, and three in chest armour, which takes that slot and hides the top
	 * while the legs and the seat patch carry on below. They stand south of the group, behind every
	 * camera that shoots it.
	 */
	private record Armoured(Chapter chapter, String skin, boolean slim, Item chest, Item head, Item feet, boolean trimmed) { }

	private static final List<Armoured> ARMOUR = List.of(
			new Armoured(Chapter.DATA, "alex", true, null, null, null, false),
			new Armoured(Chapter.IT, "kai", false, Items.NETHERITE_CHESTPLATE, null, null, false),
			new Armoured(Chapter.IT_KISEL, "makena", false,
					Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS, true),
			new Armoured(Chapter.MEDIA, "steve", false, Items.ELYTRA, null, null, false));

	/** Leg-heavy, so the half the armour cannot cover is the half that carries the art. */
	private static final List<String> ARMOUR_DESIGN = List.of(
			"front_top_left.itk", "front_top_right.nyckeln0x1", "front_low_left.it", "front_low_right.data",
			"back_big.maid",
			"sleeve_out_top_r.spiken", "sleeve_out_top_l.slaggan", "shoulder_r.itk",
			"leg_out_top_r.data", "leg_out_mid_r.it", "leg_out_top_l.spiken", "leg_out_mid_l.tmeit-marshal",
			"leg_front_top_r.slaggan", "leg_front_top_l.maid", "seat.pung");

	/** Where the {@code i}th armoured figure stands, as a z offset from the stage's middle. */
	private static double armourZ(int i) { return 10.0 + i * 1.75; }

	/** The cover model, alone on its own stage: every side of it carries something, since it is shot from both. */
	private static final Figure COVER = new Figure(Chapter.DATA, true, 0, "makena", false, List.of(
			"front_top_left.itk", "front_top_right.nyckeln0x2", "front_low_left.it", "front_low_right.data",
			"back_big.maid",
			"sleeve_out_top_r.spiken", "sleeve_out_mid_r.slaggan", "sleeve_out_top_l.it", "sleeve_out_mid_l.in_gold",
			"sleeve_front_top_r.ticket_to_my_heart", "sleeve_back_top_l.in",
			"shoulder_r.itk", "shoulder_l.data",
			"leg_out_top_r.slaggan", "leg_out_mid_r.it", "leg_out_top_l.spiken", "leg_out_mid_l.jgs",
			"leg_front_top_r.data", "leg_front_top_l.tmeit",
			// No leg_back_top_* here: the seat patch covers both of those cells.
			"seat.pung"));

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!ENABLED) return;   // an ordinary test run does nothing here

		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25601");   // 25599 is the screenshot test's, 25565/6 the comparison servers'
		props.setProperty("level-seed", SEED);
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		props.setProperty("view-distance", "16");
		props.setProperty("gamemode", "spectator");

		// The builder's "consistent settings" make a seeded superflat world with no structures — the
		// void horizon these pictures exist to avoid. The preset, the seed and the structures are put
		// back to an ordinary overworld here; the seed is fixed, so a run can be repeated exactly.
		TestWorldBuilder builder = ctx.worldBuilder().adjustSettings(creator -> {
			Holder<WorldPreset> normal = creator.getSettings().worldgenLoadContext()
					.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL);
			creator.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
			creator.setSeed(SEED);
			creator.setGenerateStructures(true);
		});

		try (TestDedicatedServerContext server = builder.createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				settleThePack(ctx);
				conn.waitForChunksRender();

				server.runCommand("gamerule advance_time false");
				server.runCommand("gamerule advance_weather false");
				server.runCommand("gamerule spectators_generate_chunks true");
				server.runCommand("gamerule send_command_feedback false");
				server.runCommand("weather clear 1000000");
				server.runCommand("time set " + MORNING);
				server.runCommand("gamemode spectator Tester");

				findStages(server);
				lookGood(ctx);

				prepare(ctx, server, hero, 17, 24, true);
				prepare(ctx, server, cover, 9, 9, false);

				dressTheFigures(server);
				conn.waitForClientboundPackets();

				// The server only learns a combination is wanted when a client is actually shown the
				// garment, so both stages are looked at before the pack is asked for, and asked for
				// again afterwards: run 7 shot its first five frames against a pack holding two of
				// the twelve combinations, and those figures came out all but bare.
				settle(ctx, server, cover, 6.0, 5.0);
				soakThePack(ctx, server, 2);
				settle(ctx, server, hero, 9.0, 9.0);
				soakThePack(ctx, server, 4);
				ctx.waitTicks(80);   // the mannequins' spawn interpolation, and the join toast

				hud(ctx, false);
				if (SHADER != null) {
					try {
						selectTheShaderPack(ctx);
					} catch (Exception e) {
						throw new AssertionError("could not put the shader pack on: is Iris loaded?", e);
					}
					shaderShots(ctx, server, conn);
					return;
				}
				heroShots(ctx, server, conn);
				closeUps(ctx, server);

				server.runCommand("time set " + AFTERNOON);
				ctx.waitTicks(20);
				backShots(ctx, server);

				coverShots(ctx, server, conn);

				server.runCommand("time set " + MORNING);
				settle(ctx, server, hero, 9.0, 9.0);
				armourShots(ctx, server);
				playerShots(ctx, server, conn);
				itemFrameShot(ctx, server);

				hud(ctx, true);
				guiShots(ctx, server, conn);
			}
		}
	}

	// ---- finding the stages

	/** Ground a lawn is worth cutting into: not sand, not ice, not a swamp, not under a jungle. */
	private static final Set<String> GOOD_GROUND = Set.of(
			"meadow", "cherry_grove", "plains", "sunflower_plains", "forest", "flower_forest", "birch_forest",
			"old_growth_birch_forest", "grove", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga",
			"dark_forest", "savanna", "savanna_plateau", "windswept_forest", "windswept_hills",
			"windswept_gravelly_hills", "pale_garden");

	/** Something worth having on the skyline. */
	private static final Set<String> PEAKS = Set.of(
			"jagged_peaks", "frozen_peaks", "stony_peaks", "snowy_slopes", "windswept_hills",
			"windswept_gravelly_hills", "windswept_forest", "meadow", "grove");

	/**
	 * Two stages, from the generator's noise alone. Heights and biomes are sampled on a coarse grid
	 * over a square half a kilometre across, and every point on that grid is scored as a stage: the
	 * ground it stands on, the mean height of the land 64–192 blocks west of it (that is the backdrop
	 * a west-facing camera gets), a bonus for peaks or for water in that window depending on which
	 * stage is being looked for, and a heavy penalty for anything within 48 blocks west tall enough
	 * to be a wall behind the figures' heads.
	 */
	private void findStages(TestDedicatedServerContext server) {
		int n = 2 * REACH / STEP + 1;
		int[] heights = new int[n * n];
		String[] biomes = new String[n * n];
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			var generator = level.getChunkSource().getGenerator();
			var random = level.getChunkSource().randomState();
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					int x = -REACH + i * STEP, z = -REACH + j * STEP;
					int h = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
					heights[i * n + j] = h;
					// Quart coordinates, and uncached: noise only, no chunk is generated for this.
					biomes[i * n + j] = level.getUncachedNoiseBiome(x >> 2, h >> 2, z >> 2)
							.unwrapKey().<String>map(key -> key.identifier().getPath()).orElse("?");
				}
			}
		});

		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
		for (int k = 0; k < n * n; k++) {
			lo = Math.min(lo, heights[k]);
			hi = Math.max(hi, heights[k]);
			tally.merge(String.valueOf(biomes[k]), 1, Integer::sum);
		}
		System.out.println("[ovvar-promo] terrain " + lo + ".." + hi + " over " + (n * n) + " samples; biomes " + tally);

		pick(hero, heights, biomes, n, true, null);
		pick(cover, heights, biomes, n, false, hero);
		System.out.println("[ovvar-promo] group stage " + hero);
		System.out.println("[ovvar-promo] cover stage " + cover);
	}

	/**
	 * Scores every grid point and writes the winner into {@code into}. The filters are relaxed a step
	 * at a time rather than failing: a seed whose neighbourhood is all swamp still has to yield a
	 * stage, and a bad one is better than the origin.
	 */
	private void pick(Stage into, int[] heights, String[] biomes, int n, boolean wantPeaks, Stage away) {
		for (int relax = 0; relax < 3; relax++) {
			if (tryPick(into, heights, biomes, n, wantPeaks, away, relax)) return;
			System.out.println("[ovvar-promo] no stage at relax=" + relax + ", loosening");
		}
	}

	private boolean tryPick(Stage into, int[] heights, String[] biomes, int n, boolean wantPeaks, Stage away, int relax) {
		double bestScore = -1e9;
		int bestI = n / 2, bestJ = n / 2;
		String bestBackdrop = "?";
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int h = heights[i * n + j];
				if (h < 63) continue;                                   // never under water
				if (relax < 2 && h > 140) continue;                     // nor a spire with nothing above it
				if (relax < 1 && !GOOD_GROUND.contains(String.valueOf(biomes[i * n + j]))) continue;
				if (away != null) {
					int x = -REACH + i * STEP, z = -REACH + j * STEP;
					if (Math.abs(x - away.x) < 192 && Math.abs(z - away.z) < 192) continue;
				}

				double sum = 0;
				int seen = 0, peaks = 0, water = 0, blocked = 0;
				String backdrop = "?";
				for (int di = -12; di <= -4; di++) {          // 64…192 blocks west
					for (int dj = -6; dj <= 6; dj++) {        // ±96 blocks across the view
						int ii = i + di, jj = j + dj;
						if (ii < 0 || jj < 0 || ii >= n || jj >= n) continue;
						int hh = heights[ii * n + jj];
						sum += hh;
						seen++;
						if (PEAKS.contains(String.valueOf(biomes[ii * n + jj]))) { peaks++; if (dj == 0) backdrop = biomes[ii * n + jj]; }
						if (hh <= 62) { water++; if (dj == 0 && backdrop.equals("?")) backdrop = "water"; }
					}
				}
				if (seen < 80) continue;
				for (int di = -3; di <= -1; di++) {           // 16…48 blocks west: the view runs through it
					for (int dj = -2; dj <= 2; dj++) {
						int ii = i + di, jj = j + dj;
						if (ii < 0 || jj < 0 || ii >= n || jj >= n) continue;
						if (heights[ii * n + jj] > h + 4) blocked++;
					}
				}

				double relief = sum / seen - h;
				double score = wantPeaks
						? relief + 0.35 * peaks - 0.10 * water
						: 0.45 * water + 0.3 * relief + 0.15 * peaks;
				score += -8.0 * blocked + 0.12 * h;
				if (score > bestScore) { bestScore = score; bestI = i; bestJ = j; bestBackdrop = backdrop; }
			}
		}
		if (bestScore <= -1e8) return false;
		into.x = -REACH + bestI * STEP;
		into.z = -REACH + bestJ * STEP;
		into.y = Math.max(heights[bestI * n + bestJ], 64);
		into.biome = biomes[bestI * n + bestJ];
		into.backdrop = bestBackdrop + " (score " + String.format(Locale.ROOT, "%.1f", bestScore) + ", relax " + relax + ")";
		return true;
	}

	// ---- building a stage

	/**
	 * Loads a stage's chunks from the camera's own side, cuts its lawn, and lets it settle. The
	 * chunks are waited for by the clock rather than by {@code waitForChunksRender}, whose ten-second
	 * limit a spectator dropped into unexplored terrain at this render distance does not meet.
	 */
	private void prepare(ClientGameTestContext ctx, TestDedicatedServerContext server, Stage stage, int deep, int wide, boolean furniture) {
		camera(server, stage.x(9.0), stage.y + 3.0, stage.z(0.0), 100, 0);
		ctx.waitTicks(200);
		clearTheView(server, stage);
		buildTheStage(server, stage, deep, wide, furniture);
		ctx.waitTicks(60);
	}

	/**
	 * Fells the wood standing between the stage and its skyline. The stage is usually cut into a
	 * forest, and a wall of spruce forty blocks west is what the frame gets instead of the hills the
	 * stage was chosen for; taking the logs and the leaves out (and nothing else) opens the view
	 * without touching the land's own shape. The volume is split because {@code /fill} takes 32768
	 * blocks at a time.
	 */
	private void clearTheView(TestDedicatedServerContext server, Stage stage) {
		int f = stage.y, x = stage.x, z = stage.z;
		for (int half = 0; half < 2; half++) {
			int z0 = half == 0 ? z - 30 : z + 1, z1 = half == 0 ? z : z + 30;
			for (String what : new String[]{"#minecraft:leaves", "#minecraft:logs"}) {
				fill(server, x - 52, f + 1, z0, x - 11, f + 26, z1, "minecraft:air", what);
				fill(server, x + 21, f + 1, z0, x + 52, f + 26, z1, "minecraft:air", what);
			}
		}
	}

	/** Puts the camera back on a stage and gives its chunks, the distant skyline included, time to come in. */
	private void settle(ClientGameTestContext ctx, TestDedicatedServerContext server, Stage stage, double east, double south) {
		camera(server, stage.x(east), stage.ground() + 1.3, stage.z(south), 100, 0);
		ctx.waitTicks(160);
	}

	private void buildTheStage(TestDedicatedServerContext server, Stage stage, int deep, int wide, boolean furniture) {
		int f = stage.y, x = stage.x, z = stage.z;
		// A level lawn cut into the hillside: grass on top, stone filling whatever hollow is under it,
		// and clear air above so no tree or crag of the generator's stands in the picture.
		fill(server, x - 10, f, z - deep, x + 20, f, z + wide, "minecraft:grass_block");
		fill(server, x - 10, f + 1, z - deep, x + 20, f + 20, z + wide, "minecraft:air");
		fill(server, x - 10, f - 6, z - deep, x + 20, f - 1, z + wide, "minecraft:stone", "air");
		// A shallow apron off the west edge, so the lawn does not end in a cliff of stone under the
		// figures' feet where the hillside falls away.
		fill(server, x - 14, f - 1, z - deep - 2, x - 11, f - 1, z + wide + 2, "minecraft:grass_block", "air");
		fill(server, x - 14, f, z - deep - 2, x - 11, f + 20, z + wide + 2, "minecraft:air");
		// The path the figures stand on.
		fill(server, x - 1, f, z - deep + 3, x + 2, f, z + wide - 2, "minecraft:dirt_path");
		// Flowers and grass west of the figures, where they read as meadow and never as something in
		// front of one: the camera's own side of the path is left bare.
		for (int zz = z - deep + 1; zz <= z + wide - 1; zz++) {
			if ((zz + 100) % 3 == 0) set(server, x - 3, f + 1, zz, flower(zz));
			if ((zz + 101) % 3 == 0) set(server, x - 5, f + 1, zz, "minecraft:short_grass");
			if ((zz + 102) % 4 == 0) set(server, x - 7, f + 1, zz, flower(zz + 2));
			if ((zz + 103) % 3 == 0) set(server, x - 9, f + 1, zz, "minecraft:short_grass");
		}
		// The cover model's stage gets none of this: it is shot to be cut out, and wants nothing but
		// lawn and sky behind it.
		if (!furniture) return;
		// Lanterns on fences, west of the figures and past the ends of the group, for warm points of
		// light that are never behind a head.
		for (int zz : new int[]{z - deep + 3, z + wide - 3}) {
			set(server, x - 4, f + 1, zz, "minecraft:oak_fence");
			set(server, x - 4, f + 2, zz, "minecraft:oak_fence");
			set(server, x - 4, f + 3, zz, "minecraft:lantern[hanging=false]");
		}
		// Both cherry trees south of the group. The oblique cameras all stand south of it and look
		// north-west, so a tree planted at the north end lands square behind somebody's head — which
		// is where run 8's canopy hung. Down here they are behind the lens, and the straight-on camera
		// still gets them at the edge of its frame.
		tree(server, x - 8, f, z + wide - 2);
		tree(server, x - 9, f, z + wide - 8);
	}

	private static String flower(int z) {
		String[] flowers = {"minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:azure_bluet", "minecraft:poppy"};
		return flowers[Math.floorMod(z, flowers.length)];
	}

	private void tree(TestDedicatedServerContext server, int x, int f, int z) {
		fill(server, x, f + 1, z, x, f + 6, z, "minecraft:cherry_log");
		fill(server, x - 2, f + 5, z - 2, x + 2, f + 7, z + 2, "minecraft:cherry_leaves[persistent=true]");
		fill(server, x - 1, f + 8, z - 1, x + 1, f + 8, z + 1, "minecraft:cherry_leaves[persistent=true]");
		fill(server, x, f + 1, z, x, f + 6, z, "minecraft:cherry_log");
	}

	private static void fill(TestDedicatedServerContext server, int x0, int y0, int z0, int x1, int y1, int z1, String block) {
		server.runCommand("fill " + x0 + " " + y0 + " " + z0 + " " + x1 + " " + y1 + " " + z1 + " " + block);
	}

	private static void fill(TestDedicatedServerContext server, int x0, int y0, int z0, int x1, int y1, int z1, String block, String replacing) {
		server.runCommand("fill " + x0 + " " + y0 + " " + z0 + " " + x1 + " " + y1 + " " + z1 + " " + block + " replace " + replacing);
	}

	private static void set(TestDedicatedServerContext server, int x, int y, int z, String block) {
		server.runCommand("setblock " + x + " " + y + " " + z + " " + block);
	}

	// ---- the figures

	private double figureX(int i) { return hero.x(SPOTS[i][0]); }

	private double figureZ(int i) { return hero.z(SPOTS[i][1]); }

	/** An ovve of {@code figure}'s chapter with its design sewn on it; no owner, so it lives on the item. */
	private static ItemStack ovveOf(Figure figure) {
		ItemStack ovve = new ItemStack(ModContent.ovve(figure.chapter()));
		OvveItem.setTopUp(ovve, figure.topUp());
		List<Placement> placements = new ArrayList<>();
		for (String key : figure.patches()) placements.add(Placement.parse(key));
		Looks.setSewn(ovve, SpotPlacements.fromList(placements).getOrThrow());
		return ovve;
	}

	/** Dresses a mannequin at {@code (x, z)}, facing east and turned {@code turn} degrees off it. */
	private void place(ServerLevel level, Figure figure, double x, double y, double z) {
		place(level, figure, x, y, z, null);
	}

	/**
	 * {@code armour} is put on instead of the ovve's own companion top, which is what a player putting
	 * a chestplate on does: the top lives in the chest slot, so real armour takes that slot. It is
	 * done here, at spawn, and not by {@code /item replace} afterwards — run 11 tried that and the
	 * command found no mannequin to replace anything on.
	 */
	private void place(ServerLevel level, Figure figure, double x, double y, double z, @Nullable Armoured armour) {
		ItemStack ovve = ovveOf(figure);
		Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, level);
		m.setPos(x, y, z);
		float yaw = -90 + figure.turn();
		m.setYRot(yaw);
		m.setYBodyRot(yaw);
		m.setYHeadRot(yaw);
		m.setItemSlot(EquipmentSlot.LEGS, ovve);
		if (armour != null && armour.chest() != null) {
			m.setItemSlot(EquipmentSlot.CHEST, armourPiece(level, armour.chest(), armour.trimmed()));
			if (armour.head() != null) m.setItemSlot(EquipmentSlot.HEAD, armourPiece(level, armour.head(), armour.trimmed()));
			if (armour.feet() != null) m.setItemSlot(EquipmentSlot.FEET, armourPiece(level, armour.feet(), armour.trimmed()));
		} else if (OvveItem.topUp(ovve)) {
			ItemStack top = new ItemStack(ModContent.top(figure.chapter()));
			top.set(ModComponents.PATCHES, ovve.get(ModComponents.PATCHES));
			m.setItemSlot(EquipmentSlot.CHEST, top);
		}
		wearSkin(m, figure.skin(), figure.slim());
		level.addFreshEntity(m);
	}

	/**
	 * One of the eighteen skins the client ships with, named outright. The profile is given a texture
	 * patch rather than a name to look up, so it resolves to a static profile and no session server
	 * is ever asked; the patch is the only public way to reach {@code ResolvableProfile.Static} with a
	 * skin on it, which is why it is built through the codec rather than a constructor.
	 */
	private static void wearSkin(Mannequin m, String skin, boolean slim) {
		CompoundTag tag = new CompoundTag();
		tag.putString("name", skin);
		tag.putString("texture", "minecraft:entity/player/" + (slim ? "slim/" : "wide/") + skin);
		tag.putString("model", slim ? "slim" : "wide");
		ResolvableProfile profile = ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		m.setComponent(DataComponents.PROFILE, profile);
	}

	/** A piece of vanilla armour, optionally with a gold sentry trim on it. */
	private static ItemStack armourPiece(ServerLevel level, Item item, boolean trimmed) {
		ItemStack stack = new ItemStack(item);
		if (!trimmed) return stack;
		var registries = level.registryAccess();
		stack.set(DataComponents.TRIM, new ArmorTrim(
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.GOLD),
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)));
		return stack;
	}

	private void dressTheFigures(TestDedicatedServerContext server) {
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			for (int i = 0; i < LINE_UP.size(); i++) place(level, LINE_UP.get(i), figureX(i), hero.ground(), figureZ(i));
			place(level, COVER, cover.x(0.0), cover.ground(), cover.z(0.0));
			double[] nudge = {0.35, -0.2, 0.3, -0.25};
			for (int i = 0; i < ARMOUR.size(); i++) {
				Armoured a = ARMOUR.get(i);
				place(level, new Figure(a.chapter(), true, i % 2 == 0 ? -12 : 14, a.skin(), a.slim(), ARMOUR_DESIGN),
						hero.x(nudge[i]), hero.ground(), hero.z(armourZ(i)), a);
			}
		});
	}

	// ---- the pack

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void settleThePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 120);
		ctx.waitTicks(20);
	}

	/**
	 * A dozen patches on a half is far more than the dye colour and the trim can carry, so the
	 * pack has to hold every one of these combinations before the figures are worth photographing.
	 * {@code /ovvar reload} builds what is pending and pushes it, and a round waits before it asks:
	 * a combination is only queued once the client has been shown the garment, so a reload issued
	 * the instant the camera arrives sends a pack built from what the last frame happened to hold.
	 */
	private static void soakThePack(ClientGameTestContext ctx, TestDedicatedServerContext server, int rounds) {
		for (int round = 0; round < rounds; round++) {
			ctx.waitTicks(120);   // let the client render what is in front of it and the server notice
			server.runCommand("execute as Tester run ovvar reload");
			ctx.waitTicks(60);
			settleThePack(ctx);
		}
		ctx.waitTicks(40);
	}

	// ---- the camera

	/** {@code y} is the eye height wanted; the player's own feet go 1.62 below it. */
	private static void camera(TestDedicatedServerContext server, double x, double y, double z, double yaw, double pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp Tester %.3f %.3f %.3f %.2f %.2f", x, y - 1.62, z, yaw, pitch));
	}

	/** Points the camera at {@code (tx, ty, tz)} from {@code (x, y, z)}, both given as eye heights. */
	private static void lookFrom(TestDedicatedServerContext server, double x, double y, double z, double tx, double ty, double tz) {
		double dx = tx - x, dy = ty - y, dz = tz - z;
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double pitch = Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		camera(server, x, y, z, yaw, pitch);
	}

	/**
	 * The vertical field of view. Thirty is the floor: the option's own range starts there, and a
	 * smaller number is not clamped but thrown away — run 8's "telephoto" frame asked for 26 and was
	 * shot at the default seventy, with six figures the size of thumbnails.
	 */
	private static void fov(ClientGameTestContext ctx, int degrees) {
		int wanted = Math.max(30, Math.min(110, degrees));
		ctx.runOnClient(client -> client.options.fov().set(wanted));
		ctx.waitTicks(2);
	}

	/** Fancy graphics, a long view, and a field of view narrow enough not to bend the group. */
	private static void lookGood(ClientGameTestContext ctx) {
		ctx.runOnClient(client -> {
			client.options.graphicsPreset().set(GraphicsPreset.FANCY);
			GraphicsPreset.FANCY.apply(client);
			client.options.renderDistance().set(16);
			client.options.entityDistanceScaling().set(4.0);
			client.options.entityShadows().set(true);
			client.options.ambientOcclusion().set(true);
			client.options.biomeBlendRadius().set(5);
			client.options.mipmapLevels().set(4);
			client.options.gamma().set(0.9);   // the low sun is warm but dim; keep the cloth readable
			client.options.fov().set(40);
			client.options.bobView().set(false);
			client.options.screenEffectScale().set(0.0);
			client.options.fovEffectScale().set(0.0);
			client.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}

	private static void hud(ClientGameTestContext ctx, boolean shown) {
		boolean[] hidden = new boolean[1];
		ctx.runOnClient(client -> hidden[0] = client.gui.hud.isHidden());
		if (hidden[0] == !shown) return;
		ctx.getInput().pressKey(options -> options.keyToggleGui);
		ctx.waitTicks(2);
	}

	private Path shot(ClientGameTestContext ctx, String name) {
		return ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(W, H).withDestinationDir(OUT).disableCounterPrefix());
	}

	/**
	 * A screen, at the size it was laid out for. {@code withSize} only enlarges the render target —
	 * it never re-lays-out an open screen — so the window itself is resized first and the screenshot
	 * then taken at the window's own size. Without this a GUI draws tiny in the corner of the frame.
	 */
	private Path guiShot(ClientGameTestContext ctx, String name) {
		return ctx.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(OUT).disableCounterPrefix());
	}

	// ---- the shots

	/**
	 * The group. The first frame is the one the poster is built on: a low three-quarter camera at the
	 * south-east corner, a telephoto field of view, and the nearest figure large and overlapping the
	 * ones behind it. The rest are the same six from other distances and angles.
	 */
	private void heroShots(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		double ground = hero.ground();
		server.runCommand("time set " + GOLDEN);
		ctx.waitTicks(30);

		// THE hero: the eye of a photographer down on one knee, looking slightly up into the group.
		fov(ctx, 36);
		lookFrom(server, hero.x(5.8), ground + 0.92, hero.z(7.4), hero.x(0.35), ground + 1.50, hero.z(0.4));
		conn.waitForClientboundPackets();
		ctx.waitTicks(15);
		shot(ctx, "01_hero_group_low_three_quarter");

		// The same corner, wider: more lawn, more sky, room for type.
		fov(ctx, 52);
		lookFrom(server, hero.x(4.4), ground + 1.05, hero.z(5.6), hero.x(0.3), ground + 1.45, hero.z(0.6));
		ctx.waitTicks(10);
		shot(ctx, "02_hero_group_low_wide");

		// Further back and longer still: the flattest, most poster-like rendering of the six.
		fov(ctx, 30);
		lookFrom(server, hero.x(10.0), ground + 1.25, hero.z(8.6), hero.x(0.4), ground + 1.40, hero.z(0.2));
		ctx.waitTicks(10);
		shot(ctx, "03_hero_group_telephoto");

		// Almost along the line, very low: the figures stack one behind the other near the middle of
		// the frame, so a 3:4 crop out of the centre is a portrait poster.
		fov(ctx, 32);
		lookFrom(server, hero.x(2.9), ground + 0.75, hero.z(8.8), hero.x(0.9), ground + 1.05, hero.z(-1.0));
		ctx.waitTicks(10);
		shot(ctx, "04_hero_vertical_stack");

		server.runCommand("time set " + MORNING);
		ctx.waitTicks(30);

		// Straight on, at chest height: every garment whole and side by side, for reference.
		fov(ctx, 38);
		camera(server, hero.x(10.0), ground + 1.35, hero.z(0.0), 90, 0.5);
		ctx.waitTicks(10);
		shot(ctx, "05_group_front");
	}

	/**
	 * Puts the shader pack where Iris keeps them and turns it on, from inside the running client.
	 * It cannot be done beforehand: the gametest task builds its run directory from scratch every
	 * time, so anything dropped in {@code shaderpacks/} or written into {@code config/iris.properties}
	 * is gone before the game starts. Iris is reached by reflection — it is not on this module's
	 * compile classpath, and nothing here may add it.
	 */
	private static void selectTheShaderPack(ClientGameTestContext ctx) throws Exception {
		if (SHADER_PACK == null) throw new AssertionError("OVVAR_PROMO_SHADERS is set but OVVAR_PROMO_SHADERPACK is not");
		Path source = Path.of(SHADER_PACK);
		String name = source.getFileName().toString();
		ctx.runOnClient(client -> {
			Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
			Path packs = (Path) iris.getMethod("getShaderpacksDirectory").invoke(null);
			java.nio.file.Files.createDirectories(packs);
			java.nio.file.Files.copy(source, packs.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

			Object config = iris.getMethod("getIrisConfig").invoke(null);
			config.getClass().getMethod("setShaderPackName", String.class).invoke(config, name);
			config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
			config.getClass().getMethod("save").invoke(config);
			iris.getMethod("reload").invoke(null);
			System.out.println("[ovvar-promo] iris pack " + config.getClass().getMethod("getShaderPackName").invoke(config)
					+ ", shaders on: " + config.getClass().getMethod("areShadersEnabled").invoke(config));
		});
		ctx.waitTicks(100);
	}

	/**
	 * The shader run: the hero frame, the straight-on group and one close-up, under whatever pack
	 * Iris has loaded. Shaders compile their programs on the first frames they are asked for and
	 * their shadow maps and light propagation take a while to settle, so the camera sits in place for
	 * a good ten seconds before the first shutter.
	 */
	private void shaderShots(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		double ground = hero.ground();
		server.runCommand("time set " + GOLDEN);
		fov(ctx, 36);
		lookFrom(server, hero.x(5.8), ground + 0.92, hero.z(7.4), hero.x(0.35), ground + 1.50, hero.z(0.4));
		conn.waitForClientboundPackets();
		ctx.waitTicks(200);   // program compilation, then shadows and light settling
		shot(ctx, "30_shaders_" + SHADER + "_hero");

		fov(ctx, 38);
		camera(server, hero.x(10.0), ground + 1.35, hero.z(0.0), 90, 0.5);
		ctx.waitTicks(60);
		shot(ctx, "31_shaders_" + SHADER + "_group_front");

		fov(ctx, 45);
		double chest = ground + 1.35;
		lookFrom(server, figureX(0) + 1.75, chest + 0.08, figureZ(0) + 0.75, figureX(0), chest, figureZ(0));
		ctx.waitTicks(60);
		shot(ctx, "32_shaders_" + SHADER + "_closeup");
	}

	/** Close enough that the art's texels are square and a patch is seen bending round a corner. */
	private void closeUps(ClientGameTestContext ctx, TestDedicatedServerContext server) {
		fov(ctx, 45);
		double chest = hero.ground() + 1.35;

		// The shoulder and the outside of the sleeve at once: the shoulder patch turns the corner here.
		lookFrom(server, figureX(0) + 1.75, chest + 0.08, figureZ(0) + 0.75, figureX(0), chest, figureZ(0));
		ctx.waitTicks(8);
		shot(ctx, "06_closeup_shoulder_corner");

		lookFrom(server, figureX(1) + 1.75, chest - 0.05, figureZ(1) - 0.45, figureX(1), chest - 0.05, figureZ(1));
		ctx.waitTicks(8);
		shot(ctx, "07_closeup_chest");

		// A leg, where a rolled-down ovve carries the whole design: knee to waist, with the top
		// bunched at the belt above it.
		fov(ctx, 40);
		lookFrom(server, figureX(2) + 1.9, hero.ground() + 0.78, figureZ(2) + 0.70, figureX(2), hero.ground() + 0.40, figureZ(2));
		ctx.waitTicks(8);
		shot(ctx, "08_closeup_leg_rolled_down");
	}

	/** The group from behind, in the afternoon so the sun is behind this camera too. */
	private void backShots(ClientGameTestContext ctx, TestDedicatedServerContext server) {
		double ground = hero.ground();
		fov(ctx, 40);
		lookFrom(server, hero.x(-4.6), ground + 1.40, hero.z(5.4), hero.x(0.4), ground + 1.45, hero.z(-0.6));
		ctx.waitTicks(12);
		shot(ctx, "09_group_back");

		fov(ctx, 45);
		double back = ground + 1.15;
		lookFrom(server, figureX(3) - 2.0, back, figureZ(3) + 0.3, figureX(3), back - 0.1, figureZ(3));
		ctx.waitTicks(8);
		shot(ctx, "10_closeup_back_big_cell");

		lookFrom(server, figureX(5) - 2.1, ground + 1.00, figureZ(5) - 1.0, figureX(5), ground + 0.75, figureZ(5));
		ctx.waitTicks(8);
		shot(ctx, "11_closeup_seat");

		// The armoured four from behind, while the sun is still on this side: the elytra and the
		// netherite back, with the seat patch under them.
		fov(ctx, 36);
		lookFrom(server, hero.x(-6.2), ground + 1.35, hero.z(20.8), hero.x(0.4), ground + 1.30, hero.z(13.4));
		ctx.waitTicks(10);
		shot(ctx, "24_armour_over_ovve_back");
		fov(ctx, 40);
	}

	/** One figure alone on the other stage, whole, front and back — a cut-out waiting to happen. */
	private void coverShots(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		// The back view first, while the sun is still in the west and behind that camera.
		settle(ctx, server, cover, -6.0, 5.0);
		fov(ctx, 36);
		lookFrom(server, cover.x(-3.6), cover.ground() + 0.80, cover.z(-2.7), cover.x(0.0), cover.ground() + 1.00, cover.z(0.0));
		ctx.waitTicks(15);
		shot(ctx, "12_cover_back_three_quarter");

		server.runCommand("time set " + GOLDEN);
		settle(ctx, server, cover, 6.0, 5.0);
		// Front three-quarter, from below the waist looking up: the figure stands against sky.
		lookFrom(server, cover.x(3.6), cover.ground() + 0.80, cover.z(2.7), cover.x(0.0), cover.ground() + 1.00, cover.z(0.0));
		ctx.waitTicks(15);
		shot(ctx, "13_cover_front_three_quarter");

		// The same figure whole, further off, so the western water is in the frame with it.
		fov(ctx, 40);
		lookFrom(server, cover.x(5.5), cover.ground() + 1.05, cover.z(3.4), cover.x(0.0), cover.ground() + 0.95, cover.z(0.0));
		ctx.waitTicks(10);
		shot(ctx, "14_cover_in_the_landscape");
	}

	/**
	 * What happens when a player puts real chest armour on over an ovve: the ovve's top is in the
	 * chest slot, so the armour takes that slot and hides it, and the legs and the seat patch are
	 * what is left on show. The leftmost figure wears no armour, so the two states stand side by side.
	 */
	private void armourShots(ClientGameTestContext ctx, TestDedicatedServerContext server) {
		double ground = hero.ground();
		fov(ctx, 36);
		fov(ctx, 38);
		lookFrom(server, hero.x(4.6), ground + 0.95, hero.z(18.2), hero.x(0.4), ground + 1.30, hero.z(12.8));
		ctx.waitTicks(12);
		shot(ctx, "23_armour_over_ovve_front");
		fov(ctx, 40);
	}

	/** The player wearing one, in third person, with a full design on it. */
	private void playerShots(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		server.runCommand("gamemode creative Tester");
		// Built here rather than asked of the wardrobe store: the store answers asynchronously and
		// run 2 caught the player still bare. This is the same unowned item the figures wear.
		server.runOnServer(mc -> {
			var player = mc.getPlayerList().getPlayerByName("Tester");
			if (player == null) return;
			ItemStack ovve = ovveOf(COVER);
			player.setItemSlot(EquipmentSlot.LEGS, ovve.copy());
			ItemStack top = new ItemStack(ModContent.top(COVER.chapter()));
			top.set(ModComponents.PATCHES, ovve.get(ModComponents.PATCHES));
			player.setItemSlot(EquipmentSlot.CHEST, top);
			System.out.println("[ovvar-promo] the player is wearing " + ovve + " and " + top);
		});
		// The player's own combination has to be in the pack too before the camera turns round.
		ctx.waitTicks(20);
		server.runCommand("execute as Tester run ovvar reload");
		ctx.waitTicks(60);
		settleThePack(ctx);
		ctx.waitTicks(40);

		// On the path south of the group, facing the morning sun; the camera is west of the player,
		// so the player is lit from the front with the group and the lawn behind.
		double ground = hero.ground();
		fov(ctx, 40);
		camera(server, hero.x(0.0), ground + 1.62, hero.z(8.5), -90, 0);
		conn.waitForClientboundPackets();
		ctx.waitTicks(25);
		ctx.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(20);
		verifyDressed(ctx);
		shot(ctx, "15_player_third_person_front");

		// Strafing turns the body off the head, which is the three-quarter a still pose has not got.
		ctx.getInput().holdKey(options -> options.keyLeft);
		ctx.waitTicks(5);
		shot(ctx, "16_player_walking");
		ctx.getInput().releaseKey(options -> options.keyLeft);
		ctx.waitTicks(6);

		camera(server, hero.x(0.0), ground + 1.62, hero.z(8.5), 90, 0);
		ctx.waitTicks(10);
		ctx.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(10);
		shot(ctx, "17_player_third_person_back");
		ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("gamemode spectator Tester");
		ctx.waitTicks(10);
	}

	/** Says in the log whether the client really has the ovve on, so a bare-skinned frame is noticed. */
	private static void verifyDressed(ClientGameTestContext ctx) {
		ctx.runOnClient(client -> {
			ItemStack legs = client.player.getItemBySlot(EquipmentSlot.LEGS);
			ItemStack chest = client.player.getItemBySlot(EquipmentSlot.CHEST);
			System.out.println("[ovvar-promo] client sees legs=" + legs + " chest=" + chest);
		});
	}

	/** A wall of item frames: every patch and the four garments, as the items a player trades. */
	private void itemFrameShot(ClientGameTestContext ctx, TestDedicatedServerContext server) {
		int wallX = hero.x + 17, wallZ = hero.z, wallY = hero.y + 4;
		server.runCommand("time set " + AFTERNOON);   // a west-facing wall wants the afternoon sun on it
		fill(server, wallX, hero.y + 1, wallZ - 6, wallX, wallY + 4, wallZ + 6, "minecraft:smooth_stone");
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			List<ItemStack> items = new ArrayList<>();
			for (Patches.Patch patch : Patches.all()) items.add(new ItemStack(ModContent.patchItem(patch)));
			for (Chapter chapter : SHOWN) items.add(new ItemStack(ModContent.ovve(chapter)));
			// Five by five, one block apart: an item frame draws its item at about half a block, so a
			// wider grid than this is a big grey wall with confetti on it. 18 patches and 4 garments.
			int i = 0;
			for (int row = 2; row >= -2 && i < items.size(); row--) {
				for (int col = -2; col <= 2 && i < items.size(); col++) {
					BlockPos pos = new BlockPos(wallX - 1, wallY + row, wallZ + col);
					ItemFrame frame = new ItemFrame(EntityTypes.ITEM_FRAME, level, pos, Direction.WEST);
					frame.setItem(items.get(i++), false);
					frame.setInvisible(true);
					level.addFreshEntity(frame);
				}
			}
		});
		fov(ctx, 36);
		camera(server, wallX - 8.5, wallY + 0.5, wallZ + 0.5, -90, 0);   // the middle column's block centre
		ctx.waitTicks(60);
		shot(ctx, "18_items_on_the_wall");
		fov(ctx, 40);
	}

	/**
	 * The wardrobe screen, the sewing dialog and the inventory: what a player clicks. The window is
	 * made the size the picture wants first, which re-lays-out whatever screen is open, and the
	 * screenshots are then taken at the window's own size.
	 */
	private void guiShots(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		server.runCommand("time set " + MORNING);
		fillTheWardrobe(server);
		ctx.waitTicks(60);
		server.runCommand("execute as Tester run ovvar reload");
		ctx.waitTicks(40);
		settleThePack(ctx);

		// Among the figures, so a screen sits over a scene and not over a wall.
		camera(server, hero.x(11.0), hero.ground() + 1.25, hero.z(0.0), 90, -2);
		ctx.waitTicks(20);
		ctx.getInput().resizeWindow(GUI_W, GUI_H);
		ctx.runOnClient(client -> {
			client.options.guiScale().set(4);
			client.resizeGui();
		});
		ctx.waitTicks(10);

		server.runCommand("execute as Tester run ovvar stash");
		conn.waitForClientboundPackets();
		ctx.waitTicks(40);
		guiShot(ctx, "19_wardrobe_screen");
		ctx.runOnClient(client -> client.gui.setScreen(null));
		ctx.waitTicks(10);

		// The sewing dialog: a stand in an ovve, then the dialog opened on a named cell.
		server.runCommand("execute as Tester at Tester run ovvar stands data");
		ctx.waitTicks(30);
		server.runCommand("execute as Tester at Tester run ovvar stitch front_top_left.itk");
		conn.waitForClientboundPackets();
		ctx.waitTicks(40);
		guiShot(ctx, "20_sewing_dialog");
		ctx.runOnClient(client -> client.gui.setScreen(null));
		ctx.waitTicks(10);

		// The inventory. Survival, not creative: the creative screen is a block palette, and what
		// these two frames are about is the player's own pockets — an ovve is a bundle, and the one
		// in the legs slot is holding a load of things while it is being worn.
		server.runCommand("gamemode survival Tester");
		server.runOnServer(mc -> {
			var player = mc.getPlayerList().getPlayerByName("Tester");
			if (player == null) return;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) player.getInventory().setItem(i, ItemStack.EMPTY);
			int slot = 18;
			for (Patches.Patch patch : Patches.all()) player.getInventory().setItem(slot++, new ItemStack(ModContent.patchItem(patch), 3));
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
			player.getInventory().setItem(1, new ItemStack(Items.COOKED_BEEF, 12));
		});
		// An unlocked-recipe toast sits in the corner for a few seconds; wait it out.
		ctx.waitTicks(40);
		ctx.runOnClient(client -> {
			client.options.guiScale().set(4);
			client.resizeGui();
		});
		ctx.getInput().pressKey(options -> options.keyInventory);
		ctx.waitTicks(120);

		// Filled last and shot at once. A worn ovve is rebuilt from the wardrobe store every tick and
		// an unowned one can be taken off the player again, so anything put on it early is gone by the
		// time the shutter opens — run 13 photographed an empty legs slot for exactly that reason.
		fillThePockets(server);
		ctx.waitTicks(10);
		hoverSlot(ctx, LEGS_SLOT);
		guiShot(ctx, "21_inventory_worn_ovve_pockets");

		// The same screen with the cursor on a spare ovve down in the pack: the item's own tooltip.
		fillThePockets(server);
		ctx.waitTicks(6);
		hoverSlot(ctx, SPARE_SLOT);
		guiShot(ctx, "22_inventory_ovve_tooltip");

		ctx.runOnClient(client -> client.gui.setScreen(null));
		ctx.waitTicks(10);
		server.runCommand("gamemode spectator Tester");
	}

	/** Puts an ovve back on the player and a spare in the pack, both with their pockets full. */
	private static void fillThePockets(TestDedicatedServerContext server) {
		server.runOnServer(mc -> {
			var player = mc.getPlayerList().getPlayerByName("Tester");
			if (player == null) return;
			ItemStack worn = ovveOf(COVER);
			worn.set(DataComponents.BUNDLE_CONTENTS, pocketful());
			player.setItemSlot(EquipmentSlot.LEGS, worn);
			ItemStack top = new ItemStack(ModContent.top(COVER.chapter()));
			top.set(ModComponents.PATCHES, worn.get(ModComponents.PATCHES));
			player.setItemSlot(EquipmentSlot.CHEST, top);

			ItemStack spare = ovveOf(LINE_UP.get(1));
			spare.set(DataComponents.BUNDLE_CONTENTS, pocketful());
			player.getInventory().setItem(SPARE_SLOT, spare);

			player.inventoryMenu.broadcastChanges();
			player.containerMenu.broadcastChanges();
			System.out.println("[ovvar-promo] worn pockets " + player.getItemBySlot(EquipmentSlot.LEGS).get(DataComponents.BUNDLE_CONTENTS)
					+ "; spare pockets " + player.getInventory().getItem(SPARE_SLOT).get(DataComponents.BUNDLE_CONTENTS));
		});
	}

	/** The legs slot of the player's own inventory menu: 0 is the crafting result, 5..8 the armour. */
	private static final int LEGS_SLOT = 7;

	/** A spare ovve in the top row of the pack proper, both as a menu slot and an inventory slot. */
	private static final int SPARE_SLOT = 9;

	/**
	 * Puts the mouse over a slot of the open container screen, so the screenshot catches its tooltip.
	 * The screen's own {@code leftPos}/{@code topPos} are protected, but a container screen is
	 * centred, so they come back from the screen's size and the vanilla 176×166 panel; the slot's own
	 * offsets within the panel are public. The cursor wants window pixels, not GUI units.
	 */
	private static void hoverSlot(ClientGameTestContext ctx, int index) {
		double[] at = new double[]{-1, -1};
		ctx.runOnClient(client -> {
			if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
			if (index >= screen.getMenu().slots.size()) return;
			var slot = screen.getMenu().slots.get(index);
			double scale = client.getWindow().getGuiScale();
			at[0] = ((screen.width - 176) / 2.0 + slot.x + 8.5) * scale;
			at[1] = ((screen.height - 166) / 2.0 + slot.y + 8.5) * scale;
		});
		if (at[0] < 0) {
			System.out.println("[ovvar-promo] no container screen to hover slot " + index + " on");
			return;
		}
		System.out.println("[ovvar-promo] hovering slot " + index + " at " + at[0] + " " + at[1]);
		ctx.getInput().setCursorPos(at[0], at[1]);
		ctx.waitTicks(4);
	}

	/** A dozen colourful things, so the bundle tooltip under the ovve's own has something to show. */
	private static BundleContents pocketful() {
		// A vanilla bundle holds sixty-four items' worth, and these come to about fifty: a cake or a
		// nether star weighs a whole bundle by itself, which is how run 11 ended up with empty pockets.
		return BundleContents.EMPTY.copyWithContents(Stream.of(
				new ItemStack(Items.DIAMOND, 8),
				new ItemStack(Items.EMERALD, 8),
				new ItemStack(Items.GOLDEN_CARROT, 6),
				new ItemStack(Items.AMETHYST_SHARD, 5),
				new ItemStack(Items.GLOW_BERRIES, 6),
				new ItemStack(Items.TORCH, 10),
				new ItemStack(Items.ENDER_PEARL, 2),
				new ItemStack(Items.COOKIE, 4)));
	}

	/**
	 * A design on every chapter and a deep stash, in one write. The store's writes are
	 * compare-and-set, so a command per patch would mostly lose to the one before it — run 2's stash
	 * held four patches out of forty for exactly that reason.
	 */
	private static void fillTheWardrobe(TestDedicatedServerContext server) {
		server.runOnServer(mc -> {
			var player = mc.getPlayerList().getPlayerByName("Tester");
			if (player == null) return;
			Wardrobes.update(player.getUUID(), w -> {
				Wardrobe next = w;
				for (Figure figure : LINE_UP) {
					List<Placement> placements = new ArrayList<>();
					for (String key : figure.patches()) placements.add(Placement.parse(key));
					next = next.withDesign(figure.chapter(), SpotPlacements.fromList(placements).getOrThrow());
				}
				for (Patches.Patch patch : Patches.all()) next = next.add(patch, 6);
				return next;
			}, outcome -> System.out.println("[ovvar-promo] wardrobe filled: " + outcome));
		});
	}
}
