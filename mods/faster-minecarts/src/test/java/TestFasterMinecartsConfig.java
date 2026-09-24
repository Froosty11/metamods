import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.faster_minecarts.FasterMinecartsConfig;
import nu.metacraft.faster_minecarts.StartupValue;
import nu.metacraft.lib.config.describe.ConfigSpec;
import nu.metacraft.lib.config.describe.OptionSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TestFasterMinecartsConfig {
	@BeforeAll
	public static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void keepsItsFileKeys() {
		assertEquals(List.of("global_faster_minecarts", "max_minecart_speed", "max_minecart_speed_underwater",
						"dangerous_minecart_speed", "damage_factor", "experimental_minecart_mode"),
				ConfigSpec.of(FasterMinecartsConfig.class).options().stream().map(OptionSpec::key).toList());
	}

	@Test
	public void readsTheFileWrittenBeforeTheChange() throws Exception {
		try (var in = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/faster_minecarts_before.json")))) {
			var read = ConfigSpec.of(FasterMinecartsConfig.class).codec().codec().parse(JsonOps.INSTANCE, JsonParser.parseReader(in)).getOrThrow();
			assertEquals(FasterMinecartsConfig.DEFAULT, read);
		}
	}

	@Test
	public void experimentalModeNeedsARestart() {
		assertTrue(ConfigSpec.of(FasterMinecartsConfig.class).option("experimental_minecart_mode").orElseThrow().restart());
	}

	@Test
	public void theStartupModeKeepsTheFirstValueRead() {
		AtomicReference<FasterMinecartsConfig.ExperimentalMinecartMode> inConfig =
				new AtomicReference<>(FasterMinecartsConfig.ExperimentalMinecartMode.EXPERIMENTAL);
		StartupValue<FasterMinecartsConfig.ExperimentalMinecartMode> mode = new StartupValue<>(inConfig::get);
		assertEquals(FasterMinecartsConfig.ExperimentalMinecartMode.EXPERIMENTAL, mode.get());
		inConfig.set(FasterMinecartsConfig.ExperimentalMinecartMode.LEGACY);   // a reload or a /config save
		assertEquals(FasterMinecartsConfig.ExperimentalMinecartMode.EXPERIMENTAL, mode.get());
	}
}
