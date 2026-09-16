package metacraft.moredyes.content;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import metacraft.moredyes.MoreDyes;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * The only place that asks Polymer for client-side donor states.
 *
 * Fallback policy: a pool that cannot serve a request is a startup failure, not a downgrade. Another
 * Polymer mod on the same server changed the arithmetic, and that needs a human. The {@link #ERROR}
 * state exists for the runtime paths that should never happen; it is deliberately a command block so
 * it is impossible to mistake for real content.
 */
public final class ClientStates {
	/** What a vanilla client is shown when a visual path fails at runtime. Loud on purpose. */
	public static final BlockState ERROR = Blocks.COMMAND_BLOCK.defaultBlockState();

	private ClientStates() {}

	/**
	 * A donor state from {@code type}'s pool, restricted to one donor block — for the pools where the
	 * blocks in them are not interchangeable to a client.
	 *
	 * The {@link BlockModelType#LEAVES} pool is one: since 26.3 every leaves block in it except
	 * spruce spawns falling leaf particles client-side ({@code FallingParticlesLeavesBlock.
	 * animateTick}, chance 0.01, on {@code UntintedParticleLeavesBlock}/
	 * {@code TintedParticleLeavesBlock}), and a vanilla client runs {@code animateTick} on whatever
	 * state it was sent whether or not the model it is wearing has anything to do with leaves. So our
	 * stained glass shed azalea leaves. {@code Blocks.SPRUCE_LEAVES} is a plain {@code LeavesBlock}
	 * with no particle of its own, which is what this is for.
	 *
	 * Polymer's own {@code BlockResourceCreator.requestBlock} takes a predicate for exactly this, but
	 * the facade that owns the one live creator ({@code PolymerBlockResourceUtils.CREATOR}) is
	 * package-private and exposes no predicate overload. So states in front of the wanted block are
	 * requested and dropped instead. They are removed from the front of the pool either way, so only
	 * the first caller pays; the count is logged because it is a real cost to every other mod sharing
	 * the pool.
	 */
	public static BlockState requestFrom(String what, BlockModelType type, Block donor, PolymerBlockModel... models) {
		int left = PolymerBlockResourceUtils.getBlocksLeft(type);
		int burned = 0;
		for (int attempt = 0; attempt <= left; attempt++) {
			BlockState state = PolymerBlockResourceUtils.requestBlock(type, models);
			if (state == null) break;
			if (state.is(donor)) {
				if (burned > 0) {
					MoreDyes.LOGGER.info("[{}] pool {}: burned {} state(s) of other blocks to reach {}",
							MoreDyes.MOD_ID, type, burned, BuiltInRegistries.BLOCK.getKey(donor));
				}
				return state;
			}
			burned++;
		}
		throw new IllegalStateException("[" + MoreDyes.MOD_ID + "] Polymer pool " + type + " has no "
				+ BuiltInRegistries.BLOCK.getKey(donor) + " state left (" + left + " in the pool, " + burned
				+ " burned) while registering " + what + ". That block is the only one in the pool whose"
				+ " states a client can wear without side effects, so the server cannot start with this"
				+ " content half-registered.");
	}

	/**
	 * How many states of one block a pool can ever hand out: the ones whose waterlogging matches the
	 * pool, less the one {@code DefaultModelData.generateDefault} keeps out of it — for a leaves
	 * block, its default state with {@code persistent} set, which is where Polymer sends a donor
	 * state a client has no pack for. Thirteen, for spruce leaves in {@link BlockModelType#LEAVES}.
	 */
	public static int donorStatesOf(Block block, BlockModelType type) {
		boolean waterlogged = type.name().endsWith("_WATERLOGGED");
		int states = 0;
		for (BlockState state : block.getStateDefinition().getPossibleStates()) {
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)
					&& state.getValue(BlockStateProperties.WATERLOGGED) != waterlogged) {
				continue;
			}
			states++;
		}
		return states - 1;
	}

	public static BlockState request(String what, BlockModelType type, PolymerBlockModel... models) {
		int left = PolymerBlockResourceUtils.getBlocksLeft(type);
		BlockState state = PolymerBlockResourceUtils.requestBlock(type, models);
		if (state == null) {
			throw new IllegalStateException("[" + MoreDyes.MOD_ID + "] Polymer pool " + type
					+ " is exhausted (" + left + " left) while registering " + what
					+ ". Another Polymer mod is using the same pool; the server cannot start with this content half-registered.");
		}
		return state;
	}

	/** Log and return the loud error state for a server state that has no client mapping. */
	public static BlockState error(BlockState state) {
		MoreDyes.LOGGER.error("[{}] no client state for {} — showing the error block", MoreDyes.MOD_ID, state);
		return ERROR;
	}

	/** The client-side state of another block's default state (ours or vanilla). */
	public static BlockState clientStateOf(Block block, @Nullable PacketContext context) {
		BlockState state = block.defaultBlockState();
		return block instanceof PolymerBlock polymer ? polymer.getPolymerBlockState(state, context) : state;
	}

	public static BlockState requestEmpty(String what, BlockModelType type) {
		BlockState state = PolymerBlockResourceUtils.requestEmpty(type);
		if (state == null) {
			throw new IllegalStateException("[" + MoreDyes.MOD_ID + "] Polymer has no empty donor state for "
					+ type + " while registering " + what);
		}
		return state;
	}
}
