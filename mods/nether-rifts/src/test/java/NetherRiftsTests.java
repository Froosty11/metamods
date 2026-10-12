import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.Bootstrap;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.TestHelper;
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.portal_blocker.PortalBlocker;
import nu.metacraft.simplecustomfeatures.Features;
import nu.metacraft.zones.METAcraftZones;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.metacraft.portalopening.PortalOpening;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Every nether-rifts game test, on one test server (registries freeze after the first init). */
public class NetherRiftsTests {

	/** FAST names blocks, so the registries must be bootstrapped before this class's other statics run. */
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/** Short timers and ranges, so raids finish inside a test and neighbouring tests' players don't take part. */
	public static final NetherRiftsConfig FAST = new NetherRiftsConfig(
			NetherRiftsConfig.Tiers.DEFAULT,
			new NetherRiftsConfig.Timers(40, 20, 100, 60),
			new NetherRiftsConfig.Ranges(10, 10, 16),
			100, 0.25, 2.0,
			NetherRiftsConfig.DEFAULT.frameCollapse(), NetherRiftsConfig.DEFAULT.scfNetherPortalTypes(),
			NetherRiftsConfig.DEFAULT.roster()
	);

	private static boolean initialised = false;

	@BeforeAll
	public static synchronized void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TestHelper.init(
				() -> {
					registerTestCommands();
					ModeTests.registerTests();
					EyeLockTests.registerTests();
					ShapeTests.registerTests();
					FormingTests.registerTests();
				},
				METAcraftLib::new, METAcraftCore::new, METAcraftZones::new, PortalBlocker::new,
				PortalOpening::new, Features::new, NetherRifts::new
		);
	}

	public static void register(String path, Consumer<GameTestHelper> test) {
		Registry.register(BuiltInRegistries.TEST_FUNCTION, NetherRifts.getID(path), test);
	}

	public static void writeConfig(NetherRiftsConfig config) {
		var json = NetherRiftsConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, config).getOrThrow();
		try {
			Files.createDirectories(NetherRiftsConfig.PATH.getParent());
			Files.writeString(NetherRiftsConfig.PATH, json.toString());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		NetherRiftsConfig.reload();
	}

	/** {@code riftstest <preset>}: rewrites the real config file and reloads it. Test environments call it. */
	static void registerTestCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("riftstest")
						.then(preset("fast", () -> FAST))
						.then(preset("default", () -> NetherRiftsConfig.DEFAULT))
						.then(preset("cap_one", () -> new NetherRiftsConfig(
								FAST.tiers(), FAST.timers(), FAST.ranges(), 1, FAST.trooperScalingPerPlayer(),
								FAST.trooperScalingCap(), FAST.frameCollapse(), FAST.scfNetherPortalTypes(), FAST.roster()
						)))
		));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> preset(String name, Supplier<NetherRiftsConfig> config) {
		return Commands.literal(name).executes(ctx -> {
			writeConfig(config.get());
			return 1;
		});
	}

	@Test
	public void gameTests() throws Exception {
		TestHelper.runTestServer("nether_rifts", "*");
	}
}
