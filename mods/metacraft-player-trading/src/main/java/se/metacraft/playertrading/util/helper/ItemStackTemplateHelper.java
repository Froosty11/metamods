package se.metacraft.playertrading.util.helper;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Objects;

public class ItemStackTemplateHelper {

	public static ItemVariant variantOf(ItemStackTemplate template) {
		return ItemVariant.of(template.item().value(), template.components());
	}

	public static boolean matches(ItemVariant itemVariant, ItemStackTemplate template) {
		return itemVariant.isOf(template.item().value()) && Objects.equals(template.components(), itemVariant.getComponentsPatch());
	}

}
