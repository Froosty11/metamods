package se.metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** SCRATCH (cloud check only, never pushed to a product branch): nametag + chat colours under the chapter-text-colors pack. */
public final class NametagScratchClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!Files.exists(Path.of("/tmp/nametag.on"))) return;
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
				server.runCommand("time set noon");
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");
				ctx.runOnClient(client -> {
					client.options.setCameraType(CameraType.FIRST_PERSON);
					System.out.println("[nametag] resource packs selected: " + client.options.resourcePacks);
					System.out.println("[nametag] repo selected: " + client.getResourcePackRepository().getSelectedIds());
				});
				ctx.waitTicks(40);
				BufferedImage base = shot(ctx, "nametag_base");
				for (String c : new String[]{"dark_purple", "dark_blue", "light_purple", "blue"}) {
					server.runCommand("kill @e[type=armor_stand]");
					server.runCommand("summon armor_stand 0.5 -60 3.5 {CustomName:{text:\"Tester2\",color:\"" + c + "\"},CustomNameVisible:1b}");
					ctx.waitTicks(30);
					BufferedImage img = shot(ctx, "nametag_" + c);
					report("nametag " + c, base, img, 400, 100, 1520, 700);
				}
				server.runCommand("kill @e[type=armor_stand]");
				ctx.waitTicks(20);
				server.runCommand("tellraw @a {\"text\":\"Tester2\",\"color\":\"dark_purple\"}");
				ctx.waitTicks(10);
				BufferedImage chat = shot(ctx, "chat_dark_purple");
				report("chat dark_purple", base, chat, 0, 600, 900, 1080);
				server.runCommand("tellraw @a {\"text\":\"Tester2\",\"color\":\"dark_blue\"}");
				ctx.waitTicks(10);
				report("chat dark_blue(both lines)", base, shot(ctx, "chat_dark_blue"), 0, 600, 900, 1080);
			}
		}
	}

	private static BufferedImage shot(ClientGameTestContext ctx, String name) {
		Path p = ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080));
		try { return ImageIO.read(p.toFile()); } catch (IOException e) { throw new RuntimeException(e); }
	}

	/** Pixels differing from the base shot inside the box; for nametags keep the topmost cluster; mean of the brightest 5%. */
	private static void report(String what, BufferedImage base, BufferedImage img, int x0, int y0, int x1, int y1) {
		List<int[]> px = new ArrayList<>();
		int minY = Integer.MAX_VALUE;
		for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
			int p = img.getRGB(x, y), q = base.getRGB(x, y);
			int d = Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) + Math.abs((p & 255) - (q & 255));
			if (d > 60) { px.add(new int[]{x, y, (p >> 16) & 255, (p >> 8) & 255, p & 255}); minY = Math.min(minY, y); }
		}
		final int top = minY;
		List<int[]> sel = what.startsWith("nametag") ? px.stream().filter(a -> a[1] < top + 40).toList() : px;
		List<int[]> sorted = new ArrayList<>(sel);
		sorted.sort(Comparator.comparingInt((int[] a) -> a[2] + a[3] + a[4]).reversed());
		int n = Math.max(1, sorted.size() / 20);
		long r = 0, g = 0, b = 0;
		for (int i = 0; i < n && i < sorted.size(); i++) { r += sorted.get(i)[2]; g += sorted.get(i)[3]; b += sorted.get(i)[4]; }
		if (sorted.isEmpty()) { System.out.println("[nametag] " + what + ": NO differing pixels found"); return; }
		n = Math.min(n, sorted.size());
		System.out.println("[nametag] " + what + ": " + sel.size() + " diff px (topY=" + top + "), brightest " + n + " mean rgb = (" + r / n + "," + g / n + "," + b / n + ")");
	}
}
