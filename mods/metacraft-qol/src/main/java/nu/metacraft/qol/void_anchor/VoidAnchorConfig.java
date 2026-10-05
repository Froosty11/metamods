package nu.metacraft.qol.void_anchor;

import nu.metacraft.qol.Qol;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.lib.config.CommentCodec;
import nu.metacraft.qol.QolConfig;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The void anchor's section of {@code metacraft-qol.json}. */
public record VoidAnchorConfig(
		boolean enabled, Identifier fuelItem, int triggerYOffset, double riftDepth, double descentSpeed, int riftTicks, float riftSize
) {

	public static final VoidAnchorConfig DEFAULT = new VoidAnchorConfig(
			true, Identifier.withDefaultNamespace("end_crystal"), 0, 6.0, 0.3, 30, 4.0f
	);

	public static final MapCodec<VoidAnchorConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					CommentCodec.comment(
							Codec.BOOL.fieldOf("enabled"),
							"Whether void anchors rescue anyone. Off, the block stays, but using it only says void anchors are off."
					).forGetter(VoidAnchorConfig::enabled),
					CommentCodec.comment(
							Identifier.CODEC.fieldOf("fuel_item"),
							"The item that adds one charge to a void anchor.",
							"An id that names no item is logged once, and then nothing charges an anchor."
					).forGetter(VoidAnchorConfig::fuelItem),
					CommentCodec.comment(
							Codec.intRange(-64, 256).fieldOf("trigger_y_offset"),
							"A rift opens when a player drops below the End's lowest Y (0) plus this."
					).forGetter(VoidAnchorConfig::triggerYOffset),
					CommentCodec.comment(
							Codec.doubleRange(1, 24).fieldOf("rift_depth"),
							"How far below the falling player the rift opens, in blocks."
					).forGetter(VoidAnchorConfig::riftDepth),
					CommentCodec.comment(
							Codec.doubleRange(0.05, 2).fieldOf("descent_speed"),
							"How fast the player sinks into the rift, in blocks per tick."
					).forGetter(VoidAnchorConfig::descentSpeed),
					CommentCodec.comment(
							Codec.intRange(1, 200).fieldOf("rift_ticks"),
							"The longest a player spends sinking before the rift takes them, in ticks."
					).forGetter(VoidAnchorConfig::riftTicks),
					CommentCodec.comment(
							Codec.floatRange(0.5f, 8).fieldOf("rift_size"),
							"How wide the rift is, in blocks."
					).forGetter(VoidAnchorConfig::riftSize)
			).apply(instance, VoidAnchorConfig::new)
	);

	private static final Set<Identifier> WARNED = ConcurrentHashMap.newKeySet();

	public static VoidAnchorConfig getInstance() {
		return QolConfig.getInstance().voidAnchor();
	}

	/** The configured fuel item; empty (and logged once) when the id names no item. */
	public Optional<Item> fuel() {
		var item = BuiltInRegistries.ITEM.getOptional(fuelItem);
		if (item.isEmpty() && WARNED.add(fuelItem)) {
			Qol.LOGGER.warn("fuel_item {} names no item; nothing can charge a void anchor", fuelItem);
		}
		return item;
	}

	public boolean isFuel(ItemStack stack) {
		return !stack.isEmpty() && fuel().map(stack::is).orElse(false);
	}

}
