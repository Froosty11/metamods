package se.metacraft.ovvar.content.enchantment;

import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.advancements.predicates.EnchantmentPredicate;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.predicates.entity.EntityEquipmentPredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import org.jetbrains.annotations.Nullable;
import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.content.PatchLoot;

import java.util.List;

/**
 * Magpie (I-III): better odds of finding patches. A structure chest's patch pool is chosen by what its
 * opener ({@code THIS_ENTITY}) wears, using only vanilla loot conditions ({@link #chestCondition}), so no
 * loot condition type is registered. A mob ovve's patch drop rises through {@link #mobOvvarDrop}.
 */
public final class Magpie {
	private Magpie() {}

	/** A chest's chance of holding a patch for an opener wearing Magpie {@code level}. */
	public static float chestChance(int level, EnchantmentsConfig.Magpie cfg) {
		return PatchLoot.CHANCE + level * cfg.chestChancePerLevel();
	}

	/** A mob ovve's patch drop chance with {@code base} as its un-enchanted chance, capped at 1. */
	public static float mobOvvarChance(float base, int level, EnchantmentsConfig.Magpie cfg) {
		return Math.min(1f, base + level * cfg.mobOvvarChancePerLevel());
	}

	/** The Magpie level the entity's worn garment acts on: 0 for null, a non-living entity, or Magpie switched off. */
	public static int level(@Nullable Entity entity) {
		return entity instanceof LivingEntity living ? OvveEnchantments.level(living, OvveEnchantments.MAGPIE) : 0;
	}

	/**
	 * What Magpie adds to a mob ovve's patch drop chance for this killer, from the current config
	 * (0 when it is off or absent). Part 2's drop roll should use {@link #mobOvvarDrop} instead.
	 * TODO: wire when part 2 (mob ovvar) lands.
	 */
	public static float patchDropBonus(@Nullable Entity killer) {
		return level(killer) * OvvarConfig.get().enchantments().magpie().mobOvvarChancePerLevel();
	}

	/**
	 * The mob ovve's patch-drop roll with Magpie: true when {@code random} comes in under {@code base} raised for
	 * the killer's Magpie level. Part 2's patch-drop roll should call this in place of its own comparison.
	 * TODO: wire when part 2 (mob ovvar) lands.
	 */
	public static boolean mobOvvarDrop(RandomSource random, float base, @Nullable Entity killer, EnchantmentsConfig.Magpie cfg) {
		return random.nextFloat() < mobOvvarChance(base, level(killer), cfg);
	}

	/**
	 * The chest pool's condition, built as the loot table loads, so "Magpie off" means then. Off, or no
	 * {@code ovvar:magpie} in {@code registries}: today's flat {@link PatchLoot#CHANCE}. Otherwise one
	 * branch per case: nobody wearing Magpie on an ovve or frack, exactly level L below the max, or the max
	 * and above (a {@code /give}n Magpie IV counts as III, never as plain).
	 */
	public static LootItemCondition.Builder chestCondition(HolderLookup.Provider registries) {
		LootItemCondition.Builder flat = LootItemRandomChanceCondition.randomChance(PatchLoot.CHANCE);
		EnchantmentsConfig.Magpie cfg = OvvarConfig.get().enchantments().magpie();
		if (!cfg.enabled()) return flat;
		var holder = registries.lookup(Registries.ENCHANTMENT).flatMap(lookup -> lookup.get(OvveEnchantments.MAGPIE));
		var items = registries.lookup(Registries.ITEM);
		// Without the tag (only if the mod's data is missing), `of` below would throw: stay flat instead.
		if (holder.isEmpty() || items.isEmpty() || items.get().get(OvveEnchantments.ENCHANTABLE_OVVE).isEmpty()) return flat;
		Holder<Enchantment> magpie = holder.get();
		int max = Math.max(1, magpie.value().getMaxLevel());
		LootItemCondition.Builder[] branches = new LootItemCondition.Builder[1 + max];
		branches[0] = AllOfCondition.allOf(InvertedLootItemCondition.invert(wears(items.get(), magpie, MinMaxBounds.Ints.atLeast(1))), flat);
		for (int level = 1; level <= max; level++) {
			MinMaxBounds.Ints bounds = level == max ? MinMaxBounds.Ints.atLeast(level) : MinMaxBounds.Ints.exactly(level);
			branches[level] = AllOfCondition.allOf(wears(items.get(), magpie, bounds),
					LootItemRandomChanceCondition.randomChance(chestChance(level, cfg)));
		}
		return AnyOfCondition.anyOf(branches);
	}

	/** The opener wears an ovve or frack ({@code #ovvar:enchantable/ovve}) with Magpie within {@code bounds}: in the legs (an ovve) or the chest (a frack). */
	private static LootItemCondition.Builder wears(HolderGetter<Item> items, Holder<Enchantment> magpie, MinMaxBounds.Ints bounds) {
		ItemPredicate.Builder item = ItemPredicate.Builder.item().of(items, OvveEnchantments.ENCHANTABLE_OVVE).withComponents(DataComponentMatchers.Builder.components()
				.partial(DataComponentPredicates.ENCHANTMENTS, EnchantmentsPredicate.enchantments(
						List.of(new EnchantmentPredicate(HolderSet.direct(magpie), bounds)))).build());
		return AnyOfCondition.anyOf(
				LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS,
						EntityPredicate.Builder.entity().equipment(EntityEquipmentPredicate.Builder.equipment().legs(item))),
				LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS,
						EntityPredicate.Builder.entity().equipment(EntityEquipmentPredicate.Builder.equipment().chest(item))));
	}
}
