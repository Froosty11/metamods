package se.metacraft.ovvar.content;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

import java.util.List;
import java.util.Set;

/**
 * Where the patches nobody can craft come from: structure chests. The chapters' own patches (IT,
 * Data, ITK, and IN made from IT) are crafted — {@code data/ovvar/recipe} — an event's own
 * ({@link #EXCLUSIVE}) come only from the event, and every other patch in the catalogue may turn up, one at a time, in a vanilla structure's chest or a Structory one.
 *
 * <p>Added to the tables as they load rather than shipped as copies of them, so a datapack or
 * another mod that changes a chest's loot keeps its change, and Structory's tables only get a pool
 * when Structory is there to load them.
 */
public final class PatchLoot {
	private PatchLoot() {}

	/** The patches with a recipe; keep in step with {@code data/ovvar/recipe/patch_<id>.json}. */
	public static final Set<String> CRAFTED = Set.of("it", "data", "itk", "in");

	/** The patches only an event hands out: never crafted, never in a chest. */
	public static final Set<String> EXCLUSIVE = Set.of("rivals");

	/** How likely a chest is to hold a patch; which one is even odds among {@link #looted}. */
	public static final float CHANCE = 0.05f;

	/** The bonus chest by the spawn is not a structure's. */
	private static final Identifier BONUS_CHEST = Identifier.withDefaultNamespace("chests/spawn_bonus_chest");

	public static void init() {
		List<Patches.Patch> looted = looted();
		if (looted.isEmpty()) return;
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (!inStructureChest(key.identifier())) return;
			LootPool.Builder pool = LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(CHANCE));
			for (Patches.Patch patch : looted) pool.add(LootItem.lootTableItem(ModContent.patchItem(patch)));
			table.withPool(pool);
		});
	}

	/** Every patch in the catalogue but the crafted and the exclusive ones. */
	public static List<Patches.Patch> looted() {
		return Patches.all().stream().filter(patch -> !CRAFTED.contains(patch.id()) && !EXCLUSIVE.contains(patch.id())).toList();
	}

	/** Vanilla's structure chests, and all of Structory's tables (every one of them fills a chest or a barrel). */
	public static boolean inStructureChest(Identifier table) {
		if (table.getNamespace().equals("structory")) return true;
		return table.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)
				&& table.getPath().startsWith("chests/") && !table.equals(BONUS_CHEST);
	}
}
