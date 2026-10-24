package se.metacraft.ovvar.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.PatchLoot;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.item.PatchItem;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The chapters' patches are crafted from a dye and string; the rest turn up in structure chests. */
public final class PatchAcquiringTests {
	@GameTest
	public void itkIsLimeDyeInString(GameTestHelper helper) {
		helper.assertValueEqual(craft(helper, plus(Items.DYE.pick(DyeColor.LIME))).getItem(), patch("itk"), "lime dye in a cross of string");
		helper.succeed();
	}

	/** IT and Data take More Dyes' Laserviolet and Cerise, so their recipes load only beside it. */
	@GameTest
	public void itAndDataNeedMoreDyes(GameTestHelper helper) {
		boolean moreDyes = FabricLoader.getInstance().isModLoaded("moredyes");
		for (String id : List.of("it", "data")) {
			var key = ResourceKey.create(Registries.RECIPE, ModContent.patchId(Patches.get(id)));
			helper.assertValueEqual(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), moreDyes, id + "'s recipe loaded");
		}
		if (moreDyes) {
			helper.assertValueEqual(craft(helper, plus(dye("laserviolet"))).getItem(), patch("it"), "laserviolet dye in a cross of string");
			helper.assertValueEqual(craft(helper, plus(dye("cerise"))).getItem(), patch("data"), "cerise dye in a cross of string");
		}
		helper.succeed();
	}

	@GameTest
	public void inIsItAndABottleOfExperience(GameTestHelper helper) {
		ItemStack[] grid = empty();
		grid[0] = new ItemStack(patch("it"));
		grid[4] = new ItemStack(Items.EXPERIENCE_BOTTLE);
		helper.assertValueEqual(craft(helper, grid).getItem(), patch("in"), "IT and a bottle of experience");
		helper.succeed();
	}

	@GameTest
	public void everyCraftedAndExclusivePatchIsInTheCatalogue(GameTestHelper helper) {
		for (String id : PatchLoot.CRAFTED) helper.assertTrue(Patches.exists(id), "no patch '" + id + "'");
		for (String id : PatchLoot.EXCLUSIVE) helper.assertTrue(Patches.exists(id), "no patch '" + id + "'");
		helper.succeed();
	}

	/** A dungeon chest turns up the uncraftable patches now and then, and never a crafted or an event's one. */
	@GameTest
	public void structureChestsHoldTheOtherPatches(GameTestHelper helper) {
		LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
		LootParams params = new LootParams.Builder(helper.getLevel())
				.withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1)))
				.create(LootContextParamSets.CHEST);
		Set<String> found = new HashSet<>();
		for (int i = 0; i < 4000; i++) {
			table.getRandomItems(params).forEach(stack -> {
				if (stack.getItem() instanceof PatchItem item) found.add(item.patch.id());
			});
		}
		helper.assertTrue(found.size() > 1, "4000 dungeon chests held only these patches: " + found);
		for (String id : PatchLoot.CRAFTED) helper.assertFalse(found.contains(id), "a dungeon chest held the craftable " + id);
		for (String id : PatchLoot.EXCLUSIVE) helper.assertFalse(found.contains(id), "a dungeon chest held the event's " + id);
		helper.assertFalse(PatchLoot.looted().stream().anyMatch(patch -> patch.id().equals("rivals")), "Rivals '26 is loot");
		helper.succeed();
	}

	@GameTest
	public void onlyStructureChestsGetPatches(GameTestHelper helper) {
		helper.assertTrue(PatchLoot.inStructureChest(BuiltInLootTables.SIMPLE_DUNGEON.identifier()), "a dungeon chest");
		helper.assertTrue(PatchLoot.inStructureChest(Identifier.fromNamespaceAndPath("structory", "outcast/bandit/desert")), "a Structory chest");
		helper.assertFalse(PatchLoot.inStructureChest(BuiltInLootTables.SPAWN_BONUS_CHEST.identifier()), "the bonus chest");
		helper.assertFalse(PatchLoot.inStructureChest(Identifier.withDefaultNamespace("entities/zombie")), "a zombie");
		helper.succeed();
	}

	private static Item patch(String id) {
		return ModContent.patchItem(Patches.get(id));
	}

	private static Item dye(String colour) {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("moredyes", colour + "_dye"));
	}

	/** {@code dye} in the middle, string on its four sides. */
	private static ItemStack[] plus(Item dye) {
		ItemStack[] grid = empty();
		for (int i : new int[] {1, 3, 5, 7}) grid[i] = new ItemStack(Items.STRING);
		grid[4] = new ItemStack(dye);
		return grid;
	}

	private static ItemStack[] empty() {
		ItemStack[] grid = new ItemStack[9];
		Arrays.fill(grid, ItemStack.EMPTY);
		return grid;
	}

	private static ItemStack craft(GameTestHelper helper, ItemStack[] grid) {
		CraftingInput input = CraftingInput.of(3, 3, List.of(grid));
		return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}
}
