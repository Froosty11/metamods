package metacraft.moredyes.content;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import metacraft.moredyes.MoreDyes;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a family is shown to vanilla clients, decided once at startup from the colour count and
 * Polymer's donor budget, and applied to every colour of that family:
 *
 * <ul>
 *   <li>{@link Look#DONOR}: one visible donor state per server state per colour, carrying our model
 *	   as chunk geometry. Best looking and cheapest for the client, but each colour spends states
 *	   from a finite vanilla pool.</li>
 *   <li>{@link Look#DISPLAY}: one shared invisible donor per server state (hitbox and outline) plus
 *	   an item display per placed block carrying the model. Costs nothing per colour.</li>
 * </ul>
 *
 * Polymer itself has no such fallback: when a pool is empty it hands back null, which
 * {@link ClientStates} turns into a startup failure. This planner is what makes adding a colour a
 * data change: the decision is deterministic, logged, and never differs between colours. Override
 * for testing with {@code -Dmoredyes.look.<family>=donor|display}.
 */
public final class Looks {
	public enum Look { DONOR, DISPLAY }

	private static final Map<Family, Look> PLAN = new EnumMap<>(Family.class);

	private Looks() {}

	public static Look of(Family family) {
		return PLAN.getOrDefault(family, Look.DONOR);
	}

	public static void plan(int colours) {
		// Glass takes spruce leaves donors only (see ClientStates#requestFrom), so its budget is not
		// the LEAVES pool but the states of the one block in it a client can wear quietly.
		donorOnly(Family.STAINED_GLASS, Blocks.SPRUCE_LEAVES, BlockModelType.LEAVES, colours);
		decide(Family.STAINED_GLASS_PANE, barsPools(), 1, colours);
	}

	/**
	 * A family that has only a donor look, drawing one state per colour from one donor block.
	 * {@code GlassBlocks.Glass} has no display path — panes needed one first, glass will get the same
	 * treatment when the colour count asks for it — so there is nothing here to fall back to, and a
	 * pool that cannot seat every colour is a startup failure like any other.
	 */
	private static void donorOnly(Family family, Block donor, BlockModelType pool, int colours) {
		int usable = Math.min(ClientStates.donorStatesOf(donor, pool),
				PolymerBlockResourceUtils.getBlocksLeft(pool) - 1);
		PLAN.put(family, Look.DONOR);
		MoreDyes.LOGGER.info("[{}] {} look: DONOR ({} colour(s); {} in pool {} has room for {})",
				MoreDyes.MOD_ID, family.id, colours, BuiltInRegistries.BLOCK.getKey(donor), pool, usable);
		if (colours > usable) {
			throw new IllegalStateException("[" + MoreDyes.MOD_ID + "] " + family.id + " needs " + colours
					+ " donor states from " + BuiltInRegistries.BLOCK.getKey(donor) + " in pool " + pool
					+ ", which has room for " + usable + ". Give the family a display look before adding"
					+ " more colours; the server cannot start with this content half-registered.");
		}
	}

	/**
	 * @param pools	  every pool the family draws from; the tightest one decides
	 * @param perColour  visible states one colour takes from each pool in DONOR mode
	 */
	private static void decide(Family family, List<BlockModelType> pools, int perColour, int colours) {
		int tightest = Integer.MAX_VALUE;
		BlockModelType tightestPool = null;
		for (BlockModelType pool : pools) {
			// requestBlock keeps the last state of a pool for the invisible donor, so it is not usable.
			int usable = PolymerBlockResourceUtils.getBlocksLeft(pool) - 1;
			if (usable < tightest) {
				tightest = usable;
				tightestPool = pool;
			}
		}
		int fit = perColour == 0 ? Integer.MAX_VALUE : tightest / perColour;
		Look look = fit >= colours ? Look.DONOR : Look.DISPLAY;
		String override = System.getProperty("moredyes.look." + family.id);
		if (override != null) {
			look = Look.valueOf(override.toUpperCase(Locale.ROOT));
			MoreDyes.LOGGER.warn("[{}] {} look forced to {} by -Dmoredyes.look.{}", MoreDyes.MOD_ID, family.id, look, family.id);
		}
		PLAN.put(family, look);
		MoreDyes.LOGGER.info("[{}] {} look: {} ({} colour(s); tightest pool {} has room for {} in donor mode)",
				MoreDyes.MOD_ID, family.id, look, colours, tightestPool, fit);
	}

	/** All 32 copper-bars pools: every horizontal connection set, waterlogged or not. */
	private static List<BlockModelType> barsPools() {
		List<BlockModelType> pools = new ArrayList<>();
		Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		for (int mask = 0; mask < 16; mask++) {
			List<Direction> sides = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				if ((mask & (1 << i)) != 0) sides.add(horizontal[i]);
			}
			pools.add(BlockModelType.getBars(false, sides));
			pools.add(BlockModelType.getBars(true, sides));
		}
		return pools;
	}
}
