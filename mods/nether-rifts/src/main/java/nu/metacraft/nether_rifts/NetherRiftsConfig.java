package nu.metacraft.nether_rifts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import nu.metacraft.nether_rifts.raid.Roster;
import nu.metacraft.nether_rifts.raid.Slot;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;
import se.metacraft.portalopening.raid.MobEntry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@code config/nether-rifts.json5}. */
public record NetherRiftsConfig(
		Tiers tiers, Timers timers, Ranges ranges, int activeRaidCap,
		double trooperScalingPerPlayer, double trooperScalingCap,
		Map<Block, Block> frameCollapse, List<Identifier> scfNetherPortalTypes,
		Map<Slot, MobEntry> roster
) {

	public record Tiers(int smallMax, int mediumMax) {
		public static final Tiers DEFAULT = new Tiers(20, 60);
		public static final Codec<Tiers> CODEC = RecordCodecBuilder.<Tiers>create(i -> i.group(
				Codec.intRange(6, 441).fieldOf("small_max").forGetter(Tiers::smallMax),
				Codec.intRange(6, 441).fieldOf("medium_max").forGetter(Tiers::mediumMax)
		).apply(i, Tiers::new)).validate(Tiers::check);

		/** Medium rifts must be reachable: no small rift may be bigger than the largest medium one. */
		private static DataResult<Tiers> check(Tiers tiers) {
			return tiers.smallMax() <= tiers.mediumMax()
					? DataResult.success(tiers)
					: DataResult.error(() -> "tiers: small_max (" + tiers.smallMax() + ") must not be above medium_max (" + tiers.mediumMax() + ")");
		}
	}

	/** All in ticks. */
	public record Timers(int unstableFallback, int countdown, int glowAfter, int abandonAfter) {
		public static final Timers DEFAULT = new Timers(6000, 200, 1800, 3600);
		public static final Codec<Timers> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 1_000_000).fieldOf("unstable_fallback").forGetter(Timers::unstableFallback),
				Codec.intRange(1, 1_000_000).fieldOf("countdown").forGetter(Timers::countdown),
				Codec.intRange(1, 1_000_000).fieldOf("glow_after").forGetter(Timers::glowAfter),
				Codec.intRange(1, 1_000_000).fieldOf("abandon_after").forGetter(Timers::abandonAfter)
		).apply(i, Timers::new));
	}

	/** All in blocks from the rift's centre. */
	public record Ranges(int warning, int participants, int tether) {
		public static final Ranges DEFAULT = new Ranges(32, 48, 64);
		public static final Codec<Ranges> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 256).fieldOf("warning").forGetter(Ranges::warning),
				Codec.intRange(1, 256).fieldOf("participants").forGetter(Ranges::participants),
				Codec.intRange(1, 256).fieldOf("tether").forGetter(Ranges::tether)
		).apply(i, Ranges::new));
	}

	public static final NetherRiftsConfig DEFAULT = new NetherRiftsConfig(
			Tiers.DEFAULT, Timers.DEFAULT, Ranges.DEFAULT, 4, 0.25, 2.0,
			Map.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN), List.of(), Roster.DEFAULT
	);

	private static final Codec<Map<Block, Block>> BLOCK_MAP = Codec.unboundedMap(
			BuiltInRegistries.BLOCK.byNameCodec(), BuiltInRegistries.BLOCK.byNameCodec()
	);

	public static final MapCodec<NetherRiftsConfig> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			CommentCodec.comment(
					section("tiers", Tiers.CODEC, Tiers.DEFAULT),
					"Rift blocks up to small_max make a small raid (3 waves), up to medium_max a medium one (5), more a large one (7)."
			).forGetter(NetherRiftsConfig::tiers),
			CommentCodec.comment(
					section("timers", Timers.CODEC, Timers.DEFAULT),
					"Ticks (20 a second): unstable rifts settle back, the countdown before wave 1, when stragglers glow, when an abandoned raid collapses."
			).forGetter(NetherRiftsConfig::timers),
			CommentCodec.comment(
					section("ranges", Ranges.CODEC, Ranges.DEFAULT),
					"Blocks from a rift's centre: who is warned, who takes part, how far rift mobs may stray."
			).forGetter(NetherRiftsConfig::ranges),
			CommentCodec.comment(
					Codec.intRange(0, 1000).optionalFieldOf("active_raid_cap", 4),
					"How many rifts may be torn open at once, server-wide."
			).forGetter(NetherRiftsConfig::activeRaidCap),
			CommentCodec.comment(
					Codec.doubleRange(0, 10).optionalFieldOf("trooper_scaling_per_player", 0.25),
					"Each player past the first adds this share of troopers to a wave."
			).forGetter(NetherRiftsConfig::trooperScalingPerPlayer),
			CommentCodec.comment(
					Codec.doubleRange(1, 10).optionalFieldOf("trooper_scaling_cap", 2.0),
					"Troopers never grow past this many times the vanilla count."
			).forGetter(NetherRiftsConfig::trooperScalingCap),
			CommentCodec.comment(
					BLOCK_MAP.optionalFieldOf("frame_collapse", Map.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN)),
					"What each frame block becomes when its rift closes. Unlisted blocks become air; crying-obsidian rifts always leave air."
			).forGetter(NetherRiftsConfig::frameCollapse),
			CommentCodec.comment(
					Identifier.CODEC.listOf().optionalFieldOf("scf_nether_portal_types", List.of()),
					"simple-custom-features dynamic portal blocks that count as nether portals."
			).forGetter(NetherRiftsConfig::scfNetherPortalTypes),
			CommentCodec.comment(
					Roster.CODEC.optionalFieldOf("roster", Roster.DEFAULT),
					"What each wave slot spawns: portal-opening mob entries (weighted entity NBT)."
			).forGetter(NetherRiftsConfig::roster)
	).apply(i, NetherRiftsConfig::new));

	public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(NetherRifts.MODID + ".json5");

	private static final ConfigContainer<NetherRiftsConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> DEFAULT
	).withPath(PATH).build(NetherRifts.MODID);

	private static <T> MapCodec<T> section(String name, Codec<T> codec, T defaults) {
		return codec.optionalFieldOf(name).xmap(present -> present.orElse(defaults), Optional::of);
	}

	public static NetherRiftsConfig getInstance() {
		return CONTAINER.get();
	}

	public static void reload() {
		CONTAINER.reload();
	}
}
