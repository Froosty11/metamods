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

	/** The file the mod writes has every key, the help as a comment over each, and reads back as the same config. */
	@GameTest
	public void savedFileIsCommentedAndReadsBack(GameTestHelper helper) {
		OvvarConfig before = OvvarConfig.get();
		try {
			OvvarConfig.modify(c -> c.minigame(c.sewingMinigame(), 9));
			String text = java.nio.file.Files.readString(OvvarConfig.PATH);
			for (String want : List.of("// " + OvvarConfig.HELP.get("stitches"), "\"stitches\": 9", "// " + StashConfig.HELP.get("withdraw"),
					"\"withdraw\":", "// " + DesignStoreConfig.Jdbc.HELP.get("url"), "\"sew_game_modes\": [", "// " + StashConfig.HELP.get("_about"))) {
				if (!text.contains(want)) helper.fail("the file lacks: " + want + "\n" + text);
			}
			if (text.contains("_help")) helper.fail("the file still has a _help block");
			OvvarConfig.reload();
			if (OvvarConfig.get().stitches() != 9) helper.fail("read back stitches " + OvvarConfig.get().stitches());
			if (!OvvarConfig.get().equals(before.minigame(before.sewingMinigame(), 9))) helper.fail("the file did not read back as the config that wrote it");
		} catch (java.io.IOException e) {
			helper.fail("could not read " + OvvarConfig.PATH + ": " + e);
		} finally {
			OvvarConfig.modify(c -> before);
		}
		helper.succeed();
	}

	/** An admin's own comment on a key survives the mod rewriting the file. */
	@GameTest
	public void adminCommentsSurviveARewrite(GameTestHelper helper) {
		OvvarConfig before = OvvarConfig.get();
		try {
			String text = java.nio.file.Files.readString(OvvarConfig.PATH);
			String help = "// " + OvvarConfig.HELP.get("stitches");
			if (!text.contains(help)) helper.fail("no stitches help line to replace");
			java.nio.file.Files.writeString(OvvarConfig.PATH, text.replace(help, "// Our note: six felt right at the playtest"));
			OvvarConfig.reload();
			OvvarConfig.modify(c -> c.minigame(c.sewingMinigame(), 9));
			String after = java.nio.file.Files.readString(OvvarConfig.PATH);
			if (!after.contains("// Our note: six felt right at the playtest")) helper.fail("the admin's comment was lost:\n" + after);
			if (!after.contains("\"stitches\": 9")) helper.fail("the value was not written under the kept comment");
		} catch (java.io.IOException e) {
			helper.fail("could not read " + OvvarConfig.PATH + ": " + e);
		} finally {
			OvvarConfig.modify(c -> before);
			OvvarConfig.save();
		}
		helper.succeed();
	}

	/** The old ovvar.json with _help blocks is read when there is no ovvar.json5, then rewritten as the new file. */
	@GameTest
	public void legacyFileIsReadOnceAndRewritten(GameTestHelper helper) {
		OvvarConfig before = OvvarConfig.get();
		try {
			java.nio.file.Files.writeString(OvvarConfig.LEGACY_PATH, """
					{
						"_help": {"_about": "old", "stitches": "old help"},
						"sewing_minigame": true,
						"stitches": 7,
						"stash": {"_help": {"withdraw": "old"}, "withdraw": false}
					}
					""");
			java.nio.file.Files.delete(OvvarConfig.PATH);
			OvvarConfig.reload();
			if (OvvarConfig.get().stitches() != 7 || OvvarConfig.get().stash().withdraw()) helper.fail("the old file was not read: " + OvvarConfig.get());
			if (!java.nio.file.Files.exists(OvvarConfig.PATH)) helper.fail("no " + OvvarConfig.PATH.getFileName() + " written from the old file");
			if (java.nio.file.Files.exists(OvvarConfig.LEGACY_PATH)) helper.fail("the old file is still there");
			if (java.nio.file.Files.readString(OvvarConfig.PATH).contains("_help")) helper.fail("the new file carried the _help blocks over");
		} catch (java.io.IOException e) {
			helper.fail("file trouble: " + e);
		} finally {
			OvvarConfig.modify(c -> before);
			OvvarConfig.save();
		}
		helper.succeed();
	}

	/** A value the codec refuses in the file keeps the config that was, with the file named in the error. */
	@GameTest
	public void badFileIsRefusedAndTheOldConfigStays(GameTestHelper helper) {
		OvvarConfig before = OvvarConfig.get();
		try {
			String text = java.nio.file.Files.readString(OvvarConfig.PATH);
			java.nio.file.Files.writeString(OvvarConfig.PATH, text.replace("\"stitches\": " + before.stitches(), "\"stitches\": 99"));
			try {
				OvvarConfig.reload();
				helper.fail("stitches 99 was read from the file");
			} catch (IllegalStateException expected) {
				if (!expected.getMessage().contains(OvvarConfig.PATH.getFileName().toString())) helper.fail("the error does not name the file: " + expected.getMessage());
			}
			if (OvvarConfig.get().stitches() != before.stitches()) helper.fail("the config changed on a refused file");
		} catch (java.io.IOException e) {
			helper.fail("file trouble: " + e);
		} finally {
			OvvarConfig.modify(c -> before);
			OvvarConfig.save();
		}
		helper.succeed();
	}

	private static OvvarConfig ok(GameTestHelper helper, DataResult<OvvarConfig> result) {
		if (result.isError()) helper.fail("refused: " + result.error().orElseThrow().message());
		return result.getOrThrow();
	}
}
