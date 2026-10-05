package nu.metacraft.void_anchor.clienttest;

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
 * What a vanilla client sees of a rift. A dedicated server runs in-process with the mod; the test
 * client joins it as a vanilla client (PolymerHelloMixin), accepts the pack, hovers above a rift
 * and looks straight down at it.
 *
 * The painted sprite is purple only; the shader adds teal. Teal on screen therefore proves the
 * pack's item shader ran, and a change between two frames proves it moves.
 *
 * Run: {@code ./gradlew :mods:metacraft-void-anchor:runClientGameTest} (opens a window).
 * Screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public final class VoidAnchorClientTests implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25601");
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
				server.runCommand("tp Tester 0.5 -50 0.5 0 90");   // hover, looking straight down
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);
				server.runCommand("voidanchor rift 0.5 -57 0.5");
				ctx.waitTicks(20);   // past the opening animation
				Path first = ctx.takeScreenshot(TestScreenshotOptions.of("rift_a").withSize(1920, 1080));
				ctx.waitTicks(6);
				Path second = ctx.takeScreenshot(TestScreenshotOptions.of("rift_b").withSize(1920, 1080));

				BufferedImage a = read(first), b = read(second);
				int box = (300 * 300);
				int purple = count(a, (r, g, bl) -> bl > g + 40 && r > g + 20);
				int teal = count(a, (r, g, bl) -> g > r + 40 && bl > r + 40);
				int moved = changed(a, b, 24);
				int[] quarterTurn = quarterTurnMismatch(a, 95, 40);
				System.out.println("[void-anchor-clienttest] purple=" + purple + " teal=" + teal + " moved=" + moved + " of " + box
						+ "; quarter-turn mismatch " + quarterTurn[0] + " of " + quarterTurn[1]);
				if (purple < box / 50) throw new AssertionError("no rift on screen: " + purple + " purple pixels in the centre");
				if (teal < box / 300) throw new AssertionError("the rift shader did not run: " + teal + " teal pixels in the centre");
				if (moved < box / 200) throw new AssertionError("the rift does not move: " + moved + " pixels changed between frames");
				// Which vertex of the quad item.vsh calls corner 0 depends on where the draw starts in a
				// shared buffer, so the pattern must look the same turned a quarter: then a shifted
				// corner order cannot make it jump.
				if (quarterTurn[0] > quarterTurn[1] * 15 / 100) {
					throw new AssertionError("the rift changes when turned a quarter: " + quarterTurn[0] + " of " + quarterTurn[1] + " pixels differ");
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

	/** Pixels in a disc at the screen's centre that differ by more than {@code step} from the pixel a quarter turn round; and the disc's size. */
	private static int[] quarterTurnMismatch(BufferedImage img, int radius, int step) {
		int n = 0, total = 0;
		int cx = img.getWidth() / 2, cy = img.getHeight() / 2;
		for (int dy = -radius; dy < radius; dy++) {
			for (int dx = -radius; dx < radius; dx++) {
				if (dx * dx + dy * dy > radius * radius) continue;
				total++;
				int p = img.getRGB(cx + dx, cy + dy), q = img.getRGB(cx - dy, cy + dx);
				for (int s = 0; s <= 16; s += 8) {
					if (Math.abs(((p >> s) & 255) - ((q >> s) & 255)) > step) {
						n++;
						break;
					}
				}
			}
		}
		return new int[] {n, total};
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
