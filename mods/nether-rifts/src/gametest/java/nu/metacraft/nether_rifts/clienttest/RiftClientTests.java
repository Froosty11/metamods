package nu.metacraft.nether_rifts.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.ConfirmScreen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * A vanilla client (no Polymer handshake) joins an in-process server and looks at rifts: a 2×3 portal at
 * each stage, an L-shaped rift (merged panels must join without a seam), a 2×3 portal on the Z plane, and a
 * void-anchor rift in the same run (the shared item shader must draw both).
 */
public final class RiftClientTests implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25602");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set midnight");
				server.runCommand("gamemode spectator Tester");

				// 2×3 portal: frame x -1..2, y -60..-56 at z 0, inside x 0..1, y -59..-57
				server.runCommand("fill -1 -60 0 2 -56 0 minecraft:obsidian");
				server.runCommand("fill 0 -59 0 1 -57 0 minecraft:air");
				server.runCommand("setblock 0 -59 0 minecraft:fire");
				server.runCommand("tp Tester 0.5 -58 4 180 0");
				conn.waitForClientboundPackets();
				ctx.waitTicks(30);
				int[] crimson = new int[3];
				int[] movement = new int[3];
				String[] stages = {"lit", "unstable", "torn"};
				for (int i = 0; i < stages.length; i++) {
					String stage = stages[i];
					server.runCommand("rifts stage 0 -58 0 " + stage);
					ctx.waitTicks(10);
					Path a = ctx.takeScreenshot(TestScreenshotOptions.of("rift_" + stage + "_a").withSize(1920, 1080));
					ctx.waitTicks(5);
					Path b = ctx.takeScreenshot(TestScreenshotOptions.of("rift_" + stage + "_b").withSize(1920, 1080));
					var first = read(a);
					int purple = count(first, RiftClientTests::riftPixel);
					int moved = changed(first, read(b), 24);
					crimson[i] = count(first, RiftClientTests::crimsonPixel);
					movement[i] = moved;
					System.out.println("[nether-rifts-clienttest] " + stage + " purple=" + purple + " moved=" + moved + " crimson=" + crimson[i]);
					check(purple >= BOX / 20, "no rift on screen at " + stage + ": " + purple + " rift pixels");
					check(moved >= BOX / 200, "the rift doesn't move at " + stage + ": " + moved + " pixels changed");
				}
				// The fallback sprite looks the same at every stage (the marker tint barely changes it); only the
				// shader reddens and churns more as the rift tears. Rising crimson and motion prove it ran.
				if (crimson[2] < 2000 || crimson[2] < crimson[0] * 2) {
					check(false, "the torn rift isn't redder than the lit one (shader not running?): crimson lit=" + crimson[0] + " torn=" + crimson[2]);
				}
				if (movement[2] * 2 < movement[0] * 3) {
					check(false, "the torn rift doesn't churn more than the lit one: moved lit=" + movement[0] + " torn=" + movement[2]);
				}
				server.runCommand("rifts collapse 0 -58 0");

				// an L of plain obsidian at z 10 (the frame itself must score nothing), opened with /rifts form
				server.runCommand("fill -2 -60 10 5 -53 10 minecraft:obsidian");
				server.runCommand("fill -1 -59 10 1 -55 10 minecraft:air");
				server.runCommand("fill 2 -59 10 4 -58 10 minecraft:air");
				server.runCommand("tp Tester 1.5 -57 15 180 0");
				ctx.waitTicks(30);
				var before = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_blob_before").withSize(1920, 1080)));
				int blobBefore = count(before, RiftClientTests::riftPixel);
				server.runCommand("rifts form 0 -58 10");
				ctx.waitTicks(30);
				var blob = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_blob").withSize(1920, 1080)));
				int blobPurple = count(blob, RiftClientTests::riftPixel);
				System.out.println("[nether-rifts-clienttest] blob before=" + blobBefore + " after=" + blobPurple);
				check(blobPurple >= blobBefore + BOX / 10, "forming didn't draw the L-shaped rift: " + blobBefore + " -> " + blobPurple);

				// a 2×3 portal on the Z plane (panels turned a quarter): frame z -21..-18 at x 20, inside z -20..-19, looked at from the east
				server.runCommand("fill 20 -60 -21 20 -56 -18 minecraft:obsidian");
				server.runCommand("fill 20 -59 -20 20 -57 -19 minecraft:air");
				server.runCommand("setblock 20 -59 -20 minecraft:fire");
				server.runCommand("tp Tester 24 -58 -19 90 0");
				conn.waitForClientboundPackets();
				ctx.waitTicks(30);
				var zA = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_z_plane_a").withSize(1920, 1080)));
				ctx.waitTicks(5);
				var zB = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_z_plane_b").withSize(1920, 1080)));
				int zPurple = count(zA, RiftClientTests::riftPixel);
				int zMoved = changed(zA, zB, 24);
				System.out.println("[nether-rifts-clienttest] z_plane purple=" + zPurple + " moved=" + zMoved);
				check(zPurple >= BOX / 20, "no Z-plane rift on screen: " + zPurple + " rift pixels");
				check(zMoved >= BOX / 200, "the Z-plane rift doesn't move: " + zMoved + " pixels changed");
				server.runCommand("rifts collapse 20 -58 -20");

				// the void anchor's rift, same shader file
				server.runCommand("tp Tester 30.5 -50 0.5 0 90");
				ctx.waitTicks(10);
				server.runCommand("voidanchor rift 30.5 -57 0.5");
				ctx.waitTicks(20);
				var voidShot = read(ctx.takeScreenshot(TestScreenshotOptions.of("void_anchor_rift").withSize(1920, 1080)));
				int voidPx = count(voidShot, (r, g, bl) -> r >= 28 && r <= 70 && g < r && g >= r - 18 && bl > r + 5 && bl < r + 30);
				System.out.println("[nether-rifts-clienttest] void=" + voidPx);
				check(voidPx >= BOX / 100, "the void anchor's rift lost its shader: " + voidPx);
			}
		}
	}

	static final int BOX = 300 * 300;

	/** Set when the client runs Iris with this shaderpack (-Piris): the rifts are the pack's to draw, so log, don't fail. */
	static final String SHADERPACK = System.getenv("RIFTS_SHADERPACK");

	private static void check(boolean ok, String failure) {
		if (ok) return;
		if (SHADERPACK != null) {
			System.out.println("[nether-rifts-clienttest] under " + SHADERPACK + ": " + failure);
		} else {
			throw new AssertionError(failure);
		}
	}

	/** The swirl's violet and crimson, the edge's hot pink; none of which a flat world at night has. */
	static boolean riftPixel(int r, int g, int b) {
		return (b > g + 40 && r > g + 20) || (r > 150 && g < 70 && b < 110);
	}

	/** The torn stage's crimson veins: red well above green, not violet or blue-dominant. */
	static boolean crimsonPixel(int r, int g, int b) {
		return r > g + 80 && b < r;
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}

	interface Rgb {
		boolean test(int r, int g, int b);
	}

	/** Pixels in the 300×300 box at the centre of the screen that match. */
	private static int count(BufferedImage img, Rgb rgb) {
		int n = 0;
		int cx = img.getWidth() / 2, cy = img.getHeight() / 2;
		for (int y = cy - 150; y < cy + 150; y++) {
			for (int x = cx - 150; x < cx + 150; x++) {
				int p = img.getRGB(x, y);
				if (rgb.test((p >> 16) & 255, (p >> 8) & 255, p & 255)) n++;
			}
		}
		return n;
	}

	/** Pixels in the centre box that changed by more than {@code step} in some channel. */
	private static int changed(BufferedImage a, BufferedImage b, int step) {
		int n = 0;
		int cx = a.getWidth() / 2, cy = a.getHeight() / 2;
		for (int y = cy - 150; y < cy + 150; y++) {
			for (int x = cx - 150; x < cx + 150; x++) {
				int p = a.getRGB(x, y), q = b.getRGB(x, y);
				for (int s = 0; s <= 16; s += 8) {
					if (Math.abs(((p >> s) & 255) - ((q >> s) & 255)) > step) {
						n++;
						break;
					}
				}
			}
		}
		return n;
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
