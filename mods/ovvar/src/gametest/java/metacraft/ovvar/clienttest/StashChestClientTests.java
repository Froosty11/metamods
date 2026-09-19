package metacraft.ovvar.clienttest;

import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.store.Wardrobes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.Properties;

/**
 * The stash pocket as a real chest, through a vanilla client's own clicks: the client opens
 * {@code /ovvar stash}, shift-clicks the patch stack out of the pocket into its inventory, and
 * shift-clicks it back in. The clicks go the whole way — client packet, vanilla menu, sgui's
 * wrapper, the pocket's slots, the wardrobe store — and the stash and the inventory are read on
 * the server after each. Screenshots of the screen before and after land beside the others.
 *
 * Run: {@code ./gradlew :mods:ovvar:runClientGameTest} (opens a window).
 */
public final class StashChestClientTests implements FabricClientGameTest {
	private static final int FIRST_POCKET_SLOT = 9;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25598");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				acceptResourcePack(ctx);
				conn.waitForChunksRender();
				Patches.Patch itk = Patches.get("itk");

				server.runCommand("ovvar patch give Tester itk 3");
				waitForStash(ctx, server, itk, 3, "the grant");

				ctx.runOnClient(client -> client.player.connection.sendCommand("ovvar stash"));
				ctx.waitFor(client -> client.gui.screen() instanceof ContainerScreen, 200);
				ctx.waitTicks(20);   // the pocket's slots reach the client with the menu's first sync
				ctx.takeScreenshot(TestScreenshotOptions.of("stash_chest_before").withSize(1920, 1080));
				ctx.runOnClient(client -> {
					ItemStack shown = client.player.containerMenu.getSlot(FIRST_POCKET_SLOT).getItem();
					if (shown.getCount() != 3) throw new AssertionError("the client sees " + shown + " in the first pocket slot, wanted 3 ITK");
					// Shift-click: the stack goes to the inventory the way it does out of any chest.
					client.gameMode.handleContainerInput(client.player.containerMenu.containerId, FIRST_POCKET_SLOT, 0, ContainerInput.QUICK_MOVE, client.player);
				});
				waitForStash(ctx, server, itk, 0, "the shift-click out");
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("stash_chest_taken").withSize(1920, 1080));
				int carried = server.computeOnServer(mc -> carried(mc.getPlayerList().getPlayers().getFirst(), itk));
				if (carried != 3) throw new AssertionError("the player carries " + carried + " ITK after the shift-click, wanted 3");

				// And back in: shift-click the stack from the inventory into the pocket.
				ctx.runOnClient(client -> {
					// Polymer shows the client a vanilla item in the patch's place, so the stack is found by
					// where it is: the inventory's slots follow the chest's 54, and hold nothing else here.
					var menu = client.player.containerMenu;
					int at = -1;
					for (int i = 54; i < menu.slots.size(); i++) {
						if (menu.getSlot(i).getItem().getCount() == 3) at = i;
					}
					if (at < 0) throw new AssertionError("the client sees no stack of 3 in its inventory to put back");
					client.gameMode.handleContainerInput(menu.containerId, at, 0, ContainerInput.QUICK_MOVE, client.player);
				});
				waitForStash(ctx, server, itk, 3, "the shift-click back in");
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("stash_chest_returned").withSize(1920, 1080));
				carried = server.computeOnServer(mc -> carried(mc.getPlayerList().getPlayers().getFirst(), itk));
				if (carried != 0) throw new AssertionError("the player still carries " + carried + " ITK after putting them back");
			}
		}
	}

	private static void waitForStash(ClientGameTestContext ctx, TestDedicatedServerContext server, Patches.Patch patch, int want, String after) {
		for (int i = 0; i < 100; i++) {
			int count = server.computeOnServer(mc -> {
				ServerPlayer player = mc.getPlayerList().getPlayers().getFirst();
				return Wardrobes.loaded(player.getUUID()) ? Wardrobes.current(player.getUUID()).count(patch) : -1;
			});
			if (count == want) return;
			ctx.waitTicks(2);
		}
		int count = server.computeOnServer(mc -> Wardrobes.current(mc.getPlayerList().getPlayers().getFirst().getUUID()).count(patch));
		throw new AssertionError("after " + after + " the stash holds " + count + " " + patch.name() + ", wanted " + want);
	}

	private static int carried(ServerPlayer player, Patches.Patch patch) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() == ModContent.patchItem(patch)) n += stack.getCount();
		}
		ItemStack cursor = player.containerMenu.getCarried();
		if (cursor.getItem() == ModContent.patchItem(patch)) n += cursor.getCount();
		return n;
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}
}
