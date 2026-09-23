package metacraft.ovvar.guide;

import eu.pb4.booklet.impl.BookletImplUtil;
import eu.pb4.booklet.impl.BookletOpenState;
import metacraft.ovvar.content.ModContent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The in-game guide: "How to ovvar", a <a href="https://github.com/Patbox/booklet">Booklet</a>
 * guidebook written as data, {@code data/ovvar/booklet/pages/<lang>/}.
 *
 * <p>{@link #MAIN} is one page of the server's encyclopedia — Booklet's own index lists every page
 * in its {@code booklet:main_page} category, so the other mods on the server add their own chapters
 * the same way and the book grows without anything here changing. The main page lists
 * {@link #CHAPTERS} through the {@link #CATEGORY} category, in {@code order}.
 *
 * <p>{@code /ovvar guide} opens it; a {@code booklet:guidebook} with {@code booklet:page='ovvar:guide'}
 * is the same book as an item. The pages are checked by {@code GuideTests}: every page here loads,
 * sits in its category, and every link and item it names exists.
 */
public final class Guide {
	private Guide() {}

	/** The page a player lands on, and the category the chapters sit in. */
	public static final Identifier MAIN = ModContent.id("guide");
	public static final Identifier CATEGORY = MAIN;

	/** The chapters, in the order the main page lists them. */
	public static final List<Identifier> CHAPTERS = List.of(
			ModContent.id("guide/ovve"),
			ModContent.id("guide/patches"),
			ModContent.id("guide/sewing"),
			ModContent.id("guide/wardrobe"));

	/** Every page ovvar ships, main first. */
	public static List<Identifier> pages() {
		return java.util.stream.Stream.concat(java.util.stream.Stream.of(MAIN), CHAPTERS.stream()).toList();
	}

	/** Booklet's page id for this server's encyclopedia, which the main page is listed in. */
	public static final Identifier BOOKSHELF = Identifier.fromNamespaceAndPath("booklet", "main_page");

	/** Opens the guide for a player; false if Booklet has no such page (the log says why). */
	public static boolean open(ServerPlayer player) {
		return BookletImplUtil.openPage(player, MAIN, BookletOpenState.DEFAULT);
	}
}
