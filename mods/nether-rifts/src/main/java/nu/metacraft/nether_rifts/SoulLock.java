package nu.metacraft.nether_rifts;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;

/**
 * While rift mode is on, soul sand drops nothing when broken (silk touch or not) and nether wart can't be planted or
 * kept on it ({@code NetherWartBlockMixin}), so the only nether wart is what the rift mobs drop: early potions come
 * from farming raids, not a farm. The overworld's soul sand is an ancient city's. Worldgen is left alone (nether
 * fortresses still grow their wart), and {@code /rifts stabilise} lifts both.
 */
public final class SoulLock {

	private SoulLock() {}

	public static void init() {
		var soulSand = Blocks.SOUL_SAND.getLootTable().orElseThrow();
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (table.is(soulSand) && locked(context.getLevel())) {
				drops.clear();
			}
		});
	}

	/** Whether rift mode is on in a live world ({@code level} is a ServerLevel); worldgen regions are never locked. */
	public static boolean locked(BlockGetter level) {
		return level instanceof ServerLevel server && RiftWorldState.get(server.getServer()).riftMode();
	}
}
