package se.metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.MobOvvar;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.content.item.OvveItem;
import se.metacraft.ovvar.pack.Combos;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * What a vanilla client sees of a mob's ovve and frack. The garments' patches are all LOOT, so they
 * ride in the dye colour and show without any pack push: the client takes no resource-pack offer
 * after its initial join. Husks stand in for the mobs (they do not burn in daylight).
 *
 * Judged by the colours the patches leave (Nyckeln 0x0 green, TMEIT blue), none near Data's cerise.
 * Runs in the cloud under software rendering, so the checks are tolerant. Never run locally.
 */
public final class MobOvvarClientTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (ClientTestSupport.skipUnderShaderpack("MobOvvarClientTests")) return;
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25601");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "easy");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ClientTestSupport.acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");

				// The garments are made on the server; the halves' combinations must stay unknown to the pack.
				server.runOnServer(mc -> {
					ServerLevel level = mc.getPlayerList().getPlayers().getFirst().level();
					ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
					OvveItem.setTopUp(ovve, true);
					Looks.setSewn(ovve, SpotPlacements.fromList(List.of(
							Placement.parse("front_top_left.nyckeln0x0"),
							Placement.parse("front_low_left.tmeit"),
							Placement.parse("leg_front_top_r.nyckeln0x0"))).getOrThrow());
					ovve.set(ModComponents.MOB_OVVE, Unit.INSTANCE);
					husk(level, 0.5, ovve);

					ItemStack frack = new ItemStack(ModContent.ovve(Chapter.MEDIA));
					Looks.setSewn(frack, SpotPlacements.fromList(List.of(Placement.parse("front_top_left.nyckeln0x0"))).getOrThrow());
					frack.set(ModComponents.MOB_OVVE, Unit.INSTANCE);
					husk(level, 6.5, frack);

					for (ItemStack garment : List.of(ovve, frack)) {
						for (Piece piece : Piece.values()) {
							var placements = SpotPlacements.asPlacementList(Looks.sewn(garment, piece));
							if (placements.isEmpty()) continue;
							if (Combos.isKnown(piece, Placement.combo(placements))) {
								throw new AssertionError("a mob's " + garment.getItem() + " " + piece + " was queued for the pack");
							}
						}
					}
				});
				conn.waitForClientboundPackets();
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				long pushesBefore = ServerPackActivity.pushes();   // after the initial join: no more may follow

				server.runCommand("tp Tester 0.5 -60 0.5 0 0");   // look south (+Z, yaw 0) at the husk
				conn.waitForClientboundPackets();
				ctx.waitTicks(100);
				BufferedImage ovveShot = read(ctx.takeScreenshot(TestScreenshotOptions.of("mob_ovve_front").withSize(1920, 1080)));
				Region box = Region.subjectBox(ovveShot);
				ClientTestSupport.assertNoChecker(ovveShot, box.x0(), box.y0(), box.x1(), box.y1(), "mob ovve");
				assertPresent(ovveShot, box, "nyckeln 0x0 (green) on the mob's ovve", (r, g, bl) -> g > 100 && g > r + 60 && bl < 140, 20);
				assertPresent(ovveShot, box, "tmeit (blue) on the mob's ovve", (r, g, bl) -> bl > 150 && bl > r + 70 && bl > g + 40, 20);

				server.runCommand("tp Tester 6.5 -60 0.5 0 0");
				conn.waitForClientboundPackets();
				ctx.waitTicks(100);
				BufferedImage frackShot = read(ctx.takeScreenshot(TestScreenshotOptions.of("mob_frack_front").withSize(1920, 1080)));
				Region fbox = Region.subjectBox(frackShot);
				ClientTestSupport.assertNoChecker(frackShot, fbox.x0(), fbox.y0(), fbox.x1(), fbox.y1(), "mob frack");
				assertPresent(frackShot, fbox, "nyckeln 0x0 (green) on the mob's frack", (r, g, bl) -> g > 100 && g > r + 60 && bl < 140, 20);

				long pushed = ServerPackActivity.pushes() - pushesBefore;
				if (pushed != 0) throw new AssertionError("the client was offered " + pushed + " resource pack(s) after joining; a mob's garment must need none");
			}
		}
	}

	/** A husk (no AI, persistent) at (x, -60, 3.5) facing north, wearing the marked garment. */
	private static void husk(ServerLevel level, double x, ItemStack garment) {
		Husk husk = new Husk(EntityTypes.HUSK, level);
		husk.setPos(x, -60, 3.5);
		husk.setYRot(180); husk.setYBodyRot(180); husk.setYHeadRot(180);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		MobOvvar.wear(husk, garment);
		level.addFreshEntity(husk);
	}

	private interface Rgb { boolean test(int r, int g, int b); }

	private record Region(int x0, int y0, int x1, int y1) {
		/** The subject's box around the screen's centre, as in OvvarClientTests. */
		static Region subjectBox(BufferedImage img) {
			int w = img.getWidth(), h = img.getHeight();
			return new Region(w / 2 - 150, h / 2 - 40, w / 2 + 150, h / 2 + 185);
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
		for (int y = in.y0(); y < in.y1(); y++) {
			for (int x = in.x0(); x < in.x1(); x++) {
				int p = img.getRGB(x, y);
				if (match.test((p >> 16) & 255, (p >> 8) & 255, p & 255)) n++;
			}
		}
		if (n < atLeast) throw new AssertionError(what + ": " + n + " matching pixel(s) in the subject box, expected at least " + atLeast);
	}
}
