package metacraft.ovvar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import metacraft.ovvar.store.DesignStoreConfig;
import metacraft.ovvar.store.StashConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * {@code config/ovvar.json5}: JSON with a comment above every key, read and written through
 * Jankson ({@link ConfigFile}) and checked by the records' codecs. Written with defaults when
 * missing; a file that does not parse is an error at startup rather than silently replaced.
 * {@code /ovvar config}, {@code /ovvar minigame} and {@code /ovvar reload} edit, save and re-read it.
 *
 * @param sewingMinigame sew on a stand through the stitching dialog ({@link metacraft.ovvar.sewing.SewingGame})
 *					   instead of in one click
 * @param stitches	   how many stitches a cell-sized patch takes in the minigame; a longer outline
 *					   takes proportionally more ({@link metacraft.ovvar.sewing.Seam#stitchesFor})
 * @param server	     what this server calls itself, for the MOTD ({@link ServerConfig}, {@link Motd})
 * @param designs	    where the players' wardrobes live and what sewing does without it ({@link DesignStoreConfig})
 * @param stash		  this server's role, and the rules for taking patches out of the stash ({@link StashConfig})
 */
public record OvvarConfig(boolean sewingMinigame, int stitches, ServerConfig server, DesignStoreConfig designs, StashConfig stash) {
	public static final int MIN_STITCHES = 1, MAX_STITCHES = 16;

	/** Written into the file as a comment above each key, and shown by {@code /ovvar config <key>}. */
	public static final Map<String, String> HELP = new LinkedHashMap<>();
	static {
		HELP.put("_about", "Ovvar: student overalls with sewn-on patches. /ovvar config and /ovvar minigame rewrite this file; comments are kept. Every key has a default; a missing key means the default.");
		HELP.put("sewing_minigame", "true: sewing a patch on a stand opens the stitching dialog and takes a few pulls. false: one click sews.");
		HELP.put("stitches", "How many pulls a cell-sized patch takes in the minigame (1-16); bigger patches take proportionally more.");
		HELP.put("server", "What this server calls itself, for the MOTD. See its _help.");
		HELP.put("designs", "The shared wardrobe store: where players' patches live. See its _help.");
		HELP.put("stash", "What this server is (survival or minigame) and the rules for patches here. See its _help.");
	}

	// optionalFieldOf(key, default) omits a block that equals its default; optionalFieldOf(key)
	// (no default) always encodes the Optional it is given, so wrapping it with xmap keeps every
	// block in the encoding whatever it is set to, while still defaulting to it when missing.
	public static final MapCodec<OvvarConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.BOOL.fieldOf("sewing_minigame").forGetter(OvvarConfig::sewingMinigame),
			Codec.intRange(MIN_STITCHES, MAX_STITCHES).fieldOf("stitches").forGetter(OvvarConfig::stitches),
			ServerConfig.CODEC.codec().optionalFieldOf("server").xmap(o -> o.orElse(ServerConfig.DEFAULT), Optional::of).forGetter(OvvarConfig::server),
			DesignStoreConfig.CODEC.codec().optionalFieldOf("designs").xmap(o -> o.orElse(DesignStoreConfig.DEFAULT), Optional::of).forGetter(OvvarConfig::designs),
			StashConfig.CODEC.codec().optionalFieldOf("stash").xmap(o -> o.orElse(StashConfig.DEFAULT), Optional::of).forGetter(OvvarConfig::stash)
	).apply(instance, OvvarConfig::new));

	public static final OvvarConfig DEFAULT = new OvvarConfig(true, 6, ServerConfig.DEFAULT, DesignStoreConfig.DEFAULT, StashConfig.DEFAULT);
	/** {@code config/ovvar.json5}: JSON with a comment above every key. */
	public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(Ovvar.MOD_ID + ".json5");
	/** The file's old name, plain JSON with {@code _help} blocks; read once and rewritten as {@link #PATH}. */
	public static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve(Ovvar.MOD_ID + ".json");

	private static @Nullable OvvarConfig config;

	public static synchronized OvvarConfig get() {
		if (config == null) reload();
		return config;
	}

	public OvvarConfig minigame(boolean on, int stitches) {
		return new OvvarConfig(on, stitches > 0 ? stitches : stitches(), server, designs, stash);
	}

	/** The same config with other store settings (a test, a command). */
	public OvvarConfig designs(DesignStoreConfig designs) {
		return new OvvarConfig(sewingMinigame, stitches, server, designs, stash);
	}

	/**
	 * Re-reads the file. A missing file means the defaults, written out; {@link #LEGACY_PATH}
	 * stands in for a missing file once and is then replaced. A file that does not parse, or
	 * holds a value the codec refuses, is an error: the config it had stays, or at startup the
	 * server does not start on a config it cannot read.
	 */
	public static synchronized void reload() {
		Path from = Files.exists(PATH) ? PATH : Files.exists(LEGACY_PATH) ? LEGACY_PATH : null;
		if (from == null) {
			config = DEFAULT;
			save();
			return;
		}
		config = ConfigFile.read(from, CODEC);
		if (from == LEGACY_PATH) {
			save();
			try {
				Files.delete(LEGACY_PATH);
			} catch (IOException e) {
				Ovvar.LOGGER.warn("Could not remove the old {} after writing {}: {}", LEGACY_PATH.getFileName(), PATH.getFileName(), e.toString());
			}
		}
	}

	/** Writes the file: every key, its help as a comment above it, an admin's own comments kept. */
	public static synchronized void save() {
		ConfigFile.write(PATH, ConfigKeys.json(get()), ConfigKeys::help, ConfigKeys::about);
	}

	public static synchronized void modify(UnaryOperator<OvvarConfig> change) {
		OvvarConfig changed = change.apply(get());
		if (changed == config) return;
		config = changed;
		save();
	}
}
