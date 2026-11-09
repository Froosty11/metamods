package se.metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.item.OvveItem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Under an Iris shaderpack: an unpatched pack draws the magenta/black checker that fills the original
 * right-limb box of a garment texture (the ovvar shader reads the copy beside it), a pack patched by
 * OvvarShaderPatcher does not. Runs only with {@code -Dovvar.iris.expect=checker|art} (see the README,
 * "Client test under a shaderpack"); otherwise it does nothing and the vanilla client run is unchanged.
 */
public final class OvvarShaderpackClientTests implements FabricClientGameTest {
	/** #FF00FF, the Spot.CHECKER_A colour. */
	private static final int MAGENTA_R = 255, MAGENTA_G = 0, MAGENTA_B = 255;
	private static final int TOLERANCE = 40;
	private static final double CHECKER_SHARE = 0.30;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String expect = System.getProperty("ovvar.iris.expect");
		if (expect == null || expect.isEmpty()) return;
		if (!expect.equals("checker") && !expect.equals("art")) {
			throw new AssertionError("ovvar.iris.expect must be checker or art, not " + expect);
		}
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25598");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ctx.waitTicks(20);
				ctx.runOnClient(client -> assertIrisLoadedPack());
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");
				server.runOnServer(mc -> {
					ServerLevel level = mc.getPlayerList().getPlayers().getFirst().level();
					ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
					OvveItem.setTopUp(ovve, true);   // nothing sewn: the bare garment
					Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, level);
					m.setPos(0.5, -60, 3.5);
					m.setYRot(180); m.setYBodyRot(180); m.setYHeadRot(180);
					m.setItemSlot(EquipmentSlot.LEGS, ovve);
					ItemStack top = new ItemStack(ModContent.top(Chapter.DATA));
					top.set(ModComponents.PATCHES, ovve.get(ModComponents.PATCHES));
					m.setItemSlot(EquipmentSlot.CHEST, top);
					level.addFreshEntity(m);
				});
				ctx.waitTicks(40);   // the pack, if the server sends one
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(100);
				Path shot = ctx.takeScreenshot(TestScreenshotOptions.of("ovvar_iris_leg_" + expect).withSize(1920, 1080));
				BufferedImage img = read(shot);
				// The mannequin faces the camera, so its right leg is on the screen's left of centre: the
				// 4-model-pixel-wide column ending at the middle, in the lower part of the subject box
				// (the box is calibrated in OvvarClientTests: ~19 screen px per model pixel).
				OvvarClientTests.Region box = OvvarClientTests.Region.subjectBox(img);
				int cx = img.getWidth() / 2;
				int x0 = cx - 66, x1 = cx - 8, y0 = box.y1() - 85, y1 = box.y1() - 10;
				int near = 0, total = 0;
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						int p = img.getRGB(x, y);
						total++;
						if (Math.abs(((p >> 16) & 255) - MAGENTA_R) <= TOLERANCE
								&& Math.abs(((p >> 8) & 255) - MAGENTA_G) <= TOLERANCE
								&& Math.abs((p & 255) - MAGENTA_B) <= TOLERANCE) near++;
					}
				}
				double share = near / (double) total;
				System.out.println("[ovvar-clienttest] iris " + expect + ": " + near + "/" + total + " right-leg pixels near #FF00FF (" + share + ")");
				if (expect.equals("checker") && share < CHECKER_SHARE) {
					throw new AssertionError("expected the unpatched pack's checker on the right leg: only " + near + "/" + total + " pixels near #FF00FF");
				}
				if (expect.equals("art") && near > 0) {
					throw new AssertionError("expected no checker on the right leg of a patched pack: " + near + "/" + total + " pixels near #FF00FF");
				}
			}
		}
	}

	/** Iris falls back silently when a pack fails to load, so ask it: a pack in use, and the one -Piris named. */
	private static void assertIrisLoadedPack() {
		String want = System.getProperty("ovvar.iris.pack");
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			boolean inUse = (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
			Object name = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null);
			System.out.println("[ovvar-clienttest] iris pack in use=" + inUse + " name=" + name + " wanted=" + want);
			if (!inUse) throw new AssertionError("Iris is not running a shaderpack (it falls back silently when one fails to load)");
			if (want != null && (name == null || !want.replaceAll("\\.zip$", "").equals(name.toString().replaceAll("\\.zip$", "")))) {
				throw new AssertionError("Iris runs the pack " + name + ", not " + want);
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Iris is not on the test client's classpath (run with -Piris=<pack.zip>)", e);
		}
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read screenshot " + path, e);
		}
	}
}
