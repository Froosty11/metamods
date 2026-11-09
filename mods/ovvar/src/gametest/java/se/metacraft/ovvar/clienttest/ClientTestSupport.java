package se.metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConfirmScreen;

/** Helpers the client tests share. */
final class ClientTestSupport {
	private ClientTestSupport() {}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}

	/** Under -Piris the vanilla screenshot tests are skipped; OvvarShaderpackClientTests runs instead. */
	static boolean skipUnderShaderpack(String test) {
		String pack = System.getProperty("ovvar.iris.pack");
		if (pack == null || pack.isEmpty()) return false;
		System.out.println("[ovvar-clienttest] " + test + " skipped under shaderpack " + pack);
		return true;
	}
}
