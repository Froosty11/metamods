import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.bundles.BundleConfig;
import nu.metacraft.bundles.METAcraftBundles;
import nu.metacraft.lib.config.describe.ConfigSpec;
import nu.metacraft.lib.config.describe.OptionSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestBundleConfig {
	@BeforeAll
	public static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void keepsItsFileKeys() {
		assertEquals(List.of("enable_bundle_rendering"),
				ConfigSpec.of(BundleConfig.class).options().stream().map(OptionSpec::key).toList());
	}

	@Test
	public void theHandWrittenCodecWritesEveryDescribedKey() {
		assertDoesNotThrow(() -> ConfigSpec.of(BundleConfig.class).checkWrittenBy(BundleConfig.CODEC.codec()));
	}

	@Test
	public void bundleRenderingNeedsARestart() {
		assertTrue(ConfigSpec.of(BundleConfig.class).option("enable_bundle_rendering").orElseThrow().restart());
	}

	/**
	 * {@code onInitialize} reads {@code bundleRendering} once, to register the mod's resource pack
	 * assets - a {@code /config} save can't retroactively add or remove that registration, so every
	 * reader (including this accessor) must agree with the value read at startup, not a live one. The
	 * JUnit environment doesn't dispatch Fabric entrypoints, so this calls {@code onInitialize} itself.
	 */
	@Test
	public void bundleRenderingAtStartupMatchesTheConfigReadAtInit() {
		new METAcraftBundles().onInitialize();
		assertEquals(BundleConfig.getInstance().bundleRendering(), METAcraftBundles.bundleRenderingAtStartup());
	}

	@Test
	public void readsTheFileWrittenBeforeTheChange() throws Exception {
		try (var in = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/bundles_before.json")))) {
			var read = BundleConfig.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseReader(in)).getOrThrow();
			assertEquals(BundleConfig.DEFAULT, read);
		}
	}
}
