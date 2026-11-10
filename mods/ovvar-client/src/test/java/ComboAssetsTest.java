import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.junit.jupiter.api.Test;
import se.metacraft.ovvar.combo.ComboAssets;
import se.metacraft.ovvarclient.ClientCombos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client builds a combination's equipment definition from its name alone, and it is byte for byte what
 * the server's combination pack holds. {@code goldens.json} is written by ovvar's game test
 * (ComboAssetsParityTests, which also compares it to ComboPack's output live): every chapter, ovve and frack,
 * rolled down, overlapping cells, the seat, given-only designs and every instant design.
 */
public class ComboAssetsTest {
	private static final Path GOLDENS = Paths.get("../ovvar/src/combo/goldens.json");
	private static final String PREFIX = "assets/ovvar/equipment/";

	private static Map<String, JsonElement> goldens() throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(GOLDENS, StandardCharsets.UTF_8)).getAsJsonObject();
		return root.asMap();
	}

	private static String assetPath(String packPath) {
		assertTrue(packPath.startsWith(PREFIX) && packPath.endsWith(".json"), packPath);
		return packPath.substring(PREFIX.length(), packPath.length() - ".json".length());
	}

	@Test
	void everyGoldenCombinationBuildsByteForByteFromItsName() throws IOException {
		Map<String, JsonElement> goldens = goldens();
		Set<String> chapters = new HashSet<>();
		boolean frack = false, rolled = false, seat = false, overlap = false;
		for (Map.Entry<String, JsonElement> golden : goldens.entrySet()) {
			String path = assetPath(golden.getKey());
			assertEquals(Optional.of(golden.getValue().getAsString()), ComboAssets.jsonFor(path), path);
			chapters.add(path.substring(0, path.indexOf('/')));
			frack |= path.startsWith("media/") || path.startsWith("b/");
			rolled |= path.contains("_nercabbad/");
			seat |= path.contains("seat.");
			overlap |= path.contains("back_big.") && path.contains("back_top_left.");
		}
		assertTrue(chapters.size() >= 18, "the goldens cover only " + chapters);
		assertTrue(frack && rolled && seat && overlap, "the goldens miss a case: frack " + frack + ", rolled " + rolled + ", seat " + seat + ", overlap " + overlap);
	}

	/** And the definition parses into what the client draws: one layer per texture, the preview dyeable. */
	@Test
	void everyGoldenParsesIntoTheClientsEquipmentInfo() throws IOException {
		for (Map.Entry<String, JsonElement> golden : goldens().entrySet()) {
			String path = assetPath(golden.getKey());
			Optional<EquipmentClientInfo> built = ClientCombos.build(path);
			assertTrue(built.isPresent(), path);
			EquipmentClientInfo expected = EquipmentClientInfo.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(golden.getValue().getAsString())).getOrThrow();
			assertEquals(expected, built.get(), path);
		}
	}

	@Test
	void namesThatAreNotCombinationsBuildNothing() {
		for (String path : List.of("data/top", "feet/iron", "chest/data/iron", "data/top/", "data/top_nercabbad/front_top_left.itk",
				"data/bottom/front_top_left.itk", "data/top/front_top_left", "data/top/front_top_left.", "data/top/.itk",
				"data/top/no_such_cell.itk", "/top/front_top_left.itk", "data/top/front_top_left.itk/x", "data/sleeve/front_top_left.itk")) {
			assertEquals(Optional.empty(), ComboAssets.jsonFor(path), path);
		}
	}

	/** A name the client cannot read falls back to the garment one level up, never to nothing it could draw instead. */
	@Test
	void anUnreadableNameFallsBackToTheGarmentWithoutPatches() {
		EquipmentClientInfo garment = EquipmentClientInfo.builder().build();
		Map<ResourceKey<EquipmentAsset>, EquipmentClientInfo> loaded = Map.of(key("data/top"), garment);
		assertSame(garment, ClientCombos.buildOrFallBack(key("data/top/no_such_cell.itk"), loaded));
		assertSame(EquipmentAssetManager.MISSING, ClientCombos.buildOrFallBack(key("nothing"), loaded));
		EquipmentClientInfo built = ClientCombos.buildOrFallBack(key("data/top/front_top_left.itk"), loaded);
		assertNotSame(garment, built);
		assertNotSame(EquipmentAssetManager.MISSING, built);
	}

	/**
	 * The mixin itself (fabric-loader-junit runs the tests under Knot with the mod's mixins): an asset
	 * manager with no packs at all hands out a built combination for a combination's name, the same object
	 * the next time, and still MISSING for anything else.
	 */
	@Test
	void theAssetManagerBuildsMissingCombinations() {
		EquipmentAssetManager manager = new EquipmentAssetManager();
		EquipmentClientInfo built = manager.get(key("data/top/front_top_left.itk"));
		assertEquals(ClientCombos.build("data/top/front_top_left.itk").orElseThrow(), built);
		assertSame(built, manager.get(key("data/top/front_top_left.itk")));
		assertSame(EquipmentAssetManager.MISSING, manager.get(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("minecraft", "data/top/front_top_left.itk"))));
		assertSame(EquipmentAssetManager.MISSING, manager.get(key("feet")));
	}

	private static ResourceKey<EquipmentAsset> key(String path) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("ovvar", path));
	}

	/**
	 * The protocol pin: a hash of the cell table and of the definitions of a made-up chapter's every cell.
	 * New chapters and designs don't move it (the client takes them as given); a cell added or moved, or a
	 * changed recipe, does. When it fails, bump ComboAssets.PROTOCOL and put the new hash here: a client on
	 * the old protocol then no longer handshakes and is pushed the pack, instead of building it wrong.
	 */
	@Test
	void theProtocolIsPinned() throws NoSuchAlgorithmException {
		StringBuilder all = new StringBuilder(ComboAssets.CELLS.toString()).append(ComboAssets.PIECE_LAYERS);
		for (String piece : ComboAssets.PIECE_LAYERS.keySet()) {
			StringBuilder combo = new StringBuilder();
			for (Map.Entry<String, ComboAssets.Cell> cell : ComboAssets.CELLS.entrySet()) {
				if (!cell.getValue().piece().equals(piece)) continue;
				if (!combo.isEmpty()) combo.append('-');
				combo.append(cell.getKey()).append(".x-y");
			}
			all.append(ComboAssets.jsonFor(ComboAssets.assetPath("x", piece, false, combo.toString())).orElseThrow());
			if (piece.equals("bottom")) all.append(ComboAssets.jsonFor(ComboAssets.assetPath("x", piece, true, combo.toString())).orElseThrow());
		}
		String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(all.toString().getBytes(StandardCharsets.UTF_8)));
		assertEquals(1, ComboAssets.PROTOCOL);
		assertEquals("fcd61991145ead6c1078e16a5c1c22dd21893a4d", hash, "the combination recipe changed: bump ComboAssets.PROTOCOL and pin the new hash");
		assertEquals("draws_combos/v1", ComboAssets.CHANNEL_PATH);
	}
}
