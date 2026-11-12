package se.metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.server.packs.repository.PackRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Chapter text colours on nametags: with the chapter-text-colors resource pack on (and, under {@code -Piris}, the
 * shaderpack patched by ovvar-client), a dark purple nametag must draw like one in cerise and a dark blue one like
 * one in laserviolet. Runs only with {@code -PtextColours} (it puts the test copy of the pack in the run dir and sets
 * {@code -Dovvar.textColours}); see the README.
 *
 * <p>The judgement is relative, never against fixed RGB: a shaderpack lights, fogs and tone-maps nametag text (BSL
 * draws it pale and sky-tinted), so no absolute colour is right under every pack. Each chapter tag is compared with
 * two reference tags drawn the same way at the same spot: the colour it should become ({@code #E83D84},
 * {@code #CC99FF}, which no patch touches) and a colour one step off its stock one ({@code #AB00AB}, {@code #0000AB}:
 * where it would land unchanged, since the exact match leaves these alone). It must be much nearer the first. A white
 * wall behind the tags keeps the translucent plate's background still (no clouds drifting through it).
 *
 * <p>The glyph colour is the mean of the tag's pixels farthest from its plate (the box's median colour). Not a
 * saturation measure: under BSL the bluish plate is more saturated than the pale glyphs.
 */
public final class NametagColoursClientTests implements FabricClientGameTest {
	/** The cases: the chapter colour, what it should draw like, and its stock colour one step off. */
	private static final List<String[]> CASES = List.of(
			new String[]{"dark_purple", "#E83D84", "#AB00AB"},
			new String[]{"dark_blue", "#CC99FF", "#0000AB"});
	/** The chapter tag must be at least this many times nearer its target than its stock neighbour. */
	private static final int MARGIN = 3;
	/** The two references must differ by at least this (max channel) or the shot cannot tell them apart. */
	private static final int MIN_SPREAD = 20;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String pack = System.getProperty("ovvar.textColours");
		if (pack == null || pack.isEmpty()) return;
		String packId = "file/" + pack;
		ctx.runOnClient(client -> {
			PackRepository repo = client.getResourcePackRepository();
			repo.reload();
			if (!repo.isAvailable(packId)) throw new AssertionError("the resource pack " + packId + " is not in the run dir (run with -PtextColours)");
			repo.addPack(packId);
			client.options.updateResourcePacks(repo);
		});
		ctx.waitTicks(5);   // the reload's overlay goes up on a later frame
		ctx.waitFor(client -> client.gui.overlay() == null, 20 * 60);
		ctx.waitTicks(20);
		ctx.runOnClient(client -> {
			System.out.println("[ovvar-clienttest] text colours: packs " + client.getResourcePackRepository().getSelectedIds()
					+ ", shaderpack " + System.getProperty("ovvar.iris.pack", "none"));
			if (!client.getResourcePackRepository().getSelectedIds().contains(packId)) throw new AssertionError(packId + " did not stay selected");
		});
		try {
			measure(ctx);
		} finally {
			// the tests after this one draw text too (lore colours): leave the client's packs as they were
			ctx.runOnClient(client -> {
				PackRepository repo = client.getResourcePackRepository();
				repo.removePack(packId);
				client.options.updateResourcePacks(repo);
			});
			ctx.waitTicks(5);   // the reload's overlay goes up on a later frame
		ctx.waitFor(client -> client.gui.overlay() == null, 20 * 60);
		}
	}

	private static void measure(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25597");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ClientTestSupport.acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("gamerule advance_weather false");
				server.runCommand("weather clear");
				server.runCommand("time set noon");
				server.runCommand("fill -6 -61 7 7 -52 7 minecraft:white_concrete");
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				BufferedImage base = shot(ctx, "text_colours_base");
				for (String[] c : CASES) {
					int[] chapter = glyph(ctx, server, base, c[0]);
					int[] target = glyph(ctx, server, base, c[1]);
					int[] stock = glyph(ctx, server, base, c[2]);
					int toTarget = distance(chapter, target), toStock = distance(chapter, stock), spread = distance(target, stock);
					System.out.println("[ovvar-clienttest] text colours: " + c[0] + " glyph " + Arrays.toString(chapter) + ", like " + c[1]
							+ " " + Arrays.toString(target) + " (" + toTarget + " off), stock " + c[2] + " " + Arrays.toString(stock) + " (" + toStock + " off)");
					if (spread < MIN_SPREAD) {
						throw new AssertionError("the references " + c[1] + " and " + c[2] + " drew only " + spread + " apart: the shot cannot judge " + c[0]);
					}
					if (toTarget * MARGIN > toStock) {
						throw new AssertionError("a " + c[0] + " nametag draws like its stock colour, not like " + c[1] + ": " + toTarget
								+ " from " + c[1] + ", " + toStock + " from " + c[2] + " (glyph " + Arrays.toString(chapter) + ")");
					}
				}
				server.runCommand("kill @e[type=armor_stand]");
			}
		}
	}

	/** A nametag in {@code colour} (a name or #RRGGBB) on an invisible armour stand three blocks ahead, its glyph colour. */
	private static int[] glyph(ClientGameTestContext ctx, TestDedicatedServerContext server, BufferedImage base, String colour) {
		server.runCommand("kill @e[type=armor_stand]");
		server.runCommand("summon armor_stand 0.5 -60 3.5 {Invisible:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:{text:\"Tester2\",color:\""
				+ colour + "\"}}");
		ctx.waitTicks(30);
		BufferedImage img = shot(ctx, "text_colours_" + colour.replace("#", "hex_"));
		int[] box = changed(base, img);
		if (box == null) throw new AssertionError("no nametag drew for " + colour);
		List<int[]> px = new ArrayList<>();
		for (int y = box[1]; y <= box[3]; y++) {
			for (int x = box[0]; x <= box[2]; x++) {
				int p = img.getRGB(x, y);
				px.add(new int[]{(p >> 16) & 255, (p >> 8) & 255, p & 255});
			}
		}
		px.sort((a, b) -> Integer.compare(a[0] + a[1] + a[2], b[0] + b[1] + b[2]));
		int[] plate = px.get(px.size() / 2);
		px.sort((a, b) -> Integer.compare(distance(b, plate), distance(a, plate)));
		int n = Math.max(1, px.size() / 10);
		long r = 0, g = 0, b = 0;
		for (int i = 0; i < n; i++) {
			r += px.get(i)[0];
			g += px.get(i)[1];
			b += px.get(i)[2];
		}
		return new int[]{(int) (r / n), (int) (g / n), (int) (b / n)};
	}

	/**
	 * The bounding box {x0, y0, x1, y1} of the pixels that changed by more than 40 in a channel, or null. Only the
	 * middle of the frame (the tag sits above its centre, on the wall), clear of the sky at the wall's sides and
	 * of the player's hand.
	 */
	private static int[] changed(BufferedImage a, BufferedImage b) {
		int w = a.getWidth(), h = a.getHeight();
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
		for (int y = h / 8; y < h * 5 / 8; y++) {
			for (int x = w * 3 / 10; x < w * 7 / 10; x++) {
				if (distance(rgb(a.getRGB(x, y)), rgb(b.getRGB(x, y))) > 40) {
					x0 = Math.min(x0, x);
					y0 = Math.min(y0, y);
					x1 = Math.max(x1, x);
					y1 = Math.max(y1, y);
				}
			}
		}
		return x1 < 0 ? null : new int[]{x0, y0, x1, y1};
	}

	private static int[] rgb(int p) {
		return new int[]{(p >> 16) & 255, (p >> 8) & 255, p & 255};
	}

	/** The largest channel difference. */
	private static int distance(int[] a, int[] b) {
		return Math.max(Math.abs(a[0] - b[0]), Math.max(Math.abs(a[1] - b[1]), Math.abs(a[2] - b[2])));
	}

	private static BufferedImage shot(ClientGameTestContext ctx, String name) {
		Path path = ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080));
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read screenshot " + path, e);
		}
	}
}
