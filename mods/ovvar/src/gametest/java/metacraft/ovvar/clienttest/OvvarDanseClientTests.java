package metacraft.ovvar.clienttest;

import metacraft.ovvar.compat.danse.DanseHooks;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
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
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * What a gesture looks like in a patched ovve.
 *
 * <p>Danse plays a gesture by hiding the real player and animating a stand-in built of item
 * displays — one pixel per skin texel, the colours in {@code custom_model_data} — and it draws the
 * armour on that stand-in itself, out of the <em>server-side</em> stack. An ovve carries nothing
 * useful there: the asset, the dye colour and the trim are all added on the way out to a client. So
 * without the compat layer the stand-in wears no ovve at all, and with it the stand-in wears the
 * whole thing, patches and all.
 *
 * <p>That is exactly what this test photographs. It runs twice from the same build:
 * <pre>
 *   ./gradlew :mods:ovvar:runClientGameTest                                        # after
 *   JAVA_TOOL_OPTIONS=-Dovvar.danse.compat=false ./gradlew :mods:ovvar:runClientGameTest   # before
 * </pre>
 * With the layer off the screenshots are named {@code before*} and nothing is asserted — there is
 * nothing to assert, which is the point of the picture. With it on the patch colours must be there.
 *
 * <p>It returns at once when Danse is not loaded, so an ovvar checkout without the dev jar still
 * runs its client tests.
 */
public final class OvvarDanseClientTests implements FabricClientGameTest {

	/**
	 * The gesture. {@code grow} is eleven seconds and plays once (so it ends, which the last
	 * screenshot needs) and keeps the figure on the ground in front of the camera — {@code ascend},
	 * the longest, lifts it out of frame.
	 */
	private static final String GESTURE = "grow";

	/**
	 * Can Danse start a gesture in this JVM at all?
	 *
	 * <p><b>In a client JVM it cannot</b> — nothing to do with ovvar. Danse lists
	 * {@code LivingEntityAccessor} (and its four siblings) in the {@code "server"} section of
	 * {@code danse.mixins.json}, so on a client those mixins are never applied; but
	 * {@code GesturePlayerModelEntity.setup} casts the player to that accessor to read their
	 * equipment, and Mixin refuses to classload a mixin that was not applied:
	 *
	 * <pre>
	 *   IllegalClassLoadError: Illegal classload request for
	 *   de.tomalbrc.danse.mixin.LivingEntityAccessor. Mixin is defined in danse.mixins.json
	 *   and cannot be referenced directly
	 * </pre>
	 *
	 * The Fabric client game test harness runs its "dedicated" server <em>in the client's own
	 * JVM</em>, so that is the environment here and a gesture cannot be started. On a real
	 * dedicated server — where ovvar actually runs, and where {@code DansePixelsTests} exercises the
	 * same pixels — the section applies and gestures work.
	 *
	 * <p>The fix is one line — those five entries under {@code "mixins"} instead of {@code "server"}
	 * — and the dev jar in {@code libs/} carries it (see {@code libs/danse-LICENSE-NOTICE.txt}), so
	 * here the gesture starts and the screenshots in {@code docs/danse/} come from this test. With
	 * an unpatched Danse this says why it cannot photograph anything rather than failing.
	 */
	private static boolean danseCanGestureHere() {
		try {
			Class.forName("de.tomalbrc.danse.mixin.LivingEntityAccessor", false,
					OvvarDanseClientTests.class.getClassLoader());
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!FabricLoader.getInstance().isModLoaded("danse")) return;
		if (!danseCanGestureHere()) {
			System.out.println("[ovvar-danse-clienttest] skipped: Danse's server-side mixins are not applied in a "
					+ "client JVM, so GesturePlayerModelEntity.setup cannot read the player's equipment. "
					+ "See danseCanGestureHere(). The pixels themselves are covered by DansePixelsTests.");
			return;
		}
		boolean compat = DanseHooks.active();
		String stage = compat ? "after" : "before";

		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25600");   // 25599 is the other client test's
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
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");

				// The Tester wears the ovve itself: a gesture is the wearer's own, and the stand-in is
				// built from the wearer's equipment. Four patches on the chest (more than the dye
				// colour carries), one on each sleeve and one on each leg.
				server.runOnServer(mc -> {
					ServerPlayer player = mc.getPlayerList().getPlayers().getFirst();
					ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
					OvveItem.setTopUp(ovve, true);
					Looks.setSewn(ovve, SpotPlacements.fromList(List.of(
							new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")),
							new Placement(Spot.FRONT_TOP_RIGHT, Patches.get("nyckeln0x0")),
							new Placement(Spot.FRONT_LOW_LEFT, Patches.get("it")),
							new Placement(Spot.FRONT_LOW_RIGHT, Patches.get("data")),
							new Placement(Spot.SLEEVE_OUT_TOP_L, Patches.get("itk")),
							new Placement(Spot.SLEEVE_OUT_TOP_R, Patches.get("it")),
							new Placement(Spot.LEG_OUT_TOP_L, Patches.get("itk")),
							new Placement(Spot.LEG_OUT_TOP_R, Patches.get("it")))).getOrThrow());
					player.setItemSlot(EquipmentSlot.LEGS, ovve);
				});
				ctx.waitTicks(40);            // the companion top goes on from the ovve's own tick
				acceptResourcePack(ctx);      // eight patches outgrow the dye colour: a pack is built and pushed
				conn.waitForClientboundPackets();

				// The gesture, started through Danse's own controller rather than its command: the
				// command swallows anything that goes wrong into a chat line, and a test wants the
				// stack trace.
				server.runOnServer(mc -> de.tomalbrc.danse.GestureController.onStart(
						mc.getPlayerList().getPlayers().getFirst(), GESTURE));
				ctx.waitTicks(60);            // the camera swings out behind the stand-in and settles
				conn.waitForClientboundPackets();

				Path mid = ctx.takeScreenshot(TestScreenshotOptions.of(stage).withSize(1920, 1080));

				// Let the gesture finish (grow is 11 s ≈ 220 ticks) and look at the real player again.
				ctx.waitTicks(220);
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				conn.waitForClientboundPackets();
				ctx.waitTicks(40);
				Path after = ctx.takeScreenshot(TestScreenshotOptions.of(stage + "_gesture_end").withSize(1920, 1080));

				if (!compat) return;   // the "before" run only takes the pictures

				BufferedImage gesturing = read(mid);
				Region box = Region.frame(gesturing);
				// Two colours that cannot come from the cerise garment, so their presence proves the
				// patches reached the stand-in — which, without the compat layer, wears no ovve at all.
				// ITK's green is the plain client test's probe too. The plain test's other probe, IT's
				// lilac, is deliberately not used here: on the 12×12 art it is a one-pixel diagonal, and
				// Danse draws one pixel per skin texel (two art pixels), so the downsample averages that
				// line into its dark-purple and white neighbours and no lilac survives. Data's yellow is a
				// flat 2×2-or-bigger region and comes through the average exactly.
				assertPresent(gesturing, box, "itk (green) on the stand-in",
						(r, g, b) -> g > 100 && g > r + 60 && b < 140, 20);
				assertPresent(gesturing, box, "data (yellow) on the stand-in",
						(r, g, b) -> r > 150 && g > 130 && b < 60 && r > b + 100, 20);

				// And once the gesture is over the wearer is dressed again — including the leg patches
				// that ride in the boots channel, which Danse's own end-of-gesture resend loses.
				BufferedImage ended = read(after);
				Region endBox = Region.frame(ended);
				assertPresent(ended, endBox, "itk (green) back on the player after the gesture",
						(r, g, b) -> g > 100 && g > r + 60 && b < 140, 20);
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

	/**
	 * The middle of the frame. Danse's gesture camera sits a few blocks behind and above the
	 * stand-in looking at it, so the figure fills the centre; this is a generous box around it
	 * rather than the tight one the mannequin test can afford, the stand-in being animated.
	 */
	record Region(int x0, int y0, int x1, int y1) {
		static Region frame(BufferedImage img) {
			int w = img.getWidth(), h = img.getHeight();
			return new Region(w / 2 - 400, h / 2 - 300, w / 2 + 400, h / 2 + 400);
		}
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read screenshot " + path, e);
		}
	}

	private static void assertPresent(BufferedImage img, Region in, String what, Rgb match, int atLeast) {
		int n = 0;
		for (int y = Math.max(0, in.y0()); y < Math.min(img.getHeight(), in.y1()); y++) {
			for (int x = Math.max(0, in.x0()); x < Math.min(img.getWidth(), in.x1()); x++) {
				int p = img.getRGB(x, y);
				if (match.test((p >> 16) & 255, (p >> 8) & 255, p & 255)) n++;
			}
		}
		if (n < atLeast) throw new AssertionError(what + ": " + n + " matching pixel(s), expected at least " + atLeast);
	}
}
