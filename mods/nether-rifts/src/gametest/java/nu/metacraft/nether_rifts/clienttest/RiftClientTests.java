package nu.metacraft.nether_rifts.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.ConfirmScreen;
import nu.metacraft.nether_rifts.mob.RiftEntities;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

/**
 * A vanilla client (no Polymer handshake) joins an in-process server and looks at rifts: a 2×3 portal at
 * each stage (and its membrane staying put on the world as the camera steps aside), an L-shaped rift (merged panels must join without a seam), a 2×3 portal on the Z plane, and a
 * void-anchor rift in the same run (the shared item shader must draw both). It also looks at the War Drummer and a
 * war ghast carrying a Ghast Bombardier, and from the side at each of their swings, which must go forward.
 */
public final class RiftClientTests implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (PanoramaCapture.CAPTURING) {
			return;
		}
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", String.valueOf(freePort()));
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
				worldLocked(ctx, server, conn);
				int[] purple = new int[3];
				int[] nether = new int[3];
				String[] stages = {"lit", "unstable", "torn"};
				for (int i = 0; i < stages.length; i++) {
					String stage = stages[i];
					server.runCommand("rifts stage 0 -58 0 " + stage);
					ctx.waitTicks(10);
					Path a = ctx.takeScreenshot(TestScreenshotOptions.of("rift_" + stage + "_a").withSize(1920, 1080));
					ctx.waitTicks(5);
					Path b = ctx.takeScreenshot(TestScreenshotOptions.of("rift_" + stage + "_b").withSize(1920, 1080));
					var first = read(a);
					int rift = count(first, RiftClientTests::riftPixel);
					int moved = changed(first, read(b), 24);
					purple[i] = count(first, RiftClientTests::purplePixel);
					nether[i] = count(first, RiftClientTests::netherPixel);
					System.out.println("[nether-rifts-clienttest] " + stage + " rift=" + rift + " purple=" + purple[i] + " nether=" + nether[i] + " moved=" + moved);
					check(rift >= BOX / 20, "no rift on screen at " + stage + ": " + rift + " rift pixels");
					check(moved >= BOX / 200, "the rift doesn't move at " + stage + ": " + moved + " pixels changed");
				}
				// The fallback sprite (the panorama, stretched) looks the same at every stage; only the shader
				// cracks the membrane open as the rift tears. More nether and less purple prove it ran.
				if (nether[2] < 2000 || nether[2] < nether[0] * 2) {
					check(false, "the torn rift shows no more nether than the lit one (shader not running?): nether lit=" + nether[0] + " torn=" + nether[2]);
				}
				if (purple[2] * 3 > purple[0] * 2) {
					check(false, "the torn rift's membrane isn't mostly gone: purple lit=" + purple[0] + " torn=" + purple[2]);
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

				// the War Drummer, 4 blocks in front of the camera, facing it (yaw -90 is east; entities face their yaw, and the camera is east of it)
				server.runCommand("difficulty easy");
				server.runCommand("time set noon");
				server.runCommand("summon nether_rifts:war_drummer -20.5 -60 0.5 {Rotation:[-90f,0f],PersistenceRequired:1b}");
				server.runCommand("tp Tester -16.5 -58.5 0.5 90 10");
				ctx.waitTicks(25);
				var drummerA = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_a").withSize(1920, 1080)));
				ctx.waitTicks(5);
				var drummerB = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_b").withSize(1920, 1080)));
				int gold = count(drummerA, (r, g, b) -> r > 200 && g > 150 && b < 110);
				int drumMoved = changed(drummerA, drummerB, 24);
				System.out.println("[nether-rifts-clienttest] drummer gold=" + gold + " moved=" + drumMoved);
				check(gold >= 300, "the War Drummer's gold helm and drum rims aren't on screen: " + gold);
				check(drumMoved >= 200, "the War Drummer doesn't animate: " + drumMoved);
				server.runCommand("kill @e[type=nether_rifts:war_drummer]");

				// the War Drummer from its side, mid big beat, away from the first one's death puff: it faces east and the
				// camera looks north from 4 blocks, eye at its shoulders, so its front is right of the screen centre (about
				// 12 px a model unit). Held on frame 4 (0.2 s, arms at 110 degrees), the arms and sticks stand out ahead of
				// its snout and above the drum; a backward swing would put them behind its back. Frame 0 (arms still) is
				// the shot to compare with.
				server.runCommand("tp Tester -20.5 -60.37 -26.5 180 0");
				server.runCommand("summon nether_rifts:war_drummer -20.5 -60 -30.5 {Rotation:[-90f,0f],NoAI:1b,PersistenceRequired:1b}");
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);
				AnimationHold.hold(server, RiftEntities.WAR_DRUMMER, "big_beat", 0);
				ctx.waitTicks(10);
				var drumRest = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_side_rest").withSize(1920, 1080)));
				AnimationHold.hold(server, RiftEntities.WAR_DRUMMER, "big_beat", 4);
				ctx.waitTicks(10);
				var drumBeat = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_side_beat").withSize(1920, 1080)));
				AnimationHold.release(server);
				swingsForward("the War Drummer's big beat", drumRest, drumBeat, 60, 290, 300, 590);
				server.runCommand("kill @e[type=nether_rifts:war_drummer]");

				// a war ghast in its harness, a Ghast Bombardier in the saddle, 12 blocks off and both facing the camera
				server.runCommand("summon nether_rifts:war_ghast -40.5 -52 0.5 {Rotation:[-90f,0f],NoAI:1b,PersistenceRequired:1b,Passengers:[{id:\"nether_rifts:ghast_bombardier\",Rotation:[-90f,0f],NoAI:1b,PersistenceRequired:1b}]}");
				server.runCommand("tp Tester -28.5 -50 0.5 90 5");
				ctx.waitTicks(25);
				var ghastShot = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_ghast").withSize(1920, 1080)));
				// the red harness's drapes as lit in game (about 80..105, 22..33, 21..25); the rider's own brown would pass a
				// brown-strap check on its own, and neither the ghast, the sky nor the grass has this red
				int harness = countAll(ghastShot, (r, g, b) -> r > 90 && r > 2 * g && r > 2 * b);
				// the light-blue stained glass lenses as lit in game (about 53, 79, 112), on the rider's face (screen
				// x 930..990, y 365..450); the box keeps out the noon sky (b = 255) and the translucent panel top left
				int lens = countIn(ghastShot, 900, 360, 1020, 450, (r, g, b) -> r < 100 && g > r + 15 && b > g + 20 && b < 160);
				System.out.println("[nether-rifts-clienttest] war ghast harness=" + harness + " lens=" + lens);
				check(harness >= 1000, "the harness isn't drawn on the war ghast: " + harness);
				check(lens >= 20, "the Bombardier's goggles aren't on screen: " + lens);

				// the same pair from its side, mid drop_bomb: the camera looks north from 5 blocks, eye at the rider's
				// shoulders (about 10 px a model unit), its front right of the centre. Held on frame 4 (0.2 s, on the
				// way down from 150 degrees, the arm at 107), the arm and its magma charge stand out ahead of the snout,
				// above the seated legs; a backward swing would put them behind its back. Frame 0 is the shot to compare with.
				server.runCommand("tp Tester -40.5 -48.77 5.5 180 0");
				ctx.waitTicks(20);
				AnimationHold.hold(server, RiftEntities.GHAST_BOMBARDIER, "drop_bomb", 0);
				ctx.waitTicks(10);
				var throwRest = read(ctx.takeScreenshot(TestScreenshotOptions.of("bombardier_side_rest").withSize(1920, 1080)));
				AnimationHold.hold(server, RiftEntities.GHAST_BOMBARDIER, "drop_bomb", 4);
				ctx.waitTicks(10);
				var throwShot = read(ctx.takeScreenshot(TestScreenshotOptions.of("bombardier_side_throw").withSize(1920, 1080)));
				AnimationHold.release(server);
				swingsForward("the Bombardier's drop_bomb", throwRest, throwShot, 58, 200, 380, 600);
				server.runCommand("kill @e[type=nether_rifts:war_ghast]");
				server.runCommand("kill @e[type=nether_rifts:ghast_bombardier]");
				server.runCommand("difficulty peaceful");
				server.runCommand("time set midnight");

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

	/** Anything the rift draws: membrane, rims, edge or the nether behind; none of which a flat world at night has. */
	static boolean riftPixel(int r, int g, int b) {
		return purplePixel(r, g, b) || netherPixel(r, g, b) || (r > 200 && b > 150);
	}

	/** The membrane's purples (reddened at stage 3) and the edge's hot pink: blue and red well above green. */
	static boolean purplePixel(int r, int g, int b) {
		return b > g + 40 && r > g + 20;
	}

	/** The panorama's nether through the cracks: lava, netherrack, crimson, the red fog; red over blue, green not far under blue. */
	static boolean netherPixel(int r, int g, int b) {
		return r > b + 25 && g + 12 >= b && r > 40 && r < 240;
	}

	/**
	 * The membrane must be fixed to the world, not the screen. The camera steps a quarter block sideways without
	 * turning, so the rift slides across the screen; a pattern on the world slides with it, one on the screen
	 * stays put. Proxy: the shift of the rift's left edge (the geometry) against the shift that best matches a
	 * window well inside the rift between the two shots (the pattern: cracks, rims, swirl). They must agree,
	 * and the geometry must have moved.
	 */
	private static void worldLocked(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		server.runCommand("rifts stage 0 -58 0 lit");
		ctx.waitTicks(20);
		var a = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_locked_a").withSize(1920, 1080)));
		server.runCommand("tp Tester 0.75 -58 4 180 0");
		conn.waitForClientboundPackets();
		ctx.waitTicks(3);
		var b = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_locked_b").withSize(1920, 1080)));
		server.runCommand("tp Tester 0.5 -58 4 180 0");
		conn.waitForClientboundPackets();
		ctx.waitTicks(3);
		int[] boxA = riftBox(a);
		if (boxA == null) {
			check(false, "no rift on screen to step past");
			return;
		}
		int geometry = leftEdge(b, boxA) - leftEdge(a, boxA);
		int x0 = boxA[0] + 70, x1 = boxA[2] - 70, y0 = boxA[1] + 40, y1 = boxA[3] - 40;
		int best = 0;
		double bestDiff = Double.MAX_VALUE, atZero = 0;
		for (int dx = -120; dx <= 120; dx++) {
			double diff = 0;
			for (int y = y0; y < y1; y += 2) {
				for (int x = x0; x < x1; x += 2) {
					int p = a.getRGB(x, y), q = b.getRGB(x + dx, y);
					diff += Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) + Math.abs((p & 255) - (q & 255));
				}
			}
			diff /= ((y1 - y0) / 2.0) * ((x1 - x0) / 2.0);
			if (dx == 0) atZero = diff;
			if (diff < bestDiff) {
				bestDiff = diff;
				best = dx;
			}
		}
		System.out.println("[nether-rifts-clienttest] locked box=" + Arrays.toString(boxA) + " geometry=" + geometry + " pattern=" + best
				+ " diff=" + String.format("%.1f", bestDiff) + " diffAtZero=" + String.format("%.1f", atZero));
		check(Math.abs(geometry) >= 20, "the camera step didn't move the rift on screen: " + geometry + " px");
		check(Math.abs(best - geometry) <= 4, "the membrane doesn't move with the world: rift moved " + geometry + " px, pattern " + best + " px");
	}

	/** The rift's bounding box (x0, y0, x1, y1) in the middle of the screen, chat and toasts left out; null if there's none. */
	private static int[] riftBox(BufferedImage img) {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
		for (int y = 300; y < img.getHeight(); y++) {
			for (int x = 600; x < 1400; x++) {
				int p = img.getRGB(x, y);
				if (riftPixel((p >> 16) & 255, (p >> 8) & 255, p & 255)) {
					x0 = Math.min(x0, x);
					y0 = Math.min(y0, y);
					x1 = Math.max(x1, x);
					y1 = Math.max(y1, y);
				}
			}
		}
		return x1 < 0 ? null : new int[]{x0, y0, x1, y1};
	}

	/** The median over the box's middle rows of the leftmost rift pixel; -1 if no row has one. */
	private static int leftEdge(BufferedImage img, int[] box) {
		var lefts = new ArrayList<Integer>();
		for (int y = box[1] + 40; y < box[3] - 40; y += 4) {
			for (int x = 600; x < 1400; x++) {
				int p = img.getRGB(x, y);
				if (riftPixel((p >> 16) & 255, (p >> 8) & 255, p & 255)) {
					lefts.add(x);
					break;
				}
			}
		}
		if (lefts.isEmpty()) return -1;
		Collections.sort(lefts);
		return lefts.get(lefts.size() / 2);
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

	/** Pixels anywhere on screen that match. */
	private static int countAll(BufferedImage img, Rgb rgb) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getRGB(x, y);
				if (rgb.test((p >> 16) & 255, (p >> 8) & 255, p & 255)) n++;
			}
		}
		return n;
	}

	/** Pixels in the box [x0, x1) × [y0, y1) that match. */
	private static int countIn(BufferedImage img, int x0, int y0, int x1, int y1, Rgb rgb) {
		int n = 0;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
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

	/**
	 * A swing seen from the side, the mob facing right of the screen centre: the pixels that moved between the
	 * still shot and the swing, in a box {@code near..far} pixels ahead of the centre, must far outnumber those in
	 * the same box mirrored behind it. Forward swings moved 4200 (drummer) and 2043 (Bombardier) ahead, 238 and 0
	 * behind; a backward one leaves only the head's nod ahead.
	 */
	private static void swingsForward(String what, BufferedImage still, BufferedImage swing, int near, int far, int y0, int y1) {
		int cx = still.getWidth() / 2;
		int ahead = changedIn(still, swing, cx + near, y0, cx + far, y1, 24);
		int behind = changedIn(still, swing, cx - far, y0, cx - near, y1, 24);
		System.out.println("[nether-rifts-clienttest] " + what + " ahead=" + ahead + " behind=" + behind);
		check(ahead >= 1000 && behind * 4 <= ahead, what + " doesn't swing forward: " + ahead + " px moved ahead of it, " + behind + " behind");
	}

	/** Pixels in the box [x0, x1) × [y0, y1) that changed by more than {@code step} in some channel. */
	private static int changedIn(BufferedImage a, BufferedImage b, int x0, int y0, int x1, int y1, int step) {
		int n = 0;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
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

	/** A port nothing listens on, so runs in several checkouts at once don't collide. */
	static int freePort() {
		try (var socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
