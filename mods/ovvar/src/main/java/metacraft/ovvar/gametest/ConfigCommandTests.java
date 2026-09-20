package metacraft.ovvar.gametest;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import metacraft.ovvar.ConfigKeys;
import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.store.DesignStoreConfig;
import metacraft.ovvar.store.StashConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * The config from in game: {@code /ovvar config} lists, shows and sets one key through the config's
 * own codec, {@code /ovvar reload} re-reads the file. Every test puts the live config back as it found it.
 */
public final class ConfigCommandTests {
	/** Every leaf has a dotted key, blocks are walked into, and the _help entries are not keys. */
	@GameTest
	public void keysAreTheLeavesOfTheFile(GameTestHelper helper) {
		List<String> keys = ConfigKeys.keys(OvvarConfig.get());
		for (String want : List.of("sewing_minigame", "stitches", "server.name", "designs.backend", "designs.jdbc.url", "stash.withdraw", "stash.sew_game_modes")) {
			if (!keys.contains(want)) helper.fail("no key " + want + " in " + keys);
		}
		for (String key : keys) {
			if (key.contains("_help") || key.contains("_about")) helper.fail("help text listed as a key: " + key);
			if (key.equals("designs") || key.equals("stash") || key.equals("designs.jdbc")) helper.fail("a block listed as a key: " + key);
		}
		helper.succeed();
	}

	/** Getting a key gives its current value as the file would have it; an unknown key is null. */
	@GameTest
	public void getReadsTheCurrentValue(GameTestHelper helper) {
		OvvarConfig config = OvvarConfig.get();
		if (!new JsonPrimitive(config.stitches()).equals(ConfigKeys.get(config, "stitches"))) helper.fail("stitches read as " + ConfigKeys.get(config, "stitches"));
		if (!new JsonPrimitive(config.stash().withdraw()).equals(ConfigKeys.get(config, "stash.withdraw"))) helper.fail("stash.withdraw read as " + ConfigKeys.get(config, "stash.withdraw"));
		if (ConfigKeys.get(config, "stash.no_such_key") != null) helper.fail("an unknown key had a value");
		if (ConfigKeys.get(config, "stash") != null) helper.fail("a block read as a value");
		helper.succeed();
	}

	/** A set goes through the codec: ranges, enums and lists are checked and the value lands typed. */
	@GameTest
	public void setParsesThroughTheCodec(GameTestHelper helper) {
		OvvarConfig config = OvvarConfig.get();
		OvvarConfig nine = ok(helper, ConfigKeys.with(config, "stitches", "9"));
		if (nine.stitches() != 9) helper.fail("stitches set to " + nine.stitches());
		if (ConfigKeys.with(config, "stitches", "99").isSuccess()) helper.fail("stitches 99 was accepted, over the codec's range");
		if (ConfigKeys.with(config, "stitches", "many").isSuccess()) helper.fail("stitches 'many' was accepted");
		if (ConfigKeys.with(config, "no_such_key", "1").isSuccess()) helper.fail("an unknown key was accepted");
		if (ConfigKeys.with(config, "stash", "1").isSuccess()) helper.fail("a block was accepted as a leaf");
		OvvarConfig named = ok(helper, ConfigKeys.with(config, "server.name", "Skogen"));
		if (!named.server().name().equals("Skogen")) helper.fail("a bare word did not land as a string: " + named.server().name());
		OvvarConfig quoted = ok(helper, ConfigKeys.with(config, "server.name", "\"Two words\""));
		if (!quoted.server().name().equals("Two words")) helper.fail("a quoted string landed as " + quoted.server().name());
		OvvarConfig modes = ok(helper, ConfigKeys.with(config, "stash.sew_game_modes", "[\"survival\"]"));
		if (!modes.stash().sewGameModes().equals(List.of(GameType.SURVIVAL))) helper.fail("game modes landed as " + modes.stash().sewGameModes());
		OvvarConfig bank = ok(helper, ConfigKeys.with(config, "stash.bank_on_pickup", "never"));
		if (bank.stash().bankOnPickup() != StashConfig.Bank.NEVER) helper.fail("bank_on_pickup landed as " + bank.stash().bankOnPickup());
		if (ConfigKeys.with(config, "designs.backend", "cloud").isSuccess()) helper.fail("an unknown enum value was accepted");
		if (ConfigKeys.with(config, "designs.jdbc.password", "hunter2").isSuccess()) helper.fail("the password could be set from chat");
		OvvarConfig untouched = ok(helper, ConfigKeys.with(config, "stitches", "9"));
		if (untouched.stash() != config.stash() && !untouched.stash().equals(config.stash())) helper.fail("setting stitches changed the stash block");
		helper.succeed();
	}

	/** Help comes from the block's own _help map, by the key's last segment. */
	@GameTest
	public void helpIsTheBlocksOwn(GameTestHelper helper) {
		if (!StashConfig.HELP.get("withdraw").equals(ConfigKeys.help("stash.withdraw"))) helper.fail("stash.withdraw help: " + ConfigKeys.help("stash.withdraw"));
		if (!DesignStoreConfig.Jdbc.HELP.get("url").equals(ConfigKeys.help("designs.jdbc.url"))) helper.fail("designs.jdbc.url help: " + ConfigKeys.help("designs.jdbc.url"));
		if (!OvvarConfig.HELP.get("stitches").equals(ConfigKeys.help("stitches"))) helper.fail("stitches help: " + ConfigKeys.help("stitches"));
		if (ConfigKeys.help("stash.no_such_key") != null) helper.fail("an unknown key had help");
		helper.succeed();
	}

	/** {@code /ovvar config <key> <value>} changes the live config and the file; {@code /ovvar reload} reads the file back. */
	@GameTest
	public void configCommandSetsAndReloadReads(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		OvvarConfig before = OvvarConfig.get();
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "ovvar config stitches 9");
			if (OvvarConfig.get().stitches() != 9) helper.fail("/ovvar config stitches 9 left stitches at " + OvvarConfig.get().stitches());
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "ovvar config stitches 99");
			if (OvvarConfig.get().stitches() != 9) helper.fail("a refused value changed stitches to " + OvvarConfig.get().stitches());
			// Edit the file behind the mod's back, as an admin would, then reload: the file wins.
			java.nio.file.Files.writeString(OvvarConfig.PATH, new com.google.gson.GsonBuilder().setPrettyPrinting().create()
					.toJson(ConfigKeys.json(before.minigame(before.sewingMinigame(), 4))));
			if (OvvarConfig.get().stitches() != 9) helper.fail("writing the file changed the live config");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "ovvar reload");
			if (OvvarConfig.get().stitches() != 4) helper.fail("/ovvar reload read stitches as " + OvvarConfig.get().stitches() + ", the file has 4");
		} catch (java.io.IOException e) {
			helper.fail("could not write " + OvvarConfig.PATH + ": " + e);
		} finally {
			OvvarConfig.modify(c -> before);
		}
		helper.succeed();
	}

	private static OvvarConfig ok(GameTestHelper helper, DataResult<OvvarConfig> result) {
		if (result.isError()) helper.fail("refused: " + result.error().orElseThrow().message());
		return result.getOrThrow();
	}
}
