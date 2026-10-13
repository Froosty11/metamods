import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tomalbrc.bil.core.model.Node;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.nether_rifts.NetherRifts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated models load in BIL with the bones and animations the mobs play; the Bombardier's helmet is an item model. */
public class ModelTests {

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static void assertModel(String name, Set<String> bones, Set<String> animations) {
		var model = BbModelLoader.load(NetherRifts.getID(name));
		assertTrue(model.animations().keySet().containsAll(animations), name + " animations: " + model.animations().keySet());
		var names = model.nodeMap().values().stream().map(Node::name).collect(Collectors.toSet());
		assertTrue(names.containsAll(bones), name + " bones: " + names + ", expected " + bones);
	}

	@Test
	public void warDrummer() {
		assertModel("war_drummer", Set.of("body", "head", "left_arm", "right_arm", "left_leg", "right_leg", "drum", "stick", "stick2"),
			Set.of("idle", "walk", "beat", "beat_legs", "big_beat"));
	}

	/**
	 * The Bombardier on foot is a vanilla piglin: its helmet is an item model in the mod's resource pack, of vanilla
	 * block textures, drawn on the head by its head display. (Elements must keep within -16..32.)
	 */
	@Test
	public void bombardierHelmet() throws IOException {
		var item = json("/assets/nether_rifts/items/bombardier_helmet.json").getAsJsonObject("model");
		assertEquals("nether_rifts:item/bombardier_helmet", item.get("model").getAsString());
		var model = json("/assets/nether_rifts/models/item/bombardier_helmet.json");
		assertTrue(model.getAsJsonObject("display").has("head"), "the helmet has no head display");
		for (var texture : model.getAsJsonObject("textures").entrySet()) {
			assertTrue(texture.getValue().getAsString().startsWith("minecraft:block/"), "texture " + texture);
		}
		var elements = model.getAsJsonArray("elements");
		var names = new java.util.HashSet<String>();
		for (var element : elements) {
			var e = element.getAsJsonObject();
			names.add(e.get("name").getAsString());
			for (var corner : List.of("from", "to")) {
				for (var v : e.getAsJsonArray(corner)) {
					assertTrue(v.getAsDouble() >= -16 && v.getAsDouble() <= 32, e.get("name") + " " + corner + " out of bounds");
				}
			}
		}
		assertTrue(names.containsAll(Set.of("goggle_bridge", "goggle_strap", "lens_left", "lens_right")), "helmet elements: " + names);
	}

	static JsonObject json(String path) throws IOException {
		try (var in = NetherRifts.class.getResourceAsStream(path)) {
			assertNotNull(in, "no " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	@Test
	public void warGhast() {
		var tentacles = IntStream.range(0, 9).mapToObj(i -> "tentacle_" + i);
		var bones = Stream.concat(tentacles, Stream.of("body", "face", "deck", "bomb_left", "bomb_right", "bomber", "bomber_arm",
			"crossbow_left", "crossbow_left_arms", "crossbow_right", "crossbow_right_arms")).collect(Collectors.toSet());
		assertModel("war_ghast", bones, Set.of("idle", "shoot", "drop_bomb", "crew_gone"));
	}

	/** Each bone is a display entity for every player in view: keep the war ghast's count in check. */
	@Test
	public void warGhastBoneBudget() {
		var model = BbModelLoader.load(NetherRifts.getID("war_ghast"));
		assertTrue(model.nodeMap().size() <= 28, "war_ghast has " + model.nodeMap().size() + " bones");
	}
}
