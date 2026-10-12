import de.tomalbrc.bil.core.model.Node;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.nether_rifts.NetherRifts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated models load in BIL with the bones and animations the mobs play. */
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
		assertModel("war_drummer", Set.of("body", "head", "arm_left", "arm_right", "leg_left", "leg_right", "drum", "banner"),
			Set.of("idle", "beat", "big_beat"));
	}

	@Test
	public void ghastBombardier() {
		assertModel("ghast_bombardier", Set.of("body", "head", "arm_left", "arm_right", "legs"), Set.of("idle", "drop_bomb"));
	}

	@Test
	public void ghastHarness() {
		assertModel("ghast_harness", Set.of("harness", "goggles"), Set.of("idle"));
	}
}
