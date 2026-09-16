package nu.metacraft.rivals.paint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client-state table (spec §2). A vanilla client can only be shown vanilla blockstates, and a
 * painted cell now needs to say which of its four in-plane neighbours are painted, so paint borrows
 * every state of three donor blocks that render whatever the pack says, have no collision, emit no
 * light and have no client-side behaviour: the two multiface blocks (64 non-waterlogged states each)
 * and redstone wire (1296). Glow lichen is deliberately not a donor: {@code GlowLichenBlock.emission}
 * gives light 7 to every state with a face.
 *
 * <p><b>Tripwire is deliberately not a donor either, and that is the whole point of this table's
 * shape.</b> Polymer's own block pool ({@code eu.pb4.polymer.blocks.api.BlockModelType.TRIPWIRE} and
 * {@code TRIPWIRE_FLAT}) hands out tripwire states, so any mod calling
 * {@code PolymerBlockResourceUtils.requestBlock} is writing {@code minecraft/blockstates/tripwire.json}
 * too. On the minigame server, where moredyes' carpets sit in that pool next to Rivals, the two
 * overrides collided and every floor cell drew nothing at all. Sculk vein, resin clump and redstone
 * wire are in no {@code BlockModelType} pool, which is why wall paint survived; the coexistence rule
 * is pinned by a game test that derives Polymer's pooled blocks and asserts none of {@link #DONORS} is
 * among them. A new donor has to clear the same test.
 *
 * <p><b>Which donor state stands for which paint state is chosen by what is quiet, not by shape.</b>
 * Rivals is played in adventure mode, so the one thing a borrowed state's outline is good for — the
 * targeted-block highlight — never appears, and a donor's shape no longer costs anything. What still
 * costs something is noise: {@code RedstoneWireBlock.animateTick} spawns dust off every wire state
 * whose {@code power} is not 0, and no resource pack can stop it. There are only 209 quiet states in
 * all (64 + 64 multiface, 81 unpowered wire) against 306 paint states, so the budget is spent on the
 * cells a player actually stands on and walks past:
 *
 * <ol>
 * <li><b>Wall cells take the colour's own multiface donor.</b> Attach N/E/S/W is 4 × 16 = 64 states
 *     per colour, which is exactly how many non-waterlogged states a multiface block has: sculk vein
 *     is DATA's, resin clump is IT's, and neither pool has a state to spare. The all-faces-false state
 *     is usable here because the pack replaces the whole blockstate file, so what the client draws is
 *     our quad, not vanilla's union of face slabs (that state's <em>shape</em> is empty, which in
 *     adventure mode nobody can see).</li>
 * <li><b>Floor and ceiling cells take unpowered redstone wire.</b> Attach {@link Direction#DOWN} and
 *     {@link Direction#UP}, 2 × 16 per colour = 64 states out of the 81 wire states at
 *     {@code power=0}.</li>
 * <li><b>Splat masks take whatever wire is left.</b> A splat is the fallback for a cell painted on two
 *     or more faces — the join lines of the arena, far rarer than plain floor and wall — so it is the
 *     one cell kind that can afford powered wire: 17 of the 114 land on the last unpowered states and
 *     the rest on {@code power=1} and up, where the client sprinkles a little dust.</li>
 * </ol>
 *
 * <p>The allocation runs once, in a fixed order, and throws at class load rather than reusing a state,
 * running a pool dry, or quietly putting a floor cell on a powered wire state. {@link #entry()} is the
 * reverse map.
 */
public final class PaintStates {
	/** The donor blocks, all three of them outside every Polymer block pool. */
	public static final List<Block> DONORS = List.of(Blocks.SCULK_VEIN, Blocks.RESIN_CLUMP, Blocks.REDSTONE_WIRE);
	public static final int CONNECTED_PER_COLOR = 6 * 16;
	/** Face masks with at least two faces set: every splat a cell can be, per colour. */
	public static final int SPLAT_PER_COLOR = (1 << 6) - 1 - 6;
	private static final int PER_COLOR = CONNECTED_PER_COLOR + SPLAT_PER_COLOR;
	private static final Direction[] DIRECTIONS = Direction.values();
	/** Connection-bit patterns per (colour, attach face). */
	private static final int BITS = 16;
	/** The multiface donor each colour's wall cells come from, in colour order. */
	private static final List<Block> WALL_DONORS = List.of(Blocks.SCULK_VEIN, Blocks.RESIN_CLUMP);
	/** Wall cells per colour: four attach directions of bit patterns. */
	private static final int WALL_PER_COLOR = 4 * BITS;
	/** What the wire pool has to cover per colour: floors, ceilings and every splat mask. */
	private static final int WIRE_PER_COLOR = 2 * BITS + SPLAT_PER_COLOR;
	/** The attach faces that are not walls, in the order they are handed wire states. */
	private static final List<Direction> FLAT_FACES = List.of(Direction.DOWN, Direction.UP);

	/** The table, indexed {@code colour * PER_COLOR + local} exactly as {@link #entry} decodes it. */
	private static final List<BlockState> TABLE = table();
	private static final Map<BlockState, Entry> ENTRIES = entries();

	private PaintStates() {}

	// ---------------------------------------------------------------- the pools

	/**
	 * Every usable state of a multiface donor, in registry order: never waterlogged, so never drawn
	 * with water in the cell. The all-faces-false state is in: the pack gives it our own model, and its
	 * empty outline shape is invisible in adventure mode.
	 */
	private static List<BlockState> multiface(Block donor) {
		List<BlockState> out = new ArrayList<>();
		for (BlockState state : donor.getStateDefinition().getPossibleStates()) {
			if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) continue;
			out.add(state);
		}
		return out;
	}

	/**
	 * Every redstone-wire state, the quiet ones first: {@code animateTick} spawns dust off every state
	 * with {@code power != 0}, so the 81 unpowered states are handed out before any powered one. The
	 * sort is stable, so within a power level the order is the registry's own and the table is the same
	 * on every start.
	 */
	private static Deque<BlockState> wires() {
		List<BlockState> out = new ArrayList<>(Blocks.REDSTONE_WIRE.getStateDefinition().getPossibleStates());
		out.sort(Comparator.comparingInt((BlockState state) -> state.getValue(RedstoneWireBlock.POWER)));
		return new ArrayDeque<>(out);
	}

	// ---------------------------------------------------------------- the allocator

	private static List<BlockState> table() {
		PaintColor[] colors = PaintColor.values();
		require(colors.length <= WALL_DONORS.size(), colors.length + " colours but " + WALL_DONORS.size()
				+ " multiface donors to give each one its own wall cells");
		BlockState[] table = new BlockState[colors.length * PER_COLOR];
		// 1. Wall cells: the colour's own multiface donor, one state per (direction, bits).
		for (PaintColor color : colors) {
			Block donor = WALL_DONORS.get(color.ordinal());
			Deque<BlockState> pool = new ArrayDeque<>(multiface(donor));
			require(pool.size() >= WALL_PER_COLOR, donor + " has " + pool.size() + " usable states but "
					+ color + "'s wall cells need " + WALL_PER_COLOR);
			for (Direction wall : Direction.Plane.HORIZONTAL) {
				for (int bits = 0; bits < BITS; bits++) table[index(color, wall, bits)] = pool.remove();
			}
		}
		// 2. Floors and ceilings, then 3. the splat masks, from the wire pool. The colours are
		// interleaved rather than filled one after the other, so when the quiet states run out part way
		// through the splats they run out for both teams at the same mask instead of for one only.
		Deque<BlockState> wire = wires();
		require(wire.size() >= colors.length * WIRE_PER_COLOR, "redstone wire has " + wire.size()
				+ " states but paint needs " + colors.length * WIRE_PER_COLOR);
		for (Direction face : FLAT_FACES) {
			for (int bits = 0; bits < BITS; bits++) {
				for (PaintColor color : colors) table[index(color, face, bits)] = wire.remove();
			}
		}
		for (int mask = 0; mask < SPLAT_PER_COLOR; mask++) {
			for (PaintColor color : colors) table[color.ordinal() * PER_COLOR + CONNECTED_PER_COLOR + mask] = wire.remove();
		}
		// The cells a player stands on and walks past may not be powered wire: vanilla's animateTick
		// sprinkles dust off every powered state, whatever the pack says the state looks like.
		for (PaintColor color : colors) {
			for (Direction face : FLAT_FACES) {
				for (int bits = 0; bits < BITS; bits++) {
					BlockState state = table[index(color, face, bits)];
					require(state.getValue(RedstoneWireBlock.POWER) == 0,
							"the quiet wire states ran out: " + color + " " + face + " " + bits + " is " + state);
				}
			}
		}
		List<BlockState> out = List.of(table);
		require(out.size() == Set.copyOf(out).size(), "a donor state was handed out twice");
		return out;
	}

	/** Where {@code (colour, face, bits)} lives in the table. */
	private static int index(PaintColor color, Direction face, int bits) {
		return color.ordinal() * PER_COLOR + face.ordinal() * BITS + bits;
	}

	/** Fail at class load, with the mod's own prefix: a donor that ran out is a bug in the table, not a runtime condition. */
	private static void require(boolean condition, String what) {
		if (!condition) throw new IllegalStateException("[" + Rivals.MOD_ID + "] " + what);
	}

	private static Map<BlockState, Entry> entries() {
		Map<BlockState, Entry> out = new HashMap<>();
		for (int i = 0; i < TABLE.size(); i++) {
			PaintColor color = PaintColor.values()[i / PER_COLOR];
			int local = i % PER_COLOR;
			out.put(TABLE.get(i), local < CONNECTED_PER_COLOR
					? new Entry(color, DIRECTIONS[local / 16], local % 16, 1 << (local / 16))
					: new Entry(color, null, 0, splatMask(local - CONNECTED_PER_COLOR)));
		}
		return Map.copyOf(out);
	}

	/** The {@code index}-th face mask with at least two bits, counting up from 0. */
	private static int splatMask(int index) {
		for (int mask = 1; mask < 64; mask++) {
			if (Integer.bitCount(mask) < 2) continue;
			if (index-- == 0) return mask;
		}
		throw new IllegalArgumentException("no splat mask " + index);
	}

	// ---------------------------------------------------------------- the lookups

	public static BlockState connected(PaintColor color, Direction face, int bits) {
		return TABLE.get(index(color, face, bits & 15));
	}

	/** {@code faceMask} bit i = Direction i painted. One face is a connected state with no bits. */
	public static BlockState splat(PaintColor color, int faceMask) {
		int popcount = Integer.bitCount(faceMask & 63);
		if (popcount == 0) throw new IllegalArgumentException("empty face mask");
		if (popcount == 1) return connected(color, DIRECTIONS[Integer.numberOfTrailingZeros(faceMask)], 0);
		// Number the masks with ≥ 2 bits in increasing order: 0..56.
		int index = 0;
		for (int mask = 1; mask < 64; mask++) {
			if (Integer.bitCount(mask) < 2) continue;
			if (mask == (faceMask & 63)) break;
			index++;
		}
		return TABLE.get(color.ordinal() * PER_COLOR + CONNECTED_PER_COLOR + index);
	}

	/**
	 * The state every paint particle of this colour carries: a wall cell, which is always one of the
	 * multiface donors (sculk vein for DATA, resin clump for IT), with all four bits set so the sprite
	 * is that colour's all-connected tile.
	 *
	 * <p>It has to be a multiface state. The client takes a block crumb's sprite from the state's model
	 * {@code particle} texture, which the pack points at the paint tile, but it also runs the state
	 * through vanilla's {@code BlockColors} — and a redstone-wire-backed state would come out tinted
	 * dark red whatever the texture said.
	 */
	public static BlockState particles(PaintColor color) {
		return connected(color, Direction.NORTH, BITS - 1);
	}

	/** Every client state in use, for tests and the pack. */
	public static List<BlockState> all() {
		return TABLE;
	}

	/** Which server state a client state stands for, for the pack: (colour, face, bits) or (colour, mask). */
	public record Entry(PaintColor color, @Nullable Direction face, int bits, int faceMask) {}

	public static Entry entry(BlockState client) {
		Entry entry = ENTRIES.get(client);
		if (entry == null) throw new IllegalArgumentException("not a paint state: " + client);
		return entry;
	}
}
