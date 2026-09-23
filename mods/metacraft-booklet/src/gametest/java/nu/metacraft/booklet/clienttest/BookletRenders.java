package nu.metacraft.booklet.clienttest;

import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import eu.pb4.polydecorations.canvas.CanvasData;
import eu.pb4.polydecorations.canvas.CanvasPixels;
import eu.pb4.polydecorations.entity.CanvasEntity;
import eu.pb4.polydecorations.item.DecorationsDataComponents;
import eu.pb4.polydecorations.item.DecorationsItems;
import eu.pb4.simpleimagerenderer.renderer.RegionImageRenderer;
import eu.pb4.simpleimagerenderer.renderer.RendererSettings;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.content.SpotPlacements;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * The guidebook's page images, the way Patbox makes PolyFactory's: each scene is built in a flat
 * world and rendered isometric, on a transparent background, with his
 * <a href="https://github.com/Patbox/SimpleImageRenderer">Simple Image Renderer</a> — a client-only
 * tool, here only in the client test runtime.
 *
 * <pre>
 *   METACRAFT_BOOKLET_RENDER=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
 * </pre>
 * writes {@code build/booklet-renders/<name>.png}; {@code
 * tools/booklet_images.py} trims them into {@code assets/metacraft/textures/booklet/image/}. Without
 * the variable this does nothing: it makes pictures, it checks nothing.
 */
public final class BookletRenders implements FabricClientGameTest {
	private static final int SIZE = 768;
	/** The isometric view: 30° down, 45° round, as PolyFactory's images are. */
	private static final int PITCH = -30, YAW = 45;

	private record Scene(String name, BlockPos from, BlockPos to, int scale) {}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!"1".equals(System.getenv("METACRAFT_BOOKLET_RENDER"))) return;
		// build/booklet-renders: outside the run directory, which the harness clears between runs.
		Path out = FabricLoader.getInstance().getGameDir().resolve("../../booklet-renders").normalize();

		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25603");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("gamemode spectator Tester");
				server.runCommand("tp Tester 30 -50 10 0 30");

				server.runOnServer(mc -> build(mc.overworld()));
				ctx.waitTicks(100);        // ovvar builds the patch combinations the mannequins wear
				// A pack is only pushed to a player whose own ovve outgrew it; nobody here wears one, so ask.
				server.runCommand("execute as Tester run ovvar reload");
				acceptResourcePack(ctx);
				conn.waitForClientboundPackets();
				ctx.waitTicks(100);        // spawn interpolation, stand sprites, the pack's textures

				Files.createDirectories(out);
				for (Scene scene : SCENES) Files.createDirectories(out.resolve(scene.name()).getParent());
				for (Scene scene : SCENES) {
					Path file = out.resolve(scene.name() + ".png");
					ctx.runOnClient(client -> {
						RendererSettings settings = RendererSettings.defaultSettings.clone();
						settings.pitch = PITCH;
						settings.yaw = YAW;
						settings.scale = scene.scale();
						var renderer = new RegionImageRenderer(client, SIZE, SIZE, client.level, BlockBox.of(scene.from(), scene.to()), false, false);
						settings.updateMatrix(renderer);
						client.gui.setScreen(new RenderScreen(renderer, file));
					});
					ctx.waitFor(client -> !(client.gui.screen() instanceof RenderScreen), 20 * 30);
					ctx.waitTicks(5);
				}
				ctx.waitTicks(20);
				for (Scene scene : SCENES) {
					if (!Files.exists(out.resolve(scene.name() + ".png"))) throw new AssertionError("no render of " + scene.name());
				}
				stash(ctx, server, conn, out.resolve("ovvar/stash.png"));
				System.out.println("[metacraft-booklet] rendered " + SCENES.size() + " page image(s) to " + out.toAbsolutePath());
			} catch (IOException e) {
				throw new AssertionError(e);
			}
		}
	}

	/**
	 * The stash menu, cut out of a screenshot: the Tester is given a design and some patches, opens
	 * {@code /ovvar stash} at GUI scale 1, so the menu is drawn at its own pixels, and the top part
	 * of the chest screen (the wardrobe; the Tester's inventory below it is left out) is kept.
	 */
	private static void stash(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, Path file) {
		server.runCommand("gamemode survival Tester");
		// /ovvar give needs a player to run it (the console is refused), and each gift is a write to the
		// wardrobe store that the next one must see, so they go one at a time.
		server.runCommand("execute as Tester run ovvar give data itk it data spiken slaggan");
		ctx.waitTicks(20);
		for (String patch : List.of("itk", "tmeit", "jgs", "in", "kommn", "rivals", "nyckeln0x1", "maid")) {
			server.runCommand("ovvar patch give Tester " + patch + " 2");
			ctx.waitTicks(10);
		}
		ctx.waitTicks(60);
		server.runCommand("execute as Tester run ovvar reload");
		acceptResourcePack(ctx);
		ctx.runOnClient(client -> {
			client.options.guiScale().set(1);
			client.resizeGui();
		});
		server.runCommand("execute as Tester run ovvar stash");
		conn.waitForClientboundPackets();
		ctx.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen, 20 * 10);
		ctx.getInput().setCursorPos(0, 0);   // no slot hovered, no tooltip
		ctx.runOnClient(client -> client.gui.toastManager().clear());
		ctx.waitTicks(20);
		Path shot = ctx.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("stash_full"));
		try {
			BufferedImage full = ImageIO.read(shot.toFile());
			// A 9×6 chest screen is 176×222, centred; its top 17 px are the title and 6 rows of 18 the
			// menu, then a 3 px margin before the inventory's own label.
			int w = 176, h = 17 + 6 * 18 + 3;
			int x = (full.getWidth() - w) / 2, y = (full.getHeight() - 222) / 2;
			ImageIO.write(full.getSubimage(x, y, w, h), "png", file.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot cut the stash out of " + shot, e);
		}
		ctx.runOnClient(client -> client.gui.setScreen(null));
	}

	private static final List<Scene> SCENES = List.of(
			new Scene("ovvar/hero", new BlockPos(20, -60, 20), new BlockPos(20, -58, 20), 60),
			new Scene("ovvar/back", new BlockPos(24, -60, 20), new BlockPos(24, -58, 20), 60),
			new Scene("ovvar/chapters", new BlockPos(28, -60, 20), new BlockPos(34, -58, 20), 70),
			new Scene("ovvar/stand", new BlockPos(40, -60, 20), new BlockPos(40, -58, 20), 60),
			new Scene("canvas/wall", new BlockPos(44, -60, 20), new BlockPos(45, -59, 21), 70));

	private static void build(ServerLevel level) {
		// ---- ovvar
		ItemStack front = ovve(Chapter.DATA, List.of(
				new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")),
				new Placement(Spot.FRONT_TOP_RIGHT, Patches.get("nyckeln0x0")),
				new Placement(Spot.FRONT_LOW_LEFT, Patches.get("it")),
				new Placement(Spot.FRONT_LOW_RIGHT, Patches.get("data")),
				new Placement(Spot.SLEEVE_OUT_TOP_L, Patches.get("spiken")),
				new Placement(Spot.SLEEVE_OUT_TOP_R, Patches.get("slaggan")),
				new Placement(Spot.SHOULDER_R, Patches.get("itk")),
				new Placement(Spot.LEG_OUT_TOP_R, Patches.get("tmeit")),
				new Placement(Spot.LEG_OUT_TOP_L, Patches.get("jgs"))));
		mannequin(level, 20, Chapter.DATA, front, 45);
		ItemStack back = ovve(Chapter.DATA, List.of(
				new Placement(Spot.BACK_BIG, Patches.get("it")),
				new Placement(Spot.BACK_TOP_LEFT, Patches.get("in")),
				new Placement(Spot.BACK_TOP_RIGHT, Patches.get("in_gold")),
				new Placement(Spot.SEAT, Patches.get("rivals"))));
		mannequin(level, 24, Chapter.DATA, back, 225);
		int x = 28;
		for (Chapter chapter : List.of(Chapter.DATA, Chapter.IT, Chapter.IT_KISEL, Chapter.MEDIA)) {
			mannequin(level, x, chapter, ovve(chapter, List.of(new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")))), 45);
			x += 2;
		}
		ArmorStand stand = new ArmorStand(EntityTypes.ARMOR_STAND, level);
		stand.setPos(40.5, -60, 20.5);
		stand.setYRot(45);
		stand.setShowArms(true);
		stand.setLeftArmPose(new Rotations(-20, 0, -10));
		stand.setRightArmPose(new Rotations(-15, 0, 10));
		stand.setItemSlot(EquipmentSlot.LEGS, ovve(Chapter.IT, List.of(
				new Placement(Spot.FRONT_TOP_LEFT, Patches.get("data")),
				new Placement(Spot.FRONT_LOW_RIGHT, Patches.get("kommn")),
				new Placement(Spot.SLEEVE_OUT_TOP_R, Patches.get("itk")))));
		level.addFreshEntity(stand);

		// ---- canvas: a 2×2 wall of planks with a 32×32 picture across four canvases on its south face,
		// the face the camera sees
		for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) {
			level.setBlockAndUpdate(new BlockPos(44 + dx, -60 + dy, 20), Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		BufferedImage picture = picture();
		for (int cx = 0; cx < 2; cx++) for (int cy = 0; cy < 2; cy++) {
			CanvasPixels pixels = new CanvasPixels();
			for (int px = 0; px < 16; px++) for (int py = 0; py < 16; py++) {
				int argb = picture.getRGB(cx * 16 + px, (1 - cy) * 16 + py);
				pixels.setRaw(px, py, (argb >>> 24) < 128 ? 0 : CanvasUtils.findClosestRawColorARGB(argb | 0xFF000000));
			}
			ItemStack canvas = new ItemStack(DecorationsItems.CANVAS);
			canvas.set(DecorationsDataComponents.CANVAS_DATA, new CanvasData(Optional.of(pixels), Optional.empty(), false, false, false));
			CanvasEntity entity = CanvasEntity.create(level, Direction.SOUTH, new BlockPos(44 + cx, -60 + cy, 21), 0);
			entity.loadFromStack(canvas);
			level.addFreshEntity(entity);
		}
	}

	private static ItemStack ovve(Chapter chapter, List<Placement> placements) {
		ItemStack ovve = new ItemStack(ModContent.ovve(chapter));
		OvveItem.setTopUp(ovve, true);
		Looks.setSewn(ovve, SpotPlacements.fromList(placements).getOrThrow());
		return ovve;
	}

	private static void mannequin(ServerLevel level, int x, Chapter chapter, ItemStack ovve, float yaw) {
		Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, level);
		m.setPos(x + 0.5, -60, 20.5);
		m.setYRot(yaw); m.setYBodyRot(yaw); m.setYHeadRot(yaw);
		m.setItemSlot(EquipmentSlot.LEGS, ovve);
		ItemStack top = new ItemStack(ModContent.top(chapter));
		top.set(ModComponents.PATCHES, ovve.get(ModComponents.PATCHES));
		m.setItemSlot(EquipmentSlot.CHEST, top);
		level.addFreshEntity(m);
	}

	/** The picture on the canvas wall: METAcraft's icon at 32×32. */
	private static BufferedImage picture() {
		try (InputStream in = BookletRenders.class.getResourceAsStream("/assets/metacraft-booklet/icon.png")) {
			BufferedImage src = ImageIO.read(in);
			BufferedImage out = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
			var g = out.createGraphics();
			g.drawImage(src, 0, 0, 32, 32, null);
			g.dispose();
			return out;
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}
}
