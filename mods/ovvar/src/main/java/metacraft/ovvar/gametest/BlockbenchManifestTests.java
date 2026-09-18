package metacraft.ovvar.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.datagen.BlockbenchManifest;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Blockbench plugin's goldens are the pack's own textures, and what tells it how to read
 * them is {@link BlockbenchManifest}. So the manifest has to be the enums, not a copy of them:
 * a cell added, moved or resized in {@link Spot}, a patch added to {@link Patches}, a change to
 * {@link Spot#anchored} — each of those must come out of {@code runDatagen} as a changed
 * manifest, and if it does not, this fails rather than the plugin silently drawing last week's
 * model.
 */
public final class BlockbenchManifestTests {
	private static JsonObject manifest(GameTestHelper helper) {
		try (InputStream in = BlockbenchManifest.class.getResourceAsStream(BlockbenchManifest.RESOURCE)) {
			if (in == null) {
				helper.fail(BlockbenchManifest.RESOURCE + " missing: run './gradlew :mods:ovvar:runDatagen'");
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			helper.fail("could not read " + BlockbenchManifest.RESOURCE + ": " + e);
			return null;
		}
	}

	@GameTest
	public void theManifestsCellTableIsSpot(GameTestHelper helper) {
		JsonObject m = manifest(helper);
		JsonArray cells = m.getAsJsonArray("cells");
		if (cells.size() != Spot.values().length) {
			helper.fail("the manifest has " + cells.size() + " cells, Spot has " + Spot.values().length);
		}
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < Spot.values().length; i++) {
			Spot spot = Spot.values()[i];
			JsonObject c = cells.get(i).getAsJsonObject();
			check(wrong, spot.id(), "id", c.get("id").getAsString(), spot.id());
			check(wrong, spot.id(), "piece", c.get("piece").getAsString(), spot.piece.id);
			check(wrong, spot.id(), "u", c.get("u").getAsInt(), spot.u);
			check(wrong, spot.id(), "v", c.get("v").getAsInt(), spot.v);
			check(wrong, spot.id(), "w", c.get("w").getAsInt(), spot.width);
			check(wrong, spot.id(), "h", c.get("h").getAsInt(), spot.height);
			check(wrong, spot.id(), "side", c.get("side").getAsString(), spot.side.name().toLowerCase(Locale.ROOT));
			check(wrong, spot.id(), "top", c.get("top").getAsBoolean(), spot.top());
			check(wrong, spot.id(), "face", c.get("face").getAsInt(), Spot.face(spot));
			check(wrong, spot.id(), "layer", c.get("layer").getAsInt(), spot.layer());
			check(wrong, spot.id(), "stripStart", c.get("stripStart").getAsInt(), Spot.stripStart(spot));
			check(wrong, spot.id(), "stripWidth", c.get("stripWidth").getAsInt(), Spot.stripWidth(spot));
			check(wrong, spot.id(), "fit", c.get("fit").getAsString(), Patches.Fit.of(spot).name().toLowerCase(Locale.ROOT));
			check(wrong, spot.id(), "label", c.get("label").getAsString(), spot.label());
		}
		if (!wrong.isEmpty()) helper.fail("the manifest's cell table has drifted from Spot: " + wrong);
		helper.succeed();
	}

	@GameTest
	public void theManifestsAnchoredSamplesAreSpotAnchored(GameTestHelper helper) {
		JsonObject m = manifest(helper);
		JsonArray samples = m.getAsJsonArray("anchoredSamples");
		if (samples.size() != BlockbenchManifest.SAMPLES) {
			helper.fail("the manifest has " + samples.size() + " anchored samples, not " + BlockbenchManifest.SAMPLES);
		}
		for (int k = 0; k < samples.size(); k++) {
			JsonArray row = samples.get(k).getAsJsonArray();
			double skinX = row.get(0).getAsDouble(), inflate = row.get(1).getAsDouble();
			int anchor = row.get(2).getAsInt();
			if (skinX != BlockbenchManifest.sampleSkinX(k) || inflate != BlockbenchManifest.sampleInflate(k)
					|| anchor != BlockbenchManifest.sampleAnchor(k)) {
				helper.fail("anchored sample " + k + " is not the grid's own row: " + row);
			}
			double want = Spot.anchored(skinX, inflate, anchor);
			if (Math.abs(row.get(3).getAsDouble() - want) > 1e-12) {
				helper.fail("anchored sample " + k + " (" + row + ") should be " + want);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void theManifestsCatalogueIsPatchesAndChapter(GameTestHelper helper) {
		JsonObject m = manifest(helper);
		JsonArray patches = m.getAsJsonArray("patches");
		if (patches.size() != Patches.all().size()) {
			helper.fail("the manifest has " + patches.size() + " patches, the catalogue has " + Patches.all().size());
		}
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < Patches.all().size(); i++) {
			Patches.Patch patch = Patches.all().get(i);
			JsonObject p = patches.get(i).getAsJsonObject();
			check(wrong, patch.id(), "id", p.get("id").getAsString(), patch.id());
			check(wrong, patch.id(), "name", p.get("name").getAsString(), patch.name());
			check(wrong, patch.id(), "seat", p.get("seat").getAsBoolean(), patch.seat());
			check(wrong, patch.id(), "w", p.get("w").getAsInt(), patch.width());
			check(wrong, patch.id(), "h", p.get("h").getAsInt(), patch.height());
			JsonArray arts = p.getAsJsonArray("arts");
			check(wrong, patch.id(), "arts", arts.size(), patch.variants().size());
			for (int a = 0; a < Math.min(arts.size(), patch.variants().size()); a++) {
				Patches.Art art = patch.variants().get(a);
				check(wrong, patch.id(), "art " + a, arts.get(a).getAsJsonObject().get("file").getAsString(), art.file() + ".png");
			}
			for (Patches.Fit fit : Patches.Fit.values()) {
				String key = fit.name().toLowerCase(Locale.ROOT);
				check(wrong, patch.id(), "fit " + key, p.getAsJsonObject("fits").get(key).getAsString(),
						Patches.artFor(patch, fit).file() + ".png");
			}
		}
		// The PolymITer chapters are reference art due for removal and are left out on purpose.
		List<String> want = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) if (!chapter.id.endsWith("_polymiter")) want.add(chapter.id);
		List<String> got = new ArrayList<>();
		for (var e : m.getAsJsonArray("chapters")) got.add(e.getAsJsonObject().get("id").getAsString());
		if (!got.equals(want)) wrong.add("chapters " + got + " should be " + want);
		if (!wrong.isEmpty()) helper.fail("the manifest's catalogue has drifted: " + wrong);
		helper.succeed();
	}

	private static void check(List<String> wrong, String owner, String field, Object got, Object want) {
		if (!got.equals(want)) wrong.add(owner + "." + field + " = " + got + ", should be " + want);
	}
}
