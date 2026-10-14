package metacraft.kultur.gametest;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import metacraft.kultur.Kultur;
import metacraft.kultur.catalogue.Catalogue;
import metacraft.kultur.content.ModContent;
import metacraft.kultur.content.PatternItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BannerPatternTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * What a server built from the catalogue actually holds: every pattern and painting in its
 * registry and its tag, every gated pattern behind an item whose component points at it alone,
 * and every asset the pack needs where Polymer will pick it up. Run:
 * {@code JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-kultur:runServer}.
 */
public final class KulturGameTests {
	@GameTest
	public void everyPatternIsRegisteredAndTagged(GameTestHelper helper) {
		Registry<BannerPattern> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		List<String> wrong = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Pattern> owned : Kultur.catalogue().patterns()) {
			Catalogue.Pattern p = owned.value();
			Identifier id = Identifier.fromNamespaceAndPath(Kultur.MOD_ID, p.id());
			var holder = registry.get(id);
			if (holder.isEmpty()) { wrong.add(p.id() + ": not in the banner pattern registry"); continue; }
			if (!holder.get().value().assetId().equals(id)) wrong.add(p.id() + ": asset id " + holder.get().value().assetId());
			boolean free = holder.get().is(BannerPatternTags.NO_ITEM_REQUIRED);
			if (free == p.item()) wrong.add(p.id() + ": no_item_required=" + free + " but item=" + p.item());
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void gatedPatternsHaveTheirItem(GameTestHelper helper) {
		Registry<BannerPattern> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		List<String> wrong = new ArrayList<>();
		int gated = 0;
		for (Catalogue.Owned<Catalogue.Pattern> owned : Kultur.catalogue().patterns()) {
			Catalogue.Pattern p = owned.value();
			PatternItem item = ModContent.patternItem(p.id());
			if (!p.item()) {
				if (item != null) wrong.add(p.id() + ": free pattern has an item");
				continue;
			}
			gated++;
			if (item == null) { wrong.add(p.id() + ": no item"); continue; }
			Identifier itemId = Identifier.fromNamespaceAndPath(Kultur.MOD_ID, p.itemPath());
			if (BuiltInRegistries.ITEM.getValue(itemId) != item) wrong.add(p.id() + ": item not registered as " + itemId);
			ItemStack stack = new ItemStack(item);
			if (!stack.is(ItemTags.LOOM_PATTERNS)) wrong.add(p.id() + ": item not in #minecraft:loom_patterns, so the loom refuses it");
			HolderSet<BannerPattern> provides = stack.get(DataComponents.PROVIDES_BANNER_PATTERNS);
			if (provides == null) { wrong.add(p.id() + ": item has no provides_banner_patterns"); continue; }
			List<Identifier> ids = provides.stream().map(h -> h.unwrapKey().map(ResourceKey::identifier).orElse(null)).toList();
			Identifier want = Identifier.fromNamespaceAndPath(Kultur.MOD_ID, p.id());
			if (ids.size() != 1 || !want.equals(ids.getFirst())) wrong.add(p.id() + ": item provides " + ids + ", wanted [" + want + "]");
			TagKey<BannerPattern> tag = TagKey.create(Registries.BANNER_PATTERN, Identifier.fromNamespaceAndPath(Kultur.MOD_ID, "pattern_item/" + p.id()));
			if (registry.get(tag).isEmpty()) wrong.add(p.id() + ": tag " + tag + " missing");
		}
		if (gated == 0) wrong.add("no gated pattern in the catalogue to test");
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void everyPaintingIsRegisteredAndPlaceable(GameTestHelper helper) {
		Registry<PaintingVariant> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
		List<String> wrong = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Painting> owned : Kultur.catalogue().paintings()) {
			Catalogue.Painting q = owned.value();
			Identifier id = Identifier.fromNamespaceAndPath(Kultur.MOD_ID, q.id());
			var holder = registry.get(id);
			if (holder.isEmpty()) { wrong.add(q.id() + ": not in the painting variant registry"); continue; }
			PaintingVariant v = holder.get().value();
			if (v.width() != q.width() || v.height() != q.height()) wrong.add(q.id() + ": " + v.width() + "×" + v.height() + " in the registry, " + q.width() + "×" + q.height() + " in the catalogue");
			if (v.title().isEmpty() || v.author().isEmpty()) wrong.add(q.id() + ": title or author missing");
			if (!holder.get().is(PaintingVariantTags.PLACEABLE)) wrong.add(q.id() + ": not placeable");
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/** Polymer copies the mod's assets wholesale, so an asset on the classpath is an asset in the pack. */
	@GameTest
	public void everyAssetIsInTheJar(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Pattern> owned : Kultur.catalogue().patterns()) {
			Catalogue.Pattern p = owned.value();
			need(missing, "textures/entity/banner/" + p.id() + ".png");
			need(missing, "textures/entity/shield/" + p.id() + ".png");
			if (p.item()) {
				need(missing, "textures/item/" + p.itemPath() + ".png");
				need(missing, "models/item/" + p.itemPath() + ".json");
				need(missing, "items/" + p.itemPath() + ".json");
			}
		}
		for (Catalogue.Owned<Catalogue.Painting> owned : Kultur.catalogue().paintings()) {
			need(missing, "textures/painting/" + owned.value().id() + ".png");
		}
		need(missing, "lang/en_us.json");
		if (!missing.isEmpty()) helper.fail("missing from assets/kultur: " + missing);
		if (!PolymerResourcePackUtils.hasResources()) helper.fail("Polymer has no mod assets registered");
		if (!PolymerResourcePackUtils.isRequired()) helper.fail("the pack is not marked required");
		helper.succeed();
	}

	private static void need(List<String> missing, String path) {
		try (InputStream in = Kultur.class.getResourceAsStream("/assets/" + Kultur.MOD_ID + "/" + path)) {
			if (in == null) missing.add(path);
		} catch (java.io.IOException e) {
			missing.add(path + " (" + e + ")");
		}
	}
}
