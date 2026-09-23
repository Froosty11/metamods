package metacraft.ovvar.gametest;

import eu.pb4.booklet.impl.BookletInit;
import eu.pb4.booklet.impl.BookletPage;
import metacraft.ovvar.guide.Guide;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The guidebook is data, and Booklet reads it leniently — a page that fails to parse is a stack
 * trace in the log, a link to a page that is not there is a button that does nothing, an item id
 * it does not know is silently a stone. So the pages are checked here, against the running
 * server's Booklet, for the things a reader would otherwise find first.
 */
public final class GuideTests {
	private static final String LANG = "en_us";
	private static final Pattern PAGE_LINK = Pattern.compile("<page(?:link|ref)\\s+'([^']+)'");
	private static final Pattern ITEM = Pattern.compile("<c?item\\s+'([^']+)'");

	private static BookletPage page(GameTestHelper helper, Identifier id) {
		Map<String, BookletPage> langs = BookletInit.PAGES.get(id);
		if (langs == null || langs.get(LANG) == null) {
			helper.fail("guide page " + id + " has no " + LANG + " text: is data/ovvar/booklet/pages/" + LANG + "/" + id.getPath() + ".txt there?");
			return null;
		}
		return langs.get(LANG);
	}

	private static String source(GameTestHelper helper, Identifier id) {
		String path = "/data/" + id.getNamespace() + "/booklet/pages/" + LANG + "/" + id.getPath() + ".txt";
		try (InputStream in = Guide.class.getResourceAsStream(path)) {
			if (in == null) {
				helper.fail("no resource " + path);
				return "";
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			helper.fail("cannot read " + path + ": " + e);
			return "";
		}
	}

	@GameTest
	public void everyGuidePageLoadsWithATitle(GameTestHelper helper) {
		for (Identifier id : Guide.pages()) {
			BookletPage page = page(helper, id);
			if (page.info().title().getString().equals(id.toString())) {
				helper.fail(id + " has no title= in its PageInfo section (Booklet fell back to the id)");
			}
			if (page.info().description().isEmpty()) helper.fail(id + " has no description= (the category entry shows it)");
			if (page.info().icon().isEmpty()) helper.fail(id + " has no icon= that parses (the category entry shows it)");
		}
		helper.succeed();
	}

	@GameTest
	public void theGuideIsOnTheServersBookshelfAndItsChaptersAreInOrder(GameTestHelper helper) {
		List<Identifier> shelf = BookletInit.CATEGORIES.getOrDefault(Guide.BOOKSHELF, List.of());
		if (!shelf.contains(Guide.MAIN)) {
			helper.fail(Guide.MAIN + " is not in " + Guide.BOOKSHELF + " (category= in guide.txt): the encyclopedia would not list it");
		}
		List<Identifier> chapters = BookletInit.CATEGORIES.getOrDefault(Guide.CATEGORY, List.of());
		if (!chapters.equals(Guide.CHAPTERS)) {
			helper.fail("category " + Guide.CATEGORY + " lists " + chapters + ", Guide.CHAPTERS is " + Guide.CHAPTERS
					+ " (category= and order= in each chapter's PageInfo)");
		}
		helper.succeed();
	}

	@GameTest
	public void everyLinkAndItemInTheGuideExists(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Identifier id : Guide.pages()) {
			String text = source(helper, id);
			Matcher links = PAGE_LINK.matcher(text);
			while (links.find()) {
				Identifier target = Identifier.tryParse(links.group(1));
				if (target == null || !BookletInit.PAGES.containsKey(target)) wrong.add(id + " links to " + links.group(1));
			}
			Matcher items = ITEM.matcher(text);
			while (items.find()) {
				Identifier item = Identifier.tryParse(items.group(1));
				if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) wrong.add(id + " names item " + items.group(1));
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}
}
