import de.tomalbrc.bil.file.loader.BbModelLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.nether_rifts.NetherRifts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated models load in BIL with the bones and animations the mobs play. */
public class ModelTests {

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static void assertModel(String name, int minBones, Set<String> animations) {
		var model = BbModelLoader.load(NetherRifts.getID(name));
		assertTrue(model.animations().keySet().containsAll(animations), name + " animations: " + model.animations().keySet());
		assertTrue(model.nodeMap().size() >= minBones, name + " has " + model.nodeMap().size() + " bones, expected at least " + minBones);
	}

	@Test
	public void warDrummer() {
		assertModel("war_drummer", 8, Set.of("idle", "beat", "big_beat"));
	}

	@Test
	public void ghastBombardier() {
		assertModel("ghast_bombardier", 5, Set.of("idle", "drop_bomb"));
	}

	@Test
	public void ghastHarness() {
		assertModel("ghast_harness", 2, Set.of("idle"));
	}
}
