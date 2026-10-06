package nu.metacraft.core.item;

import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nu.metacraft.core.METAcraftCore;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * One creative tab, "METAcraft", with every item our mods add. Polymer shows a server's items in a
 * client's creative menu (when the client has Polymer) only through tabs registered with it, and
 * most of our mods register items without one. Mods with a tab of their own (ovvar, moredyes) keep it.
 *
 * <p>The tab is filled when it is shown, so items registered after metacraft-core count too.
 */
public final class METAcraftCreativeTab {

	/** The item namespaces our mods use. A mod adding items under a new namespace adds it here. */
	public static final Set<String> NAMESPACES = Set.of(
			"metacraft", "better_pets", "faster_minecarts", "metacraft_plots", "portable_jukebox", "portal_blocker",
			"simple_custom_features"
	);

	private METAcraftCreativeTab() {}

	public static void init() {
		var tab = PolymerCreativeModeTabUtils.builder()
				.title(Component.literal("METAcraft"))
				.icon(() -> items().stream().findFirst().map(ItemStack::new).orElseGet(() -> new ItemStack(Items.ENDER_EYE)))
				.displayItems((params, output) -> items().forEach(output::accept))
				.build();
		PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(METAcraftCore.getID("items"), tab);
	}

	/** Our items, by id. */
	public static List<Item> items() {
		return BuiltInRegistries.ITEM.entrySet().stream()
				.filter(entry -> NAMESPACES.contains(entry.getKey().identifier().getNamespace()))
				.sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
				.map(entry -> entry.getValue())
				.toList();
	}

}
