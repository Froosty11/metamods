package se.metacraft.kultur.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import se.metacraft.kultur.catalogue.Catalogue;
import se.metacraft.kultur.content.ModContent;
import se.metacraft.kultur.drinks.DrinkKind;
import se.metacraft.kultur.drinks.DrinkPotions;
import se.metacraft.kultur.drinks.DrunkTicker;
import se.metacraft.kultur.drinks.Drunkenness;
import se.metacraft.kultur.drinks.KulturDrinks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

				// A failed check is collected, not thrown, so the shots after it still get taken.
				List<String> failures = new ArrayList<>();
				chapterLoom(ctx, server, conn, failures);
				chapterBanners(ctx, server, conn, failures);

				if (FabricLoader.getInstance().isModLoaded("brewery")) {
					// The drinks, each in a frame on a wall of their own behind the camera, close up: PolymITer's
					// bottles, through Brewery.
					server.runCommand("fill -3 -60 -7 3 -57 -7 minecraft:stone");
					server.runCommand("item replace entity Tester weapon.mainhand with minecraft:air");
					server.runOnServer(WithBrewery::frameTheDrinks);
					server.runCommand("tp Tester -0.5 -60 -3.6 180 10");
					conn.waitForClientboundPackets();
					ctx.waitTicks(40);
					Path drinksShot = ctx.takeScreenshot(TestScreenshotOptions.of("kultur_drinks").withSize(1920, 1080));
					System.out.println("[kultur-clienttest] screenshot: " + drinksShot);
				} else {
					drinksWithoutBrewery(ctx, server, conn);
				}
				if (!failures.isEmpty()) {
					throw new AssertionError(failures.size() + " kultur client check(s) failed:\n - " + String.join("\n - ", failures));
				}
			}
		}
	}

	private static void fail(List<String> failures, String what) {
		System.out.println("[kultur-clienttest] " + what);
		failures.add(what);
	}

	/** Every catalogue pattern that is gated by an item, in catalogue order: the chapter patterns. */
	private static List<Catalogue.Pattern> chapterPatterns() {
		List<Catalogue.Pattern> out = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Pattern> owned : Catalogue.load().patterns()) {
			if (owned.value().item()) out.add(owned.value());
		}
		return out;
	}

	private static boolean offers(LoomMenu menu, Identifier want) {
		return menu.getSelectablePatterns().stream()
				.anyMatch(h -> h.unwrapKey().map(k -> k.identifier().equals(want)).orElse(false));
	}

	/**
	 * The loom, checked on the server-side {@code LoomMenu} for every kultur pattern, old and new: with
	 * only a banner in the slot every pattern without an item must be selectable (the old ungated
	 * ones), and with its pattern item in the pattern slot every pattern with an item must be. Failures
	 * go into {@code failures}. The screenshot is the same thing seen by a vanilla client, with the
	 * ITK pattern item in the slot.
	 */
	private static void chapterLoom(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, List<String> failures) {
		List<Catalogue.Pattern> all = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Pattern> owned : Catalogue.load().patterns()) all.add(owned.value());
		List<Catalogue.Pattern> gated = chapterPatterns();
		if (gated.isEmpty()) fail(failures, "loom: the catalogue has no pattern with an item");
		Catalogue.Pattern shown = gated.isEmpty() ? null : gated.getFirst();
		for (Catalogue.Pattern p : gated) {
			if (p.id().equals("it")) shown = p;
		}
		Catalogue.Pattern showPattern = shown;
		server.runCommand("setblock 5 -60 -2 minecraft:loom");
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayerByName("Tester");
			var level = s.overworld();
			player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
				LoomMenu menu = new LoomMenu(id, inv, ContainerLevelAccess.create(level, new BlockPos(5, -60, -2)));
				menu.getBannerSlot().set(new ItemStack(Items.BANNER.white()));
				return menu;
			}, Component.translatable("block.minecraft.loom")));
			if (!(player.containerMenu instanceof LoomMenu menu)) {
				fail(failures, "loom: the loom menu did not open");
				return;
			}
			// Banner only: every pattern without an item.
			int ungated = 0;
			for (Catalogue.Pattern p : all) {
				if (p.item()) continue;
				ungated++;
				Identifier want = Identifier.fromNamespaceAndPath("kultur", p.id());
				if (!offers(menu, want)) fail(failures, "loom: " + p.id() + " (no item) is not selectable with only a banner in the loom");
			}
			System.out.println("[kultur-clienttest] loom: " + ungated + " pattern(s) without an item checked with a banner only ("
					+ menu.getSelectablePatterns().size() + " selectable)");
			// Each pattern with an item, with that item in the pattern slot.
			for (Catalogue.Pattern p : gated) {
				var item = ModContent.patternItem(p.id());
				if (item == null) {
					fail(failures, "loom: " + p.id() + " has no pattern item registered (" + p.itemPath() + ")");
					continue;
				}
				menu.getPatternSlot().set(new ItemStack(item));
				Identifier want = Identifier.fromNamespaceAndPath("kultur", p.id());
				if (!offers(menu, want)) fail(failures, "loom: " + p.id() + " is not selectable with " + p.itemPath() + " in the pattern slot");
			}
			System.out.println("[kultur-clienttest] loom: " + gated.size() + " pattern item(s) checked");
			// Leave the shown pattern's item in the slot for the screenshot.
			var showItem = showPattern == null ? null : ModContent.patternItem(showPattern.id());
			menu.getPatternSlot().set(showItem == null ? ItemStack.EMPTY : new ItemStack(showItem));
		});
		conn.waitForClientboundPackets();
		ctx.waitTicks(30);
		Path shot = ctx.takeScreenshot(TestScreenshotOptions.of("kultur_chapter_loom").withSize(1920, 1080));
		System.out.println("[kultur-clienttest] screenshot: " + shot);
		server.runOnServer(s -> s.getPlayerList().getPlayerByName("Tester").closeContainer());
		ctx.waitTicks(10);
	}

	/**
	 * Every chapter pattern in white on a black banner, in a row against a stone wall, a few banners
	 * to a shot so each is legible. An empty view from the same spot is shot first; each banner shot
	 * has to differ from it.
	 */
	private static void chapterBanners(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, List<String> failures) {
		final int perShot = 6;
		List<Catalogue.Pattern> patterns = chapterPatterns();
		int shots = (patterns.size() + perShot - 1) / perShot;
		System.out.println("[kultur-clienttest] chapter banners: " + patterns.size() + " patterns in " + shots + " shots");
		server.runCommand("fill -4 -60 21 " + (perShot + 4) + " -56 21 minecraft:stone");
		server.runCommand("gamemode creative Tester");
		server.runCommand("item replace entity Tester weapon.mainhand with minecraft:air");
		server.runCommand("tp Tester " + (perShot / 2.0) + " -60 16.5 0 5");
		conn.waitForClientboundPackets();
		ctx.waitTicks(40);
		Path empty = ctx.takeScreenshot(TestScreenshotOptions.of("kultur_chapter_banners_empty").withSize(1920, 1080));
		for (int shot = 0; shot < shots; shot++) {
			int from = shot * perShot;
			int to = Math.min(patterns.size(), from + perShot);
			for (int i = from; i < to; i++) {
				int x = i - from + 1;
				server.runCommand("setblock " + x + " -60 20 minecraft:black_banner[rotation=8]{patterns:[{pattern:\"kultur:"
						+ patterns.get(i).id() + "\",color:\"white\"}]}");
			}
			for (int x = to - from + 1; x <= perShot; x++) server.runCommand("setblock " + x + " -60 20 minecraft:air");
			conn.waitForClientboundPackets();
			ctx.waitTicks(40);
			String name = shots == 1 ? "kultur_chapter_banners" : "kultur_chapter_banners_" + (shot + 1);
			Path path = ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080));
			System.out.println("[kultur-clienttest] screenshot: " + path + " (patterns "
					+ patterns.subList(from, to).stream().map(Catalogue.Pattern::id).toList() + ")");
			try {
				if (Files.mismatch(empty, path) == -1L) fail(failures, "banners: " + name + " is identical to the empty view");
			} catch (IOException e) {
				fail(failures, "banners: could not compare " + name + " with the empty view: " + e);
			}
		}
	}

	/**
	 * Without Brewery, Kultur brews the drinks itself: the four bottles, each in a frame on a wall behind
	 * the camera with a Spiken in hand, should be PolymITer's models, not plain potions. Then the
	 * inventory at 55 alcohol: "Inebriated IV" with PolymITer's icon (Luck, renamed by the pack), and
	 * the weakness and slowness of the steps below it.
	 */
	private static void drinksWithoutBrewery(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		server.runCommand("fill -3 -60 -7 3 -57 -7 minecraft:stone");
		server.runOnServer(s -> {
			var level = s.overworld();
			DrinkKind[] kinds = DrinkKind.values();
			for (int i = 0; i < kinds.length; i++) {
				var frame = new ItemFrame(level, new BlockPos(i - 2, -59, -6), Direction.SOUTH);
				frame.setItem(stack(kinds[i]));
				level.addFreshEntity(frame);
			}
			s.getPlayerList().getPlayerByName("Tester").setItemInHand(InteractionHand.MAIN_HAND, stack(DrinkKind.SPIKEN));
		});
		server.runCommand("tp Tester -0.5 -60 -3.6 180 10");
		conn.waitForClientboundPackets();
		ctx.waitTicks(40);
		Path bottles = ctx.takeScreenshot(TestScreenshotOptions.of("drinks_vanilla_bottles").withSize(1920, 1080));
		System.out.println("[kultur-clienttest] screenshot: " + bottles);

		server.runCommand("gamemode survival Tester");
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayerByName("Tester");
			Drunkenness.set(player, 55);
			DrunkTicker.refreshInebriated(player);
		});
		ctx.waitTicks(100);   // the steps' weakness and slowness come every 4 s
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(20);
		Path inebriated = ctx.takeScreenshot(TestScreenshotOptions.of("drinks_vanilla_inebriated").withSize(1920, 1080));
		System.out.println("[kultur-clienttest] screenshot: " + inebriated);
		ctx.setScreen(() -> null);
		server.runOnServer(s -> Drunkenness.set(s.getPlayerList().getPlayerByName("Tester"), 0));
	}

	private static ItemStack stack(DrinkKind kind) {
		return kind == DrinkKind.NYCKELN ? new ItemStack(KulturDrinks.NYCKELN) : DrinkPotions.bottle(kind);
	}

	/** Everything that names a Brewery class, loaded only once Brewery is known to be there. */
	private static final class WithBrewery {
		static void frameTheDrinks(MinecraftServer s) {
			var level = s.overworld();
			String[] drinks = {"alcohol", "spiken", "slaggan", "nyckeln"};
			for (int i = 0; i < drinks.length; i++) {
				var frame = new net.minecraft.world.entity.decoration.ItemFrame(level, new net.minecraft.core.BlockPos(i - 2, -59, -6), net.minecraft.core.Direction.SOUTH);
				frame.setItem(eu.pb4.brewery.drink.DrinkUtils.createDrink(net.minecraft.resources.Identifier.fromNamespaceAndPath("kultur", drinks[i]),
						0, 10, i == 1 || i == 2 ? 1 : 0, net.minecraft.world.level.block.Blocks.FIRE));
				level.addFreshEntity(frame);
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
