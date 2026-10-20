package metacraft.moredyes.armor;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.color.ModColors;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.Optional;

/**
 * Dyed armour of an animated colour. The stack carries the colour's id in {@link #ANIMATED_DYE}
 * next to a plain {@code dyed_color} of the colour's static {@code rgb}, so with the mod gone it is
 * ordinary dyed armour.
 */
public final class AnimatedArmor {
	/** The animated colour an armour piece was dyed with: the colour id. Server only. */
	public static final DataComponentType<String> ANIMATED_DYE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "animated_dye"),
			DataComponentType.<String>builder().persistent(Codec.STRING).build());

	private AnimatedArmor() {}

	public static void init() {
		PolymerComponent.registerDataComponent(ANIMATED_DYE);
	}

	/**
	 * The animated colour this stack is dyed with. A marker only counts while it still describes the
	 * armour: it needs a {@code dyed_color} equal to the colour's {@code rgb} and a colour that exists
	 * and animates. So washing in a cauldron (which removes {@code dyed_color}), re-colouring by any
	 * other route, or a colour taken out of colors.json all leave plain armour, not an error.
	 */
	public static Optional<ModColor> animatedColour(ItemStack stack) {
		String id = stack.get(ANIMATED_DYE);
		DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
		if (id == null || dyed == null) return Optional.empty();
		ModColor colour = ModColors.getOrNull(id);
		if (colour == null || colour.animation().isEmpty() || (dyed.rgb() & 0xFFFFFF) != (colour.rgb() & 0xFFFFFF)) return Optional.empty();
		return Optional.of(colour);
	}

	/** {@code dyed_color} for armour dyed with this colour: its static rgb. */
	public static DyedItemColor dyedColor(ModColor colour) {
		return new DyedItemColor(colour.rgb() & 0xFFFFFF);
	}
}
