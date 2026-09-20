package metacraft.ovvar.clienttest;

import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

import java.util.Properties;

/**
 * What a vanilla client's tooltip makes of an ovve with things in its pockets: the server fills
 * one past half (a stack and a half of stone, a few smokers) and tries to put two ovves in, the
 * client opens its inventory and hovers it, and the screenshot shows the bundle grid and the bundle
 * mod's own fill bar. Two things are pinned here: the ovves are refused (an ovve never goes in a
 * pocket), and the tooltip carries no "Full" label — vanilla would print one from its unscaled
 * weight at half fill, over the mod's bar; the bundle mod's lang override blanks it.
 *
 * Run: {@code ./gradlew :mods:ovvar:runClientGameTest} (opens a window).
 */
public final class OvvePocketsClientTests implements FabricClientGameTest {
	/** The inventory screen's first hotbar slot: index 36 of the inventory menu, at (8, 142) in the 176x166 panel. */
	private static final int HOTBAR_0 = 36, PANEL_W = 176, PANEL_H = 166;

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

				server.runOnServer(mc -> {
					ServerPlayer player = mc.getPlayerList().getPlayers().getFirst();
					ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
					OvveItem.setOwner(ovve, player.getUUID());
					BundleContents.Mutable pockets = ovve.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
					int stone = pockets.tryInsert(new ItemStack(Items.STONE, 64)) + pockets.tryInsert(new ItemStack(Items.STONE, 32));
					int smokers = pockets.tryInsert(new ItemStack(Items.SMOKER, 3));
					// One at a time, as a player would click them in; both must be refused.
					int ovves = pockets.tryInsert(new ItemStack(ModContent.ovve(Chapter.IT))) + pockets.tryInsert(new ItemStack(ModContent.ovve(Chapter.IT)));
					if (ovves != 0) throw new AssertionError("the pockets took " + ovves + " ovve(s); an ovve never goes in a pocket");
					if (stone != 96 || smokers != 3) throw new AssertionError("the pockets took stone=" + stone + " smokers=" + smokers + ", not 96 and 3");
					ovve.set(DataComponents.BUNDLE_CONTENTS, pockets.toImmutable());
					player.getInventory().setItem(0, ovve);
					System.out.println("[ovvar-clienttest] pockets took stone=" + stone + " smokers=" + smokers + " ovves=" + ovves
							+ " weight=" + ovve.get(DataComponents.BUNDLE_CONTENTS).weight()
							+ " items=" + ovve.get(DataComponents.BUNDLE_CONTENTS).items());
				});
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);

				ctx.takeScreenshot(TestScreenshotOptions.of("ovve_pockets_before_open").withSize(1920, 1080));
				ctx.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
				ctx.waitForScreen(InventoryScreen.class);
				ctx.waitTicks(5);
				ctx.runOnClient(client -> {
					InventoryScreen screen = (InventoryScreen) client.gui.screen();
					Slot slot = screen.getMenu().slots.get(HOTBAR_0);
					int left = (client.getWindow().getGuiScaledWidth() - PANEL_W) / 2, top = (client.getWindow().getGuiScaledHeight() - PANEL_H) / 2;
					double scale = client.getWindow().getGuiScale();
					System.out.println("[ovvar-clienttest] hovering slot at gui (" + (left + slot.x + 8) + "," + (top + slot.y + 8) + ") scale " + scale
							+ " item " + slot.getItem());
				});
				double[] at = new double[2];
				ctx.runOnClient(client -> {
					InventoryScreen screen = (InventoryScreen) client.gui.screen();
					Slot slot = screen.getMenu().slots.get(HOTBAR_0);
					int left = (client.getWindow().getGuiScaledWidth() - PANEL_W) / 2, top = (client.getWindow().getGuiScaledHeight() - PANEL_H) / 2;
					double scale = client.getWindow().getGuiScale();
					at[0] = (left + slot.x + 8) * scale;
					at[1] = (top + slot.y + 8) * scale;
				});
				ctx.getInput().setCursorPos(at[0], at[1]);
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("ovve_pockets_tooltip").withSize(1920, 1080));
			}
		}
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}
}
