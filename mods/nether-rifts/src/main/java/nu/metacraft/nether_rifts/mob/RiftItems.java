package nu.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nu.metacraft.nether_rifts.NetherRifts;

/** The rift mobs' gear that has no vanilla look of its own: server-side (Polymer) items. */
public final class RiftItems {

	private static final ResourceKey<Item> BOMBARDIER_HELMET_KEY = ResourceKey.create(Registries.ITEM, NetherRifts.getID("bombardier_helmet"));

	/**
	 * The Ghast Bombardier's goggles. Only ever on its head: in no creative tab, no recipe, and it
	 * never drops. Clients see paper with the item model {@code nether_rifts:bombardier_helmet} (from the mod's
	 * resource pack), which a piglin draws on its head by the model's {@code head} display.
	 */
	public static final Item BOMBARDIER_HELMET = Registry.register(BuiltInRegistries.ITEM, BOMBARDIER_HELMET_KEY,
			new SimplePolymerItem(new Item.Properties().setId(BOMBARDIER_HELMET_KEY).stacksTo(1), Items.PAPER, true));

	private RiftItems() {}

	public static void init() {
	}
}
