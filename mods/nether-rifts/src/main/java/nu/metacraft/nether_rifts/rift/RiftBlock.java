package nu.metacraft.nether_rifts.rift;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marks a rift's positions on the server. Clients see air: the rift is drawn by its displays, it never
 * teleports anyone, and there's no portal overlay.
 */
public class RiftBlock extends Block implements PolymerBlock {

	public RiftBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return Blocks.AIR.defaultBlockState();
	}
}
