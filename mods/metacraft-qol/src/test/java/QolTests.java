import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.TestHelper;
import nu.metacraft.qol.Qol;
import nu.metacraft.qol.QolConfig;
import nu.metacraft.qol.concrete_cauldron.ConcreteCauldronConfig;
import nu.metacraft.qol.silence_mobs.SilenceMobsConfig;
import nu.metacraft.qol.void_anchor.VoidAnchorConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Every feature's game tests, run together on one test server (registries freeze after the first init). */
public class QolTests {

	private static boolean initialised = false;

	/** Every test class shares one JVM, so all of them go through here. */
	@BeforeAll
	public static synchronized void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TestHelper.init(
				() -> {
					registerTestCommands();
					VoidAnchorTests.registerTests();
					ConcreteCauldronTests.registerTests();
					SilenceMobsTests.registerTests();
				},
				METAcraftLib::new, METAcraftCore::new, Qol::new
		);
	}

	static void register(String path, Consumer<GameTestHelper> test) {
		Registry.register(BuiltInRegistries.TEST_FUNCTION, Qol.getID(path), test);
	}

	/**
	 * {@code qoltest <feature> <true|false>}: switches a feature on or off through the real config
	 * file and a reload. Each feature's "off" tests run in their own environment (a batch of their
	 * own) whose setup and teardown functions call this, so no other test sees the feature off.
	 */
	static void registerTestCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("qoltest")
						.then(toggle("void_anchor", (config, on) -> {
							var v = config.voidAnchor();
							return new QolConfig(
									new VoidAnchorConfig(on, v.fuelItem(), v.triggerYOffset(), v.riftDepth(), v.descentSpeed(), v.riftTicks(), v.riftSize(), v.riftStyle()),
									config.concreteCauldron(), config.silenceMobs()
							);
						}))
						.then(toggle("concrete_cauldron", (config, on) -> new QolConfig(config.voidAnchor(), new ConcreteCauldronConfig(on), config.silenceMobs())))
						.then(toggle("silence_mobs", (config, on) -> new QolConfig(config.voidAnchor(), config.concreteCauldron(), new SilenceMobsConfig(on))))
		));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> toggle(String feature, BiFunction<QolConfig, Boolean, QolConfig> withEnabled) {
		return Commands.literal(feature).then(Commands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
			var changed = withEnabled.apply(QolConfig.getInstance(), BoolArgumentType.getBool(ctx, "enabled"));
			var json = QolConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, changed).getOrThrow();
			try {
				Files.writeString(QolConfig.PATH, json.toString());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			QolConfig.reload();
			return 1;
		}));
	}

	@Test
	public void gameTests() throws Exception {
		TestHelper.runTestServer("metacraft", "*");
	}

}
