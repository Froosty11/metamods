package nu.metacraft.booklet.gametest;

import eu.pb4.booklet.impl.BookletInit;
import eu.pb4.booklet.impl.BookletPage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import nu.metacraft.booklet.MetacraftBooklet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Booklet reads pages leniently: a page that fails to parse is a stack trace in the log, a link to
 * a page that is not there is a button that does nothing, an unknown item id silently a stone. So
 * every chapter this mod registers is checked here against the running server's Booklet — for the
 * hooks whose mod is loaded, which in the dev runtime is all of them.
 */
public final class GuideTests {
	private static final String LANG = "en_us";
	private static final Pattern PAGE_LINK = Pattern.compile("<page(?:link|ref)\\s+'([^']+)'");
	private static final Pattern ITEM = Pattern.compile("<c?item\\s+'([^']+)'");
	private static final Pattern IMAGE = Pattern.compile("(?m)^### Image:\\s*(\\S+)");
	private static final Pattern CATEGORY = Pattern.compile("(?m)^category=(.*)$");
	private static final Identifier BOOKSHELF = Identifier.fromNamespaceAndPath("booklet", "main_page");

	/** Every page file in the loaded hooks' packs, page id to its text. */
	private static Map<Identifier, String> pages(GameTestHelper helper) {
		Map<Identifier, String> out = new LinkedHashMap<>();
		var self = FabricLoader.getInstance().getModContainer(MetacraftBooklet.MOD_ID).orElseThrow();
		for (MetacraftBooklet.Hook hook : MetacraftBooklet.HOOKS) {
			if (!FabricLoader.getInstance().isModLoaded(hook.modId())) continue;
			var data = self.findPath("resourcepacks/" + hook.pack() + "/data");
			if (data.isEmpty()) {
				helper.fail("hook " + hook.pack() + " has no resourcepacks/" + hook.pack() + "/data folder");
				continue;
			}
			try (Stream<Path> files = Files.walk(data.get())) {
				for (Path file : files.filter(Files::isRegularFile).toList()) {
					Path rel = data.get().relativize(file);
					// <namespace>/booklet/pages/<lang>/<path>.txt
					if (rel.getNameCount() < 5 || !rel.getName(1).toString().equals("booklet") || !rel.getName(3).toString().equals(LANG)) continue;
					String path = rel.subpath(4, rel.getNameCount()).toString().replace('\\', '/');
					out.put(Identifier.fromNamespaceAndPath(rel.getName(0).toString(), path.substring(0, path.length() - ".txt".length())),
							Files.readString(file, StandardCharsets.UTF_8));
				}
			} catch (IOException e) {
				helper.fail("cannot read " + hook.pack() + ": " + e);
			}
		}
		if (out.isEmpty()) helper.fail("no guide pages found at all");
		return out;
	}

	@GameTest
	public void everyPageLoadsWithATitleADescriptionAndAnIcon(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Identifier id : pages(helper).keySet()) {
			Map<String, BookletPage> langs = BookletInit.PAGES.get(id);
			BookletPage page = langs == null ? null : langs.get(LANG);
			if (page == null) { wrong.add(id + " did not load"); continue; }
			if (page.info().title().getString().equals(id.toString())) wrong.add(id + " has no title=");
			if (page.info().description().isEmpty()) wrong.add(id + " has no description=");
			if (page.info().icon().isEmpty()) wrong.add(id + " has no icon= that parses");
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void everyPageIsReachableFromTheIndex(GameTestHelper helper) {
		// Every page is in a category, and every category it names is either the bookshelf or one a
		// page on the bookshelf lists — so nothing is written that no reader can get to.
		Map<Identifier, String> pages = pages(helper);
		List<String> wrong = new ArrayList<>();
		for (var entry : pages.entrySet()) {
			Matcher m = CATEGORY.matcher(entry.getValue());
			if (!m.find()) { wrong.add(entry.getKey() + " is in no category"); continue; }
			for (String raw : m.group(1).split(",")) {
				Identifier cat = Identifier.tryParse(raw.strip());
				if (cat == null) { wrong.add(entry.getKey() + " names category " + raw); continue; }
				if (!BookletInit.CATEGORIES.getOrDefault(cat, List.of()).contains(entry.getKey())) {
					wrong.add(entry.getKey() + " is not listed in " + cat);
				}
				if (!cat.equals(BOOKSHELF) && pages.values().stream().noneMatch(t -> t.contains("### Category Entries: " + cat))) {
					wrong.add(entry.getKey() + "'s category " + cat + " is listed by no page");
				}
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void everyLinkAndItemInTheGuideExists(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (var entry : pages(helper).entrySet()) {
			Matcher links = PAGE_LINK.matcher(entry.getValue());
			while (links.find()) {
				Identifier target = Identifier.tryParse(links.group(1));
				if (target == null || !BookletInit.PAGES.containsKey(target)) wrong.add(entry.getKey() + " links to " + links.group(1));
			}
			Matcher items = ITEM.matcher(entry.getValue());
			while (items.find()) {
				Identifier item = Identifier.tryParse(items.group(1));
				if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) wrong.add(entry.getKey() + " names item " + items.group(1));
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void everyImageInTheGuideIsInThePack(GameTestHelper helper) {
		// Booklet finds an image in the resource pack; one that is not there is a gap on the page.
		var self = FabricLoader.getInstance().getModContainer(MetacraftBooklet.MOD_ID).orElseThrow();
		List<String> wrong = new ArrayList<>();
		for (var entry : pages(helper).entrySet()) {
			Matcher images = IMAGE.matcher(entry.getValue());
			while (images.find()) {
				Identifier image = Identifier.tryParse(images.group(1));
				boolean there = image != null && (nu.metacraft.booklet.Beside.wants(image)
						? nu.metacraft.booklet.Beside.has(image)
						: self.findPath("assets/" + image.getNamespace() + "/textures/booklet/image/" + image.getPath() + ".png").isPresent());
				if (!there) {
					wrong.add(entry.getKey() + " shows image " + images.group(1) + ", which is not in this mod's assets");
				}
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void polyDecorationsHasOnlyItsCanvasRecipes(GameTestHelper helper) {
		if (!FabricLoader.getInstance().isModLoaded("polydecorations")) { helper.succeed(); return; }
		List<String> left = new ArrayList<>();
		boolean canvas = false;
		for (var holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
			Identifier id = holder.id().identifier();
			if (!id.getNamespace().equals("polydecorations")) continue;
			if (id.getPath().startsWith("canvas")) canvas |= id.getPath().equals("canvas");
			else left.add(id.toString());
		}
		if (!canvas) helper.fail("polydecorations:canvas, the canvas's own recipe, is gone too");
		if (!left.isEmpty()) {
			helper.fail(left.size() + " other PolyDecorations recipe(s) still loaded — a newer PolyDecorations? regenerate "
					+ "resourcepacks/polydecorations_canvas_only: " + String.join(", ", left.subList(0, Math.min(8, left.size()))));
		}
		helper.succeed();
	}
}
