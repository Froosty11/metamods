package se.metacraft.ovvar.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.combo.ComboAssets;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Cloth;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.pack.ComboPack;
import se.metacraft.ovvar.pack.Combos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.zip.ZipInputStream;

/**
 * The ovvar-client mod builds a combination's equipment definition from the asset's name alone
 * ({@link ComboAssets#jsonFor}); these check that it is byte for byte what {@link ComboPack} writes.
 * The same files are kept in {@code src/combo/goldens.json} (path in {@code -Dovvar.comboGoldens}, set by
 * the build), which the client mod's own unit test reads back without a server: rewrite it with
 * {@code -PupdateComboGoldens} when the pack's output changes on purpose (and see ComboAssets#PROTOCOL).
 */
public final class ComboAssetsParityTests {

	/** Every cloth (each chapter and each of its named variants) and every rolled-down state it has, ovve and frack, overlapping cells, the seat, given-only and every instant design. */
	@GameTest
	public void theClientBuildsEveryCombinationExactlyAsThePackHoldsIt(GameTestHelper helper) {
		Set<Combos.KeyedCombo> combos = sample();
		Map<String, String> files = unzip(ComboPack.build(combos).zip());
		files.remove("pack.mcmeta");
		Set<Cloth> chapters = new HashSet<>();
		int nercabbad = 0;
		for (Map.Entry<String, String> file : files.entrySet()) {
			String path = assetPath(file.getKey());
			Optional<String> built = ComboAssets.jsonFor(path);
			assertThat(built.isPresent(), "the client cannot build " + path);
			assertThat(built.get().equals(file.getValue()), "the client builds " + path + " differently from the pack:\n" + built.get() + "\nvs\n" + file.getValue());
			chapters.add(Cloth.byId(path.substring(0, path.indexOf('/'))));
			if (path.contains(ComboAssets.NERCABBAD)) nercabbad++;
		}
		assertThat(chapters.equals(Set.copyOf(Cloth.all())), "not every chapter and named variant was checked: " + chapters);
		assertThat(nercabbad > 0, "no rolled-down variant was checked");
		goldens(files);
		helper.succeed();
	}

	/** ComboAssets' cell table is ovvar's Spot, cell for cell (id, order, half, layer), and its halves are Piece. */
	@GameTest
	public void theSharedCellTableIsSpot(GameTestHelper helper) {
		Map<String, ComboAssets.Cell> spots = new LinkedHashMap<>();
		for (Spot spot : Spot.values()) spots.put(spot.id(), new ComboAssets.Cell(spot.piece.id, spot.layer()));
		assertThat(new ArrayList<>(spots.entrySet()).equals(new ArrayList<>(ComboAssets.CELLS.entrySet())),
				"ComboAssets.CELLS is not Spot (and a changed cell table is a new ComboAssets.PROTOCOL):\n" + ComboAssets.CELLS + "\nvs\n" + spots);
		Map<String, String> pieces = new LinkedHashMap<>();
		for (Piece piece : Piece.values()) pieces.put(piece.id, piece.layer);
		assertThat(pieces.equals(ComboAssets.PIECE_LAYERS), "ComboAssets.PIECE_LAYERS is not Piece: " + pieces);
		assertThat(Ovvar.MOD_ID.equals(ComboAssets.NAMESPACE), "ComboAssets.NAMESPACE is not the mod id");
		assertThat(Spot.SEAT.id().equals(ComboAssets.SEAT), "ComboAssets.SEAT is not Spot.SEAT");
		helper.succeed();
	}

	/** What the server names that is not a combination, or names it cannot have made, the client leaves alone (it falls back). */
	@GameTest
	public void namesThatAreNotCombinationsBuildNothing(GameTestHelper helper) {
		String top = Placement.combo(List.of(new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")))).key();
		for (String path : List.of("data/top", "data/bottom_nercabbad", "feet/iron", "chest/data/iron", "data/top/",
				"data/top_nercabbad/" + top, "data/bottom/" + top, "data/top/front_top_left", "data/top/front_top_left.",
				"data/top/no_such_cell.itk", "/top/" + top, "data/top/" + top + "/x")) {
			assertThat(ComboAssets.jsonFor(path).isEmpty(), "built something for " + path);
		}
		helper.succeed();
	}

	/** The combinations checked: see the test above. */
	static Set<Combos.KeyedCombo> sample() {
		Set<Combos.KeyedCombo> out = new HashSet<>();
		for (Cloth chapter : Cloth.all()) {
			for (Piece piece : chapter.chapter().pieces()) {
				if (piece == Piece.TOP) {
					// the big back cell under a back-top one, a given-only design with a '-' in its id, an instant one
					out.add(key(chapter, piece, place(Spot.BACK_TOP_LEFT, "tmeit-marshal"), place(Spot.BACK_BIG, "kth"),
							place(Spot.FRONT_TOP_LEFT, "itk"), place(Spot.SLEEVE_OUT_TOP_L, "metacraft")));
					out.add(key(chapter, piece, place(Spot.SLEEVE_BACK_MID_R, "metacrew")));
				} else {
					// the seat, and the legs' back-top cells (drawn over the others on the legs)
					out.add(key(chapter, piece, place(Spot.SEAT, "rivals"), place(Spot.LEG_OUT_TOP_R, "tmeit")));
					out.add(key(chapter, piece, place(Spot.LEG_BACK_TOP_R, "spiken"), place(Spot.LEG_FRONT_MID_L, "data"),
							place(Spot.LEG_OUT_TOP_L, "tmeit-marshal")));
				}
			}
		}
		// the client screenshot test's own combination (OvvarClientTests: the Data top, four front patches),
		// so the very asset that test draws on a modded client is pinned against the pack's
		out.add(key(Cloth.of(Chapter.DATA), Piece.TOP, place(Spot.FRONT_TOP_LEFT, "itk"), place(Spot.FRONT_TOP_RIGHT, "nyckeln"),
				place(Spot.FRONT_LOW_LEFT, "it"), place(Spot.FRONT_LOW_RIGHT, "data")));
		// every instant design, once each, on a cell it fits
		List<Patches.Patch> designs = Patches.byCode();
		for (int i = 0; i < Math.min(Looks.INSTANT_DESIGNS, designs.size()); i++) {
			Patches.Patch design = designs.get(i);
			if (!Looks.instant(design)) continue;
			Spot cell = null;
			for (Spot spot : Spot.cells(Piece.TOP)) if (cell == null && design.fits(spot)) cell = spot;
			if (cell != null) out.add(new Combos.KeyedCombo(Chapter.DATA, Piece.TOP, Placement.combo(List.of(new Placement(cell, design)))));
		}
		return out;
	}

	private static Placement place(Spot spot, String patch) {
		Patches.Patch p = Patches.get(patch);
		if (!p.fits(spot)) throw new GameTestAssertException(Component.literal(patch + " does not fit " + spot.id() + "; pick another for the sample"), 0);
		return new Placement(spot, p);
	}

	private static Combos.KeyedCombo key(Cloth chapter, Piece piece, Placement... placements) {
		return new Combos.KeyedCombo(chapter, piece, Placement.combo(List.of(placements)));
	}

	/** Compares the files to src/combo/goldens.json, or rewrites it with -Dovvar.comboGoldens.update=true. */
	private static void goldens(Map<String, String> files) {
		String where = System.getProperty("ovvar.comboGoldens");
		if (where == null) {
			Ovvar.LOGGER.warn("[ovvar] -Dovvar.comboGoldens is not set; the client's golden combinations were not checked");
			return;
		}
		Path path = Path.of(where);
		JsonObject root = new JsonObject();
		for (Map.Entry<String, String> file : new TreeMap<>(files).entrySet()) root.addProperty(file.getKey(), file.getValue());
		String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
		try {
			if (Boolean.getBoolean("ovvar.comboGoldens.update")) {
				Files.writeString(path, text, StandardCharsets.UTF_8);
				Ovvar.LOGGER.info("[ovvar] wrote {} golden combination(s) to {}", files.size(), path);
				return;
			}
			assertThat(Files.isRegularFile(path), path + " is missing: run the game tests with -PupdateComboGoldens");
			assertThat(JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).equals(root),
					path + " is not what the pack holds: run the game tests with -PupdateComboGoldens (and bump ComboAssets.PROTOCOL if a name now means something else)");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** assets/ovvar/equipment/<path>.json to <path>. */
	private static String assetPath(String packPath) {
		String prefix = "assets/" + Ovvar.MOD_ID + "/equipment/";
		assertThat(packPath.startsWith(prefix) && packPath.endsWith(".json"), "an unexpected file in the pack: " + packPath);
		return packPath.substring(prefix.length(), packPath.length() - ".json".length());
	}

	static Map<String, String> unzip(byte[] zip) {
		Map<String, String> files = new TreeMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return files;
	}

	private static void assertThat(boolean condition, String message) {
		if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
	}
}
