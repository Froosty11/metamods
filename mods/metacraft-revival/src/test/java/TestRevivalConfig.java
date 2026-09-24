import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.lib.config.describe.ConfigSpec;
import nu.metacraft.lib.config.describe.OptionSpec;
import nu.metacraft.revival.RevivalConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestRevivalConfig {
	@BeforeAll
	public static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void keepsItsFileKeys() {
		assertEquals(List.of("max_wait_time", "revive_duration", "item_pickup", "xp_pickup", "revive_effects"),
				ConfigSpec.of(RevivalConfig.class).options().stream().map(OptionSpec::key).toList());
	}

	@Test
	public void theHandWrittenCodecWritesEveryDescribedKey() {
		assertDoesNotThrow(() -> ConfigSpec.of(RevivalConfig.class).checkWrittenBy(RevivalConfig.CODEC.codec()));
	}

	@Test
	public void maxWaitTimeNeedsAtLeastOne() {
		assertEquals(1.0, ConfigSpec.of(RevivalConfig.class).option("max_wait_time").orElseThrow().min());
	}

	@Test
	public void reviveDurationCannotGoNegative() {
		assertEquals(0.0, ConfigSpec.of(RevivalConfig.class).option("revive_duration").orElseThrow().min());
	}

	/**
	 * {@code IntLimit} (used by {@code reviveEffects}) has no {@code equals()}, so two
	 * independently-built {@code RevivalConfig}s are never {@code Object#equals}; re-encoding both
	 * sides and comparing the JSON is the equivalent check.
	 */
	@Test
	public void readsTheFileWrittenBeforeTheChange() throws Exception {
		try (var in = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/revival_before.json")))) {
			var read = RevivalConfig.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseReader(in)).getOrThrow();
			var readJson = RevivalConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, read).getOrThrow();
			var defaultJson = RevivalConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, RevivalConfig.DEFAULT).getOrThrow();
			assertEquals(defaultJson, readJson);
		}
	}
}
