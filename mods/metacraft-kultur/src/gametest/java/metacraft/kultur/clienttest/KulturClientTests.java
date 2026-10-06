package metacraft.kultur.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ConfirmScreen;

import java.nio.file.Path;
import java.util.Properties;

/**
 * What a vanilla client sees of the chapter patterns. A dedicated server runs in-process with
 * kultur; the client joins it as a vanilla client (PolymerHelloMixin), accepts the pack, and looks
 * at a white banner with the ITK pattern, holds a shield with the Pirkko pattern, has the Draken
 * painting on the wall beside them, and — since the main-hand shield shows its back, not its face —
 * two item frames on the same wall holding a decorated shield (face-on) and the Pirkko pattern item
 * itself, so the shield and pattern-item textures are both actually visible in the shot. The
 * main-hand shield is kept anyway, even though first person shows only its back: it is evidence
 * that the component syntax equips a live item, not just that the frames render one. The
 * screenshot is judged by eye — open it and look.
 *
 * Run: {@code ./gradlew :mods:metacraft-kultur:runClientGameTest} (opens a window). Screenshots
 * land in {@code mods/metacraft-kultur/build/run/clientGameTest/screenshots}.
 */
public final class KulturClientTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25597");
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
				// A wall behind the scene so the painting has something to hang on, a banner in
				// front of it with ITK in black on white, and the painting on the wall to the east.
				server.runCommand("fill -3 -60 4 3 -56 4 minecraft:stone");
				server.runCommand("setblock 0 -60 3 minecraft:white_banner[rotation=8]{patterns:[{pattern:\"kultur:itk\",color:\"black\"}]}");
				server.runCommand("summon minecraft:painting 2.5 -58.5 3.9 {facing:2b,variant:\"kultur:draken\"}");
				// The main-hand shield shows its back in first person, so the shield's decorated face and
				// the raw pattern item are also placed in item frames on the wall, facing the camera.
				server.runCommand("summon minecraft:item_frame -1.5 -57.5 3.5 {Facing:2b,Item:{id:\"minecraft:shield\",count:1,components:{\"minecraft:base_color\":\"white\",\"minecraft:banner_patterns\":[{pattern:\"kultur:itk\",color:\"black\"},{pattern:\"kultur:pirkko\",color:\"red\"}]}}}");
				server.runCommand("summon minecraft:item_frame -1.5 -58.5 3.5 {Facing:2b,Item:{id:\"kultur:pirkko_banner_pattern\",count:1}}");
				// The Pirkko pattern on a shield in the main hand, held so it shows in first person.
				server.runCommand("item replace entity Tester weapon.mainhand with minecraft:shield[minecraft:base_color=\"white\",minecraft:banner_patterns=[{pattern:\"kultur:itk\",color:\"black\"},{pattern:\"kultur:pirkko\",color:\"red\"}]]");
				server.runCommand("tp Tester 0.5 -60 -2.5 0 5");   // look south (+Z) at the banner, painting and frames
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				Path shot = ctx.takeScreenshot(TestScreenshotOptions.of("kultur_banner_shield_painting").withSize(1920, 1080));
				System.out.println("[kultur-clienttest] screenshot: " + shot);
			}
		}
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}
}
