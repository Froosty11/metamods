package nu.metacraft.config.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.dialog.DialogScreen;

import java.nio.file.Files;
import java.util.Properties;

/**
 * /config as a vanilla client sees it: the list, faster-minecarts' page, and a page whose file does
 * not load. Asserts only that each opens as a dialog; the pictures are for a person to judge.
 */
public final class ConfigScreenshots implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25604");
		props.setProperty("level-type", "minecraft:flat");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ctx.getInput().resizeWindow(1920, 1080);
				// "auto" (0) picks an oversized scale on a Retina host and the dialog's Save / Reset /
				// Back row falls below the fold; a fixed scale keeps the whole page on screen.
				ctx.runOnClient(client -> client.options.guiScale().set(2));
				conn.waitForChunksRender();
				server.runCommand("op Tester");
				shoot(ctx, server, conn, "execute as Tester run config", "config_main");
				shoot(ctx, server, conn, "execute as Tester run config faster_minecarts", "config_faster_minecarts");
				server.runOnServer(mc -> {
					try {
						// Every key the codec needs, one of them out of range.
						Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("faster_minecarts.json"), """
								{"global_faster_minecarts": false, "max_minecart_speed": -5, "max_minecart_speed_underwater": 45,
								"damage_factor": 43.2, "experimental_minecart_mode": "experimental"}""");
					} catch (Exception e) {
						throw new AssertionError(e);
					}
				});
				server.runCommand("reload");
				ctx.waitTicks(40);
				shoot(ctx, server, conn, "execute as Tester run config faster_minecarts", "config_load_error");
			}
		}
	}

	private static void shoot(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, String command, String name) {
		ctx.runOnClient(client -> client.gui.setScreen(null));
		ctx.waitTicks(5);
		server.runCommand(command);
		conn.waitForClientboundPackets();
		ctx.waitFor(client -> client.gui.screen() instanceof DialogScreen<?>, 20 * 10);
		ctx.runOnClient(client -> client.gui.toastManager().clear());
		ctx.getInput().setCursorPos(0, 0);
		ctx.waitTicks(5);
		ctx.takeScreenshot(TestScreenshotOptions.of(name));
	}
}
