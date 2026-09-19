package nu.metacraft.rivals.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.paint.Painter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * What a client sees of the paint. A dedicated server runs in-process with the mod; the test client
 * joins it as a vanilla client (PolymerHelloMixin), takes the required pack, and looks at a scene
 * the server paints: two blobs on a stone floor that touch, a blob on a wall, and a blob over a
 * stair (display quads, drawn by the item shader, not the terrain one). Screenshots land in
 * {@code build/run/clientGameTest/screenshots}, named for whether Sodium is on the client, so a
 * vanilla run and a {@code -Psodium=<version>} run can be laid side by side.
 *
 * Run: {@code ./gradlew :mods:rivals-paint:runClientGameTest} (opens a window).
 */
public final class RivalsClientTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		String flavour = FabricLoader.getInstance().isModLoaded("sodium") ? "sodium" : "vanilla";
		System.out.println("[rivals-clienttest] client flavour: " + flavour + " (mods: " + FabricLoader.getInstance().getAllMods().stream()
				.map(m -> m.getMetadata().getId()).filter(id -> id.equals("sodium") || id.equals("iris")).toList() + ")");
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25598");
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
				server.runCommand("gamemode creative Tester");
				server.runCommand("tp Tester 0.5 -57 -5.5 0 35");
				server.runOnServer(mc -> {
					ServerLevel level = mc.getPlayerList().getPlayers().getFirst().level();
					// A stone floor (grass reads too close to the paint), a wall across the far side, a stair and a slab.
					for (int x = -8; x <= 8; x++) {
						for (int z = -8; z <= 10; z++) level.setBlock(new BlockPos(x, -61, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
					}
					for (int x = -8; x <= 8; x++) {
						for (int y = -60; y <= -57; y++) level.setBlock(new BlockPos(x, y, 8), Blocks.STONE_BRICKS.defaultBlockState(), 3);
					}
					level.setBlock(new BlockPos(4, -60, 1), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
							.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.BOTTOM), 3);
					level.setBlock(new BlockPos(-4, -60, 1), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3);
				});
				ctx.waitTicks(20);
				server.runOnServer(mc -> {
					ServerLevel level = mc.getPlayerList().getPlayers().getFirst().level();
					var random = level.getRandom();
					// Floor: two colours meeting, so the seam between sheets is in the picture.
					Painter.splat(level, new BlockPos(-1, -61, 2), Direction.UP, PaintColor.DATA, random, 2);
					Painter.splat(level, new BlockPos(2, -61, 3), Direction.UP, PaintColor.IT, random, 2);
					Painter.splat(level, new BlockPos(-3, -61, -1), Direction.UP, PaintColor.IT, random, 0);
					// Wall: the north face of the far wall, which the player faces.
					Painter.splat(level, new BlockPos(-2, -59, 8), Direction.NORTH, PaintColor.DATA, random, 1);
					Painter.splat(level, new BlockPos(3, -58, 8), Direction.NORTH, PaintColor.IT, random, 1);
					// Stair and slab: display quads.
					Painter.splat(level, new BlockPos(4, -60, 1), Direction.UP, PaintColor.DATA, random, 0);
					Painter.splat(level, new BlockPos(-4, -60, 1), Direction.UP, PaintColor.IT, random, 0);
				});
				acceptResourcePack(ctx);   // in case the pack is pushed again
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				server.runCommand("tp Tester 0.5 -57 -5.5 0 35");
				conn.waitForClientboundPackets();
				ctx.waitTicks(100);

				Path wide = ctx.takeScreenshot(TestScreenshotOptions.of("paint_" + flavour + "_wide").withSize(1920, 1080));
				server.runCommand("tp Tester 0.5 -58.5 0.5 0 70");
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				Path close = ctx.takeScreenshot(TestScreenshotOptions.of("paint_" + flavour + "_close").withSize(1920, 1080));
				server.runCommand("tp Tester 0.5 -58 4.5 0 10");
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				Path wall = ctx.takeScreenshot(TestScreenshotOptions.of("paint_" + flavour + "_wall").withSize(1920, 1080));
				// Straight down over the lone IT cell at (-3, -61, -1): its centre is the screen's centre. The
				// cell's corner texel is stone on every client — the geometry's notch — and the centre is paint.
				// Before the border moved into the geometry a Sodium client painted the whole square.
				server.runCommand("tp Tester -2.5 -57 -0.5 0 90");
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				Path down = ctx.takeScreenshot(TestScreenshotOptions.of("paint_" + flavour + "_corner").withSize(1920, 1080));

				BufferedImage img = read(wide);
				int data = count(img, (r, g, b) -> r > 140 && g < 90 && b > 50 && b < 130);     // DATA 0xBD3754, cerise
				int it = count(img, (r, g, b) -> b > 140 && r > 90 && r < 180 && g < 120);     // IT 0x8A57BD, lilac
				System.out.println("[rivals-clienttest] " + flavour + ": data-ish pixels " + data + ", it-ish pixels " + it
						+ " in " + wide + ", " + close + ", " + wall);
				if (data < 500) throw new AssertionError("Data paint barely shows: " + data + " pixels");
				if (it < 500) throw new AssertionError("IT paint barely shows: " + it + " pixels");
				BufferedImage top = read(down);
				Rgb lilac = (r, g, b) -> b > 140 && r > 90 && r < 180 && g < 120;
				int cx = top.getWidth() / 2, cy = top.getHeight() / 2;
				if (!lilac.test(rgb(top, cx, cy)[0], rgb(top, cx, cy)[1], rgb(top, cx, cy)[2])) {
					throw new AssertionError(flavour + ": the cell's centre is not IT paint: " + java.util.Arrays.toString(rgb(top, cx, cy)) + " in " + down);
				}
				// How big the cell is on screen is measured, not assumed: along a row a little above the centre
				// (clear of the block outline drawn at the centre) the paint runs from the centre to the cell's
				// straight edge, which with the border is 7 texels out, so that run is 7 texels. The corner texel
				// is then 7.5 texels out on both axes: stone with the border, paint without it (a full square's
				// run is 8 texels, which puts 7.5 of its "texels" inside it — and painted).
				int scanRow = cy - 100;
				int run = 0;
				while (cx + run < top.getWidth() && lilac.test(rgb(top, cx + run, scanRow)[0], rgb(top, cx + run, scanRow)[1], rgb(top, cx + run, scanRow)[2])) run++;
				double texel = run / 7.0;
				int off = (int) Math.round(7.5 * texel);
				System.out.println("[rivals-clienttest] " + flavour + ": paint runs " + run + " px from the centre; corner sampled " + off + " px out in " + down);
				if (run < 40) throw new AssertionError(flavour + ": the cell is too small on screen to judge (" + run + " px)");
				for (int[] corner : new int[][] {{-off, -off}, {off, -off}, {-off, off}, {off, off}}) {
					int[] c = rgb(top, cx + corner[0], cy + corner[1]);
					if (lilac.test(c[0], c[1], c[2])) {
						throw new AssertionError(flavour + ": the cell's corner texel at " + corner[0] + "," + corner[1] + " is painted (no border): "
								+ java.util.Arrays.toString(c) + " in " + down);
					}
				}
			}
		}
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}

	interface Rgb { boolean test(int r, int g, int b); }

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read screenshot " + path, e);
		}
	}

	private static int[] rgb(BufferedImage img, int x, int y) {
		int p = img.getRGB(x, y);
		return new int[] {(p >> 16) & 255, (p >> 8) & 255, p & 255};
	}

	private static int count(BufferedImage img, Rgb match) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getRGB(x, y);
				if (match.test((p >> 16) & 255, (p >> 8) & 255, p & 255)) n++;
			}
		}
		return n;
	}
}
