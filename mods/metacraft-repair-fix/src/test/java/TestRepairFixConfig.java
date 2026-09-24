import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.lib.config.describe.ConfigSpec;
import nu.metacraft.lib.config.describe.OptionSpec;
import nu.metacraft.repair_fix.RepairFixConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestRepairFixConfig {
	@BeforeAll
	public static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void keepsItsFileKeys() {
		assertEquals(List.of("max_repair_cost", "cap_at_max_level", "base_cost_increase_mode"),
				ConfigSpec.of(RepairFixConfig.class).options().stream().map(OptionSpec::key).toList());
	}

	@Test
	public void theHandWrittenCodecWritesEveryDescribedKey() {
		assertDoesNotThrow(() -> ConfigSpec.of(RepairFixConfig.class).checkWrittenBy(RepairFixConfig.CODEC));
	}

	@Test
	public void maxRepairCostCannotGoNegative() {
		assertEquals(0.0, ConfigSpec.of(RepairFixConfig.class).option("max_repair_cost").orElseThrow().min());
	}

	@Test
	public void readsTheFileWrittenBeforeTheChange() throws Exception {
		try (var in = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/repair_fix_before.json")))) {
			var read = RepairFixConfig.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseReader(in)).getOrThrow();
			assertEquals(RepairFixConfig.DEFAULT, read);
		}
	}
}
