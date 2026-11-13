package se.metacraft.ovvar.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import de.tomalbrc.danse.util.MinecraftSkinParser.BodyPart;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import se.metacraft.ovvar.combo.ComboAssets;
import se.metacraft.ovvar.compat.danse.DanseLayers;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Cloth;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.content.Variants;
import se.metacraft.ovvar.content.item.OvveItem;
import se.metacraft.ovvar.datagen.GeneratedAssets;
import se.metacraft.ovvar.pack.ComboPack;
import se.metacraft.ovvar.pack.Combos;
import se.metacraft.ovvar.pack.EquipmentJson;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

/**
 * Named variants: an ovve or frack whose custom name is exactly a variant's name wears that variant's
 * cloth ({@link Cloth}), worn and as its inventory icon, with patches, combos and the instant channel on
 * top as on the chapter's own. The tests use the first variant of the catalogue and its own name, so a
 * renamed or replaced variant keeps them meaningful.
 */
public final class VariantTests {

	private static Variants.Variant variant(GameTestHelper helper) {
		List<Variants.Variant> all = Variants.all();
		if (all.isEmpty()) throw helper.assertionException(Component.literal("no variants in " + Variants.RESOURCE));
		return all.getFirst();
	}

	private static ItemStack named(Chapter chapter, String name) {
		ItemStack ovve = new ItemStack(ModContent.ovve(chapter));
		ovve.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return ovve;
	}

	private static Cloth cloth(Variants.Variant v) {
		return new Cloth(v.chapter(), v);
	}

	private static Placement place(Spot spot, String patch) {
		return new Placement(spot, Patches.get(patch));
	}

	private static void check(GameTestHelper helper, boolean ok, String message) {
		if (!ok) throw helper.assertionException(Component.literal(message));
	}

	private static ResourceKey<EquipmentAsset> clientAsset(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
		ItemStack client = PolymerItemUtils.getPolymerItemStack(stack, player.connection.getPacketContext(), helper.getLevel().registryAccess());
		Equippable equippable = client.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.assetId().isEmpty()) throw helper.assertionException(Component.literal("the client stack wears nothing"));
		return equippable.assetId().get();
	}

	// ---- the data

	@GameTest
	public void theCatalogueReadsAndEveryVariantHasArt(GameTestHelper helper) {
		for (Variants.Variant v : Variants.all()) {
			check(helper, v.id().matches("[a-z0-9_]+"), v + ": a texture id is [a-z0-9_]");
			check(helper, Cloth.byId(cloth(v).id()) != null && cloth(v).equals(Cloth.byId(cloth(v).id())), cloth(v).id() + " does not read back");
			check(helper, cloth(v).id().equals(v.chapter().id + "." + v.id()), "the cloth id is " + cloth(v).id());
			for (GeneratedAssets.BaseLayer base : GeneratedAssets.baseLayers(cloth(v))) {
				String path = "/assets/ovvar/textures/entity/equipment/" + base.piece().layer + "/" + cloth(v).id() + "/" + base.name() + ".png";
				check(helper, Variants.class.getResource(path) != null, "no generated layer " + path);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void aBadCatalogueFailsLoudly(GameTestHelper helper) {
		for (String bad : List.of(
				"{\"nosuchchapter\": [{\"name\": \"x\", \"texture\": \"x\"}]}",
				"{\"it\": [{\"name\": \"x\", \"texture\": \"Bad-Id\"}]}",
				"{\"it\": [{\"name\": \"x\", \"texture\": \"a\"}, {\"name\": \"x\", \"texture\": \"b\"}]}",
				"{\"it\": [{\"name\": \"x\", \"texture\": \"a\"}, {\"name\": \"y\", \"texture\": \"a\"}]}",
				"{\"it\": [{\"name\": \"\", \"texture\": \"a\"}]}",
				"{\"it\": [{\"texture\": \"a\"}]}")) {
			boolean threw = false;
			try {
				Variants.parse(bad);
			} catch (IllegalStateException e) {
				threw = true;
			}
			check(helper, threw, "read without complaint: " + bad);
		}
		// One name on two chapters is two variants, each of its own chapter.
		check(helper, Variants.parse("{\"it\": [{\"name\": \"x\", \"texture\": \"a\"}], \"data\": [{\"name\": \"x\", \"texture\": \"a\"}]}").size() == 2,
				"the same name on two chapters");
		helper.succeed();
	}

	/** kultur's ArtFiles rule: {@code _<id>.png} is AI-drawn and used, a plain {@code <id>.png} replaces it. */
	@GameTest
	public void aPlainFileWinsOverTheAiDrawnOne(GameTestHelper helper) {
		check(helper, "spaceships.png".equals(Variants.artFile("spaceships", Set.of("spaceships.png", "_spaceships.png")::contains)), "plain over _");
		check(helper, "_spaceships.png".equals(Variants.artFile("spaceships", Set.of("_spaceships.png")::contains)), "_ alone");
		check(helper, Variants.artFile("spaceships", Set.of()::contains) == null, "neither");
		helper.succeed();
	}

	// ---- matching

	@GameTest
	public void anExactNameWearsTheVariant(GameTestHelper helper) {
		Variants.Variant v = variant(helper);
		ItemStack ovve = named(v.chapter(), v.name());
		check(helper, cloth(v).equals(Cloth.of(ovve)), "named " + v.name() + ": " + Cloth.of(ovve));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Piece own = v.chapter().ownPiece();
		boolean nercabbad = own == Piece.BOTTOM && v.chapter().rollable;   // a new ovve's top is down
		check(helper, clientAsset(helper, player, ovve).equals(Looks.asset(cloth(v), own, nercabbad, "")),
				"the client wears " + clientAsset(helper, player, ovve));
		helper.succeed();
	}

	@GameTest
	public void aNearMissWearsTheChapter(GameTestHelper helper) {
		Variants.Variant v = variant(helper);
		String name = v.name();
		List<Component> misses = List.of(
				Component.literal(name.toLowerCase()), Component.literal(name.toUpperCase()),
				Component.literal(" " + name), Component.literal(name + " "), Component.literal(name.replace(" ", "  ")),
				Component.literal(name).withStyle(ChatFormatting.RED), Component.literal("").append(name));
		for (Component miss : misses) {
			if (miss.getString().equals(name) && miss.getStyle().isEmpty() && miss.getSiblings().isEmpty()) continue;
			ItemStack ovve = new ItemStack(ModContent.ovve(v.chapter()));
			ovve.set(DataComponents.CUSTOM_NAME, miss);
			check(helper, Cloth.of(ovve).equals(Cloth.of(v.chapter())), "'" + miss.getString() + "' (" + miss + ") wears " + Cloth.of(ovve));
		}
		// the name belongs to its chapter: on another chapter's ovve it is just a name
		for (Chapter other : Chapter.values()) {
			if (other == v.chapter() || Variants.of(other).stream().anyMatch(o -> o.name().equals(name))) continue;
			check(helper, Cloth.of(named(other, name)).equals(Cloth.of(other)), other.id + " named " + name + " wears a variant");
		}
		helper.succeed();
	}

	@GameTest
	public void aMobsGarmentNeverWearsAVariant(GameTestHelper helper) {
		Variants.Variant v = variant(helper);
		ItemStack ovve = named(v.chapter(), v.name());
		ovve.set(ModComponents.MOB_OVVE, Unit.INSTANCE);
		check(helper, Cloth.of(ovve).equals(Cloth.of(v.chapter())), "a mob's garment wears " + Cloth.of(ovve));
		helper.succeed();
	}

	/** Renamed in an anvil it is the variant; the name taken off again in an anvil, the chapter's. */
	@GameTest
	public void renamingAndUnnamingInAnAnvil(GameTestHelper helper) {
		Variants.Variant v = variant(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.experienceLevel = 30;
		ItemStack renamed = anvil(helper, player, new ItemStack(ModContent.ovve(v.chapter())), v.name());
		check(helper, cloth(v).equals(Cloth.of(renamed)), "renamed in an anvil: " + Cloth.of(renamed) + ", name " + renamed.get(DataComponents.CUSTOM_NAME));
		ItemStack unnamed = anvil(helper, player, renamed, "");
		check(helper, !unnamed.isEmpty() && !unnamed.has(DataComponents.CUSTOM_NAME), "the anvil did not take the name off");
		check(helper, Cloth.of(unnamed).equals(Cloth.of(v.chapter())), "unnamed: " + Cloth.of(unnamed));
		helper.succeed();
	}

	private static ItemStack anvil(GameTestHelper helper, ServerPlayer player, ItemStack in, String name) {
		AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(in.copy());
		menu.setItemName(name);
		return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
	}

	/** The companion top follows the ovve's name each tick, both ways, and the client wears it. */
	@GameTest
	public void theCompanionTopFollowsTheName(GameTestHelper helper) {
		Variants.Variant v = variant(helper);
		if (!v.chapter().rollable) { helper.succeed(); return; }
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
		ItemStack ovve = named(v.chapter(), v.name());
		OvveItem.setTopUp(ovve, true);
		stand.setItemSlot(EquipmentSlot.LEGS, ovve);
		tick(helper, stand);
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		ItemStack top = stand.getItemBySlot(EquipmentSlot.CHEST);
		check(helper, top.getItem() == ModContent.top(v.chapter()), "no companion top: " + top);
		check(helper, cloth(v).equals(Cloth.of(top)), "the top wears " + Cloth.of(top));
		check(helper, clientAsset(helper, viewer, top).equals(Looks.asset(cloth(v), Piece.TOP, false, "")), "the top's client asset is " + clientAsset(helper, viewer, top));
		stand.getItemBySlot(EquipmentSlot.LEGS).remove(DataComponents.CUSTOM_NAME);
		tick(helper, stand);
		ItemStack after = stand.getItemBySlot(EquipmentSlot.CHEST);
		check(helper, Cloth.of(after).equals(Cloth.of(v.chapter())), "after the name came off the top wears " + Cloth.of(after));
		check(helper, clientAsset(helper, viewer, after).equals(Looks.asset(Cloth.of(v.chapter()), Piece.TOP, false, "")), "and the client's " + clientAsset(helper, viewer, after));
		helper.succeed();
	}

	private static void tick(GameTestHelper helper, ArmorStand stand) {
		ItemStack legs = stand.getItemBySlot(EquipmentSlot.LEGS);
		legs.inventoryTick(helper.getLevel(), stand, EquipmentSlot.LEGS);
	}

	// ---- the inventory icon

	/**
	 * The icon is the vanilla client's own doing: the chapter's item definition selects on
	 * {@code custom_name}, and the case is the very component the anvil writes, which Polymer passes on.
	 */
	@GameTest
	public void theIconIsASelectOnTheCustomName(GameTestHelper helper) {
		Variants.Variant v = variant(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack ovve = named(v.chapter(), v.name());
		ItemStack client = PolymerItemUtils.getPolymerItemStack(ovve, player.connection.getPacketContext(), helper.getLevel().registryAccess());
		Identifier model = client.get(DataComponents.ITEM_MODEL);
		check(helper, ModContent.ovveId(v.chapter()).equals(model), "the client's item model is " + model);
		check(helper, Component.literal(v.name()).equals(client.get(DataComponents.CUSTOM_NAME)), "the client's name is " + client.get(DataComponents.CUSTOM_NAME));

		JsonObject root = json(helper, "/assets/" + model.getNamespace() + "/items/" + model.getPath() + ".json").getAsJsonObject().getAsJsonObject("model");
		check(helper, "minecraft:select".equals(root.get("type").getAsString()), "the item definition is a " + root.get("type"));
		check(helper, "minecraft:component".equals(root.get("property").getAsString()), "it selects on " + root.get("property"));
		check(helper, "minecraft:custom_name".equals(root.get("component").getAsString()), "it selects on the component " + root.get("component"));
		String variantModel = null;
		for (JsonElement c : root.getAsJsonArray("cases")) {
			Component when = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, c.getAsJsonObject().get("when")).getOrThrow();
			if (when.equals(client.get(DataComponents.CUSTOM_NAME))) variantModel = c.getAsJsonObject().getAsJsonObject("model").get("model").getAsString();
		}
		check(helper, Variants.iconModel(v).toString().equals(variantModel), "no case for the variant's name picks " + Variants.iconModel(v) + ": " + variantModel);
		check(helper, root.getAsJsonObject("fallback").get("model").getAsString().equals(model.getNamespace() + ":item/" + model.getPath()), "the fallback is not the chapter's icon");
		Identifier icon = Variants.iconModel(v);
		check(helper, Variants.class.getResource("/assets/" + icon.getNamespace() + "/models/" + icon.getPath() + ".json") != null, "no model " + icon);
		check(helper, Variants.class.getResource("/assets/" + icon.getNamespace() + "/textures/" + icon.getPath() + ".png") != null, "no texture " + icon);
		helper.succeed();
	}

	// ---- patches and combos

	@GameTest
	public void patchesStayOnAVariant(GameTestHelper helper) {
		Variants.Variant v = variant(helper);
		Cloth cloth = cloth(v);
		Piece piece = Piece.TOP;
		Placement instant = place(Spot.FRONT_TOP_LEFT, "spiken"), given = place(Spot.FRONT_TOP_RIGHT, "metacrew");
		ItemStack ovve = named(v.chapter(), v.name());
		OvveItem.setTopUp(ovve, true);
		Looks.setSewn(ovve, SpotPlacements.fromList(List.of(instant)).getOrThrow());
		ServerPlayer vanilla = helper.makeMockServerPlayerInLevel(), modded = helper.makeMockServerPlayerInLevel();
		Combos.setDrawsCombosForTest(modded.getUUID(), true);
		Combos.Combo one = Placement.combo(List.of(instant)), two = Placement.combo(List.of(instant, given));
		try {
			// a vanilla viewer: the variant's empty cloth, the loot patch in the dye colour
			Looks.Look look = Looks.look(ovve, piece, vanilla.getUUID());
			check(helper, look.combo().isEmpty() && look.dye() == Looks.instantDye(piece, List.of(instant)), "a vanilla viewer sees " + look);
			check(helper, Combos.isKnown(cloth, piece, one), "the variant's combination was not queued for the pack");
			// ovvar-client: the whole combination on the variant's cloth
			Looks.setSewn(ovve, SpotPlacements.fromList(List.of(instant, given)).getOrThrow());
			Looks.Look full = Looks.look(ovve, piece, modded.getUUID());
			check(helper, full.combo().equals(two) && full.dye() == 0, "ovvar-client sees " + full);
			ResourceKey<EquipmentAsset> asset = Looks.asset(cloth, piece, false, two.key());
			check(helper, asset.identifier().getPath().startsWith(cloth.id() + "/top/"), "the asset is " + asset);
			// the pack and the client build the same definition, the variant's cloth under the patches
			Map<String, String> files = unzip(ComboPack.build(Set.of(new Combos.KeyedCombo(cloth, piece, two))).zip());
			String path = "assets/ovvar/equipment/" + asset.identifier().getPath() + ".json";
			check(helper, files.containsKey(path), "the pack lacks " + path + ": " + files.keySet());
			check(helper, ComboAssets.jsonFor(asset.identifier().getPath()).orElse("").equals(files.get(path)), "the client builds " + path + " differently");
			JsonArray layers = JsonParser.parseString(files.get(path)).getAsJsonObject().getAsJsonObject("layers").getAsJsonArray(piece.layer);
			check(helper, layers.get(0).getAsJsonObject().get("texture").getAsString().equals(EquipmentJson.baseTexture(cloth, piece, false)), "the bottom layer is " + layers.get(0));
			check(helper, layers.size() == 4, "cloth, two patches and the preview: " + layers);
		} finally {
			Combos.setDrawsCombosForTest(modded.getUUID(), false);
			Combos.forgetForTest(cloth, piece, one);
			Combos.forgetForTest(cloth, piece, two);
		}
		helper.succeed();
	}

	@GameTest
	public void variantCombosAreKeyedByTheirCloth(GameTestHelper helper) {
		Variants.Variant v = variant(helper);
		Cloth cloth = cloth(v);
		Piece piece = v.chapter().ownPiece();
		Combos.KeyedCombo key = new Combos.KeyedCombo(cloth, piece, Placement.combo(List.of(place(piece == Piece.TOP ? Spot.FRONT_TOP_LEFT : Spot.LEG_FRONT_TOP_R, "itk"))));
		check(helper, key.key().startsWith(cloth.id() + "/"), "the key is " + key.key());
		Combos.KeyedCombo read = Combos.KeyedCombo.KEY_CODEC.parse(JavaOps.INSTANCE, key.key()).getOrThrow();
		check(helper, read.equals(key), "read back as " + read.key());
		// a line naming a variant this build has not got is dropped, like an unknown chapter
		check(helper, Combos.parseKeys(List.of(v.chapter().id + ".no_such_variant/" + piece.id + ":" + key.combo().key())).isEmpty(), "an unknown variant's line was kept");
		// every rolled-down state of the chapter is built for the variant too
		Map<String, String> files = unzip(ComboPack.build(Set.of(key)).zip());
		files.remove("pack.mcmeta");
		check(helper, files.size() == EquipmentJson.variants(v.chapter(), piece).size(), "files for " + key.key() + ": " + files.keySet());
		helper.succeed();
	}

	/**
	 * The startup pre-build stays per chapter: wardrobes keep designs per chapter and know no names, and
	 * building every design once more per variant would multiply the pack for an easter egg. A variant's
	 * combinations are built when they are first seen, and combos.json remembers them from then on.
	 */
	@GameTest
	public void thePrebuildAddsNoVariantCombinations(GameTestHelper helper) {
		Variants.Variant v = variant(helper);
		Piece piece = v.chapter().ownPiece();
		Placement p = place(piece == Piece.TOP ? Spot.SLEEVE_BACK_MID_L : Spot.LEG_BACK_MID_L, "metacraft");
		SpotPlacements design = SpotPlacements.fromList(List.of(p)).getOrThrow();
		Combos.Combo combo = Placement.combo(List.of(p));
		boolean known = Combos.isKnown(Cloth.of(v.chapter()), piece, combo);
		try {
			Combos.prebuild(List.of(Map.entry(v.chapter(), design)));
			check(helper, Combos.isKnown(Cloth.of(v.chapter()), piece, combo), "the chapter's combination was not pre-built");
			check(helper, !Combos.isKnown(cloth(v), piece, combo), "the variant's was pre-built too");
		} finally {
			if (!known) Combos.forgetForTest(Cloth.of(v.chapter()), piece, combo);
		}
		helper.succeed();
	}

	// ---- the chest wrap and Danse

	@GameTest
	public void aChestplateOverAVariantCarriesTheVariantsTop(GameTestHelper helper) {
		Variants.Variant v = variant(helper);
		if (!v.chapter().rollable) { helper.succeed(); return; }
		Cloth cloth = cloth(v);
		ResourceKey<EquipmentAsset> asset = EquipmentJson.chestAsset(cloth, "iron");
		check(helper, asset.identifier().getPath().equals("chest/" + cloth.id() + "/iron"), "the wrap's asset is " + asset);
		JsonObject wrap = json(helper, "/assets/ovvar/equipment/" + asset.identifier().getPath() + ".json").getAsJsonObject();
		String under = wrap.getAsJsonObject("layers").getAsJsonArray(Piece.TOP.layer).get(0).getAsJsonObject().get("texture").getAsString();
		check(helper, under.equals(EquipmentJson.baseTexture(cloth, Piece.TOP, false)), "the wrap's cloth is " + under);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack ovve = named(v.chapter(), v.name());
		OvveItem.setTopUp(ovve, true);
		player.setItemSlot(EquipmentSlot.LEGS, ovve);
		ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
		chestplate.set(ModComponents.WRAPPED_TOP, player.getUUID());
		player.setItemSlot(EquipmentSlot.CHEST, chestplate);
		// The wrap finds the wearer through the server, which a mock player's connection does not carry.
		PacketContext own = player.connection.getPacketContext();
		PacketContext context = new PacketContext() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> T get(PacketContext.ReadKey<T> key) {
				return key == PacketContext.SERVER_INSTANCE ? (T) helper.getLevel().getServer() : own.get(key);
			}

			@Override
			public <T> void set(PacketContext.Key<T> key, T value) {
				own.set(key, value);
			}
		};
		ItemStack client = PolymerItemUtils.getPolymerItemStack(player.getItemBySlot(EquipmentSlot.CHEST), context, helper.getLevel().registryAccess());
		Equippable worn = client.get(DataComponents.EQUIPPABLE);
		check(helper, worn != null && worn.assetId().isPresent() && worn.assetId().get().equals(asset), "the client's chestplate wears " + (worn == null ? null : worn.assetId()));
		helper.succeed();
	}

	@GameTest
	public void aGestureDrawsTheVariantsCloth(GameTestHelper helper) {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("danse")) { helper.succeed(); return; }
		Variants.Variant v = variant(helper);
		if (!v.chapter().rollable) { helper.succeed(); return; }
		EntityEquipment eq = new EntityEquipment();
		ItemStack ovve = named(v.chapter(), v.name());
		eq.set(EquipmentSlot.LEGS, ovve);
		CustomModelData data = DanseLayers.INSTANCE.layer(eq, BodyPart.RIGHT_LEG, true).get(DataComponents.CUSTOM_MODEL_DATA);
		String want = cloth(v).id() + "/bottom_nercabbad";
		check(helper, data != null && want.equals(data.strings().getFirst()), "the stand-in's legs are " + (data == null ? null : data.strings().getFirst()) + ", not " + want);
		helper.succeed();
	}

	// ---- helpers

	private static JsonElement json(GameTestHelper helper, String resource) {
		try (InputStream in = Variants.class.getResourceAsStream(resource)) {
			if (in == null) throw helper.assertionException(Component.literal("missing " + resource));
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Map<String, String> unzip(byte[] zip) {
		Map<String, String> out = new HashMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				out.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out;
	}
}
