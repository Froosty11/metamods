package se.metacraft.ovvar.content;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import se.metacraft.ovvar.combo.ComboAssets;

import java.util.ArrayList;
import java.util.List;

/**
 * What a garment is drawn in: its chapter's cloth, or a named variant of it ({@link Variants}). Every
 * base texture, empty and combination equipment asset, combos.json key, chest wrap and Danse base layer
 * is per cloth, named by {@link #id}: {@code it}, or {@code it.spaceships} for a variant. The chapter
 * decides everything else (slot, halves, rolling down, the wardrobe design); a variant only changes the
 * cloth under the patches.
 */
public record Cloth(Chapter chapter, Variants.@Nullable Variant variant) {

	public Cloth {
		if (variant != null && variant.chapter() != chapter) throw new IllegalArgumentException(variant + " is not a variant of " + chapter.id);
	}

	public static Cloth of(Chapter chapter) {
		return new Cloth(chapter, null);
	}

	/** The cloth a garment stack (ovve, frack, companion top or cuffs) is drawn in; null for anything else. */
	public static @Nullable Cloth of(ItemStack stack) {
		Chapter chapter = Looks.chapterOf(stack);
		return chapter == null ? null : new Cloth(chapter, Variants.of(stack));
	}

	/** {@code <chapter>} or {@code <chapter>.<variant>}: the first segment of every asset and texture name. */
	public String id() {
		return ComboAssets.clothId(chapter.id, variant == null ? null : variant.id());
	}

	/** The cloth with this id, or null (an unknown chapter, or a variant this build has not got). */
	public static @Nullable Cloth byId(String id) {
		int dot = id.indexOf(ComboAssets.VARIANT_SEPARATOR);
		Chapter chapter = Chapter.byId(dot < 0 ? id : id.substring(0, dot));
		if (chapter == null) return null;
		if (dot < 0) return of(chapter);
		Variants.Variant variant = Variants.byId(chapter, id.substring(dot + 1));
		return variant == null ? null : new Cloth(chapter, variant);
	}

	/** Every cloth: each chapter, then its variants. */
	public static List<Cloth> all() {
		List<Cloth> out = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) {
			out.add(of(chapter));
			for (Variants.Variant v : Variants.of(chapter)) out.add(new Cloth(chapter, v));
		}
		return List.copyOf(out);
	}

	@Override
	public String toString() {
		return id();
	}
}
