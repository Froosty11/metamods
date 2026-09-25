package metacraft.ovvar.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Our Danse fork runs on a public server under the AGPL, so every player must be able to find its
 * source — including players whose permissions keep them out of {@code /gesture} itself.
 */
public final class DanseForkTests {

	@GameTest
	public void anyPlayerCanAskWhereDansesSourceIs(GameTestHelper helper) {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("danse")) { helper.succeed(); return; }
		var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
		var danse = root.getChild("danse");
		if (danse == null || danse.getChild("source") == null) { helper.fail("no /danse source command"); return; }
		CommandSourceStack player = helper.makeMockServerPlayerInLevel().createCommandSourceStack();
		if (!danse.canUse(player) || !danse.getChild("source").canUse(player)) {
			helper.fail("/danse source is not open to a plain player");
			return;
		}
		helper.succeed();
	}
}
