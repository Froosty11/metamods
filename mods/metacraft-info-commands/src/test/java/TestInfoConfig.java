import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.info_commands.InfoConfig;
import nu.metacraft.lib.config.describe.ConfigSpec;
import nu.metacraft.lib.config.describe.OptionSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestInfoConfig {
	@BeforeAll
	public static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void keepsItsFileKeys() {
		assertEquals(List.of("commands", "resendCommandTreeOnReload", "enableResendCommandTreeCommand",
						"infoMessageIntervalTicks", "infoMessagePrefix", "infoMessages"),
				ConfigSpec.of(InfoConfig.class).options().stream().map(OptionSpec::key).toList());
	}

	@Test
	public void theHandWrittenCodecWritesEveryDescribedKey() {
		assertDoesNotThrow(() -> ConfigSpec.of(InfoConfig.class).checkWrittenBy(InfoConfig.CODEC.codec()));
	}

	@Test
	public void resendCommandTreeCommandNeedsARestart() {
		assertTrue(ConfigSpec.of(InfoConfig.class).option("enableResendCommandTreeCommand").orElseThrow().restart());
	}

	@Test
	public void resendCommandTreeOnReloadIsLive() {
		assertFalse(ConfigSpec.of(InfoConfig.class).option("resendCommandTreeOnReload").orElseThrow().restart());
	}

	@Test
	public void infoMessageIntervalTicksIsLive() {
		assertFalse(ConfigSpec.of(InfoConfig.class).option("infoMessageIntervalTicks").orElseThrow().restart());
	}

	@Test
	public void readsTheFileWrittenBeforeTheChange() throws Exception {
		try (var in = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/info_commands_before.json")))) {
			var read = InfoConfig.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseReader(in)).getOrThrow();
			var readJson = InfoConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, read).getOrThrow();
			var defaultJson = InfoConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, InfoConfig.DEFAULT).getOrThrow();
			assertEquals(defaultJson, readJson);
		}
	}
}
