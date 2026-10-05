package nu.metacraft.qol.void_anchor;

import nu.metacraft.qol.Qol;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import nu.metacraft.lib.util.RegistrationPair;
import nu.metacraft.qol.void_anchor.block.VoidAnchorBlock;

import java.util.function.Function;

public class VoidAnchorBlocks {

	public static final RegistrationPair<Block> VOID_ANCHOR = register(
			"void_anchor", VoidAnchorBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.RESPAWN_ANCHOR)
	);

	public static void init() {

	}

	private static RegistrationPair<Block> register(String id, Function<BlockBehaviour.Properties, Block> creator, BlockBehaviour.Properties settings) {
		var key = ResourceKey.create(Registries.BLOCK, Qol.getID(id));
		return new RegistrationPair<>(key, Registry.register(BuiltInRegistries.BLOCK, key, creator.apply(settings.setId(key))));
	}

}
