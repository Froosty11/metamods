package metacraft.kultur.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackCreator;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

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
			Identifier id = Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, p.id());
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
			Identifier itemId = Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, p.itemPath());
			if (BuiltInRegistries.ITEM.getValue(itemId) != item) wrong.add(p.id() + ": item not registered as " + itemId);
			ItemStack stack = new ItemStack(item);
			if (!stack.is(ItemTags.LOOM_PATTERNS)) wrong.add(p.id() + ": item not in #minecraft:loom_patterns, so the loom refuses it");
			HolderSet<BannerPattern> provides = stack.get(DataComponents.PROVIDES_BANNER_PATTERNS);
			if (provides == null) { wrong.add(p.id() + ": item has no provides_banner_patterns"); continue; }
			List<Identifier> ids = provides.stream().map(h -> h.unwrapKey().map(ResourceKey::identifier).orElse(null)).toList();
			Identifier want = Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, p.id());
			if (ids.size() != 1 || !want.equals(ids.getFirst())) wrong.add(p.id() + ": item provides " + ids + ", wanted [" + want + "]");
			TagKey<BannerPattern> tag = TagKey.create(Registries.BANNER_PATTERN, Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, "pattern_item/" + p.id()));
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
			Identifier id = Identifier.fromNamespaceAndPath(Kultur.NAMESPACE, q.id());
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

	/**
	 * The pack shows Luck as Inebriated (Berusad), with PolymITer's icon, and Kultur's
	 * {@code assets/minecraft/lang} files add their keys to another mod's files of the same name rather
	 * than replace them: Polymer merges every {@code assets/<namespace>/lang/*.json} key by key, whichever
	 * mod comes first. Built in-process, with a stand-in for another mod's {@code en_us.json} added before
	 * Kultur's assets and one after.
	 */
	@GameTest(maxTicks = 400)
	public void packRenamesLuck(GameTestHelper helper) {
		ResourcePackCreator creator = ResourcePackCreator.create();
		creator.addAssetSource(Kultur.MOD_ID);
		creator.creationEvent.register(builder -> builder.addData("assets/minecraft/lang/en_us.json",
				"{\"kultur.test.before\": \"another mod's key, added first\"}".getBytes(StandardCharsets.UTF_8)));
		creator.afterInitialCreationEvent.register(builder -> builder.addData("assets/minecraft/lang/en_us.json",
				"{\"kultur.test.after\": \"another mod's key, added last\"}".getBytes(StandardCharsets.UTF_8)));
		try {
			Path zip = Files.createTempFile("kultur-pack", ".zip");
			creator.build(zip);
			try (ZipFile pack = new ZipFile(zip.toFile())) {
				byte[] icon = read(pack, "assets/minecraft/textures/mob_effect/luck.png");
				byte[] ours;
				try (InputStream in = Kultur.class.getResourceAsStream("/assets/minecraft/textures/mob_effect/luck.png")) {
					ours = in == null ? null : in.readAllBytes();
				}
				helper.assertTrue(ours != null && Arrays.equals(icon, ours), "the pack's luck.png is not PolymITer's icon");
				JsonObject english = JsonParser.parseString(new String(read(pack, "assets/minecraft/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
				helper.assertValueEqual(string(english, "effect.minecraft.luck"), "Inebriated", "effect.minecraft.luck in en_us");
				helper.assertValueEqual(string(english, "potion.potency.6"), "VII", "potion.potency.6 in en_us");
				helper.assertTrue(english.has("kultur.test.before") && english.has("kultur.test.after"), "another mod's en_us keys did not survive: " + english.keySet());
				JsonObject swedish = JsonParser.parseString(new String(read(pack, "assets/minecraft/lang/sv_se.json"), StandardCharsets.UTF_8)).getAsJsonObject();
				helper.assertValueEqual(string(swedish, "effect.minecraft.luck"), "Berusad", "effect.minecraft.luck in sv_se");
				helper.assertValueEqual(string(swedish, "potion.potency.6"), "VII", "potion.potency.6 in sv_se");
			}
			Files.deleteIfExists(zip);
		} catch (java.io.IOException | java.util.concurrent.ExecutionException | InterruptedException e) {
			helper.fail("building the pack failed: " + e);
		}
		helper.succeed();
	}

	private static byte[] read(ZipFile zip, String path) throws java.io.IOException {
		var entry = zip.getEntry(path);
		if (entry == null) throw new java.io.IOException(path + " is not in the pack");
		try (InputStream in = zip.getInputStream(entry)) {
			return in.readAllBytes();
		}
	}

	private static String string(JsonObject json, String key) {
		return json.has(key) ? json.get(key).getAsString() : null;
	}

	private static void need(List<String> missing, String path) {
		try (InputStream in = Kultur.class.getResourceAsStream("/assets/" + Kultur.NAMESPACE + "/" + path)) {
			if (in == null) missing.add(path);
		} catch (java.io.IOException e) {
			missing.add(path + " (" + e + ")");
		}
	}
}
