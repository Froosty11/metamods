package metacraft.moredyes.gametest;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.blocks.api.BlockModelType;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.banner.BannerPatterns;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import metacraft.moredyes.beacon.BeaconBeams;
import metacraft.moredyes.beacon.BeaconBeamHolder;
import metacraft.moredyes.beacon.BeamWalk;
import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.color.ModColors;
import metacraft.moredyes.content.ClientStates;
import metacraft.moredyes.content.Family;
import metacraft.moredyes.content.ModContent;
import metacraft.moredyes.sheep.SheepColors;
import metacraft.moredyes.sheep.SheepOverlay;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side game tests (Fabric GameTest API, vanilla test framework underneath).
 *
 * <p>Run headless: {@code ./gradlew runGametest} — starts a server, runs every test in an empty
 * 8×8×8 structure, writes {@code build/test-results/gametest.xml}, and fails the build on any
 * failure. Or in a normal world as an op: {@code /test runall} — the lectern/beacon feedback is
 * vanilla's test framework.
 *
 * <p>What these can and can't cover: everything a vanilla client would be sent (client states,
 * registry contents, tracked-data overrides), drops, and interactions. Not pixels: whether a
 * model looks right is still a visual check with a vanilla client.
 */
public final class MoreDyesGameTests {
	private static final BlockPos FLOOR = new BlockPos(0, 0, 0);

	private static ModColor first() {
		return ModColors.all().getFirst();
	}

	private static Block block(Family family) {
		return ModContent.block(first(), family);
	}

	/** GameTestHelper.destroyBlock breaks without drops; we want the loot. */
	private static void breakWithDrops(GameTestHelper helper, BlockPos pos) {
		helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** Every server state of every block resolves to a real client state, never the error block. */
	@GameTest
	public void clientStatesResolve(GameTestHelper helper) {
		int states = 0;
		for (ModColor color : ModColors.all()) {
			for (Family family : Family.values()) {
				Block block = ModContent.block(color, family);
				helper.assertTrue(block instanceof PolymerBlock, family.id + " is not a Polymer block");
				for (BlockState state : block.getStateDefinition().getPossibleStates()) {
					BlockState client = ((PolymerBlock) block).getPolymerBlockState(state, null);
					helper.assertTrue(client != null && client != ClientStates.ERROR, "no client state for " + state);
					if (family == Family.BED) {
						helper.assertTrue(client.getBlock() instanceof BedBlock, "bed client state is not a bed: " + client);
					}
					states++;
				}
			}
		}
		MoreDyes.LOGGER.info("[gametest] {} client states resolved", states);
		helper.succeed();
	}

	/** Double slabs drop two, and a bed drops once (from the head). */
	@GameTest
	public void slabAndBedDrops(GameTestHelper helper) {
		floor(helper);
		BlockPos slab = new BlockPos(1, 1, 1);
		helper.setBlock(slab, block(Family.WOOL_SLAB).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
		breakWithDrops(helper, slab);

		BlockPos foot = new BlockPos(4, 1, 1), head = new BlockPos(4, 1, 2);
		BlockState bed = block(Family.BED).defaultBlockState();
		helper.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
		breakWithDrops(helper, head); // the head half carries the loot; the foot follows via updateShape

		helper.succeedWhen(() -> {
			helper.assertItemEntityCountIs(block(Family.WOOL_SLAB).asItem(), slab, 2.0, 2);
			helper.assertItemEntityCountIs(block(Family.BED).asItem(), head, 3.0, 1);
		});
	}

	/** A shulker box in our colour keeps its contents when broken (vanilla block entity + loot). */
	@GameTest
	public void shulkerKeepsContents(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, block(Family.SHULKER_BOX));
		ShulkerBoxBlockEntity box = helper.getBlockEntity(pos, ShulkerBoxBlockEntity.class);
		box.setItem(0, new ItemStack(Items.DIAMOND, 3));
		breakWithDrops(helper, pos);
		helper.succeedWhen(() -> {
			List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, pos, 2.0);
			helper.assertTrue(!drops.isEmpty(), "no drop");
			ItemStack stack = drops.getFirst().getItem();
			helper.assertTrue(stack.is(block(Family.SHULKER_BOX).asItem()), "wrong drop " + stack);
			var container = stack.get(DataComponents.CONTAINER);
			helper.assertTrue(container != null && container.nonEmptyItems().iterator().hasNext(), "contents lost");
		});
	}

	/** Concrete powder falls into water and hardens into OUR concrete. */
	@GameTest(maxTicks = 200)
	public void concretePowderHardens(GameTestHelper helper) {
		floor(helper);
		BlockPos water = new BlockPos(3, 1, 3);
		helper.setBlock(water, Blocks.WATER);
		helper.setBlock(water.above(3), block(Family.CONCRETE_POWDER));
		helper.succeedWhenBlockPresent(block(Family.CONCRETE), water);
	}

	/** Our candles can be lit (block tag) and are not cake-able (item tag deliberately absent). */
	@GameTest
	public void candleTags(GameTestHelper helper) {
		BlockState unlit = block(Family.CANDLE).defaultBlockState();
		helper.assertTrue(CandleBlock.canLight(unlit), "candle not lightable: missing #minecraft:candles block tag");
		helper.assertTrue(new ItemStack(block(Family.CANDLE)).is(ItemTags.CANDLES), "candle missing from #minecraft:candles item tag");
		helper.assertTrue(CandleCakeBlock.canLight(block(Family.CANDLE_CAKE).defaultBlockState()),
				"candle cake not lightable: missing #minecraft:candle_cakes block tag");
		helper.succeed();
	}

	/** Dye a sheep: colour attached, vanilla colour white, shearing drops our wool. */
	@GameTest(maxTicks = 100)
	public void sheepDyeAndShear(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(4, 1, 4);
		Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, pos);
		SheepColors.set(sheep, first());
		helper.assertValueEqual(SheepColors.get(sheep), first(), "attached colour");
		helper.assertValueEqual(sheep.getColor(), DyeColor.WHITE, "vanilla colour while ours is set");
		sheep.shear(helper.getLevel(), SoundSource.PLAYERS, ItemStack.EMPTY);
		helper.assertTrue(sheep.isSheared(), "not sheared");
		helper.succeedWhen(() -> {
			List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, pos, 3.0);
			boolean ours = drops.stream().anyMatch(e -> e.getItem().is(block(Family.WOOL).asItem()));
			boolean vanilla = drops.stream().anyMatch(e -> e.getItem().is(Items.WOOL.white()));
			helper.assertTrue(ours, "no coloured wool dropped");
			helper.assertFalse(vanilla, net.minecraft.network.chat.Component.literal("vanilla white wool dropped"));
		});
	}

	/** A vanilla dye (setColor) clears our colour. */
	@GameTest
	public void sheepVanillaDyeClears(GameTestHelper helper) {
		floor(helper);
		Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, new BlockPos(4, 1, 4));
		SheepColors.set(sheep, first());
		sheep.setColor(DyeColor.RED);
		helper.assertTrue(SheepColors.get(sheep) == null, "our colour survived a vanilla dye");
		helper.assertValueEqual(sheep.getColor(), DyeColor.RED, "vanilla colour");
		helper.succeed();
	}

	/** Death loot: our wool plus mutton, never white wool. */
	@GameTest(maxTicks = 100)
	public void sheepDeathLoot(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(4, 1, 4);
		Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, pos);
		SheepColors.set(sheep, first());
		sheep.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000.0F);
		helper.succeedWhen(() -> {
			List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, pos, 3.0);
			helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(block(Family.WOOL).asItem())), "no coloured wool");
			helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.MUTTON)), "no mutton");
			helper.assertFalse(drops.stream().anyMatch(e -> e.getItem().is(Items.WOOL.white())),
					net.minecraft.network.chat.Component.literal("white wool dropped"));
		});
	}

	/** What a vanilla client is told about a dyed sheep: invisible and sheared. */
	@GameTest
	public void sheepOverlayHidesVanillaSheep(GameTestHelper helper) {
		floor(helper);
		Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, new BlockPos(4, 1, 4));
		SheepColors.set(sheep, first());
		List<SynchedEntityData.DataValue<?>> data = new ArrayList<>(sheep.getEntityData().getNonDefaultValues());
		new SheepOverlay(sheep).modifyRawTrackedData(data, null, true);
		boolean invisible = data.stream().anyMatch(v -> v.id() == 0 && v.value() instanceof Byte b && (b & 0x20) != 0);
		helper.assertTrue(invisible, "sheep not sent invisible");
		helper.succeed();
	}

	/** Every non-mod banner pattern gets one derived pattern per colour, with a tinted texture id. */
	@GameTest
	public void bannerPatternsDerived(GameTestHelper helper) {
		Registry<BannerPattern> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		long source = registry.entrySet().stream().filter(e -> !e.getKey().identifier().getNamespace().equals(MoreDyes.MOD_ID)).count();
		long derived = registry.entrySet().stream().filter(e -> e.getKey().identifier().getNamespace().equals(MoreDyes.MOD_ID)).count();
		helper.assertValueEqual(derived, source * ModColors.all().size(), "derived pattern count");
		Identifier creeper = BannerPatterns.derivedId(first(), Identifier.withDefaultNamespace("creeper"));
		helper.assertTrue(registry.containsKey(creeper), "missing " + creeper);
		helper.assertTrue(BannerPatterns.derived(creeper) != null, "derived record missing for " + creeper);
		helper.succeed();
	}
	/** Our candle on a vanilla cake becomes our candle cake; breaking it gives the candle back. */
	@GameTest
	public void candleOnCake(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, Blocks.CAKE);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block(Family.CANDLE)));
		helper.useBlock(pos, player);
		helper.assertBlockPresent(block(Family.CANDLE_CAKE), pos);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "candle not consumed");
		breakWithDrops(helper, pos);
		helper.succeedWhen(() -> helper.assertItemEntityCountIs(block(Family.CANDLE).asItem(), pos, 2.0, 1));
	}

	/** Glass is translucent in the model data the client gets, and panes connect like vanilla panes. */
	@GameTest
	public void glassAndPanes(GameTestHelper helper) {
		floor(helper);
		Block glass = block(Family.STAINED_GLASS);
		Block pane = block(Family.STAINED_GLASS_PANE);
		helper.assertTrue(!glass.defaultBlockState().canOcclude(), "glass occludes");
		// setBlock skips placement logic, so connections come from neighbour-shape updates: place
		// ours first, then its neighbours (and re-set ours to update the vanilla pane).
		helper.setBlock(new BlockPos(3, 1, 2), pane);
		helper.setBlock(new BlockPos(2, 1, 2), glass);
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.GLASS_PANE);
		BlockState ours = helper.getBlockState(new BlockPos(3, 1, 2));
		helper.assertTrue(ours.getValue(IronBarsBlock.WEST) && ours.getValue(IronBarsBlock.EAST), "our pane did not connect: " + ours);
		helper.assertTrue(!ours.getValue(IronBarsBlock.NORTH), "our pane connected to air");
		helper.setBlock(new BlockPos(3, 1, 2), ours.setValue(IronBarsBlock.WATERLOGGED, true));
		BlockState vanilla = helper.getBlockState(new BlockPos(4, 1, 2));
		helper.assertTrue(vanilla.getValue(IronBarsBlock.WEST), "vanilla pane did not connect to ours: " + vanilla);
		BlockState client = ((PolymerBlock) pane).getPolymerBlockState(ours, null);
		helper.assertTrue(client.getBlock() instanceof IronBarsBlock && client.getValue(IronBarsBlock.WEST)
				&& client.getValue(IronBarsBlock.EAST) && !client.getValue(IronBarsBlock.NORTH), "pane donor shape mismatch: " + client);
		helper.succeed();
	}

	private static ItemStack craft(GameTestHelper helper, ItemStack... grid) {
		CraftingInput input = CraftingInput.of(3, 3, List.of(grid));
		return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	private static ItemStack[] grid(ItemStack... items) {
		ItemStack[] grid = new ItemStack[9];
		java.util.Arrays.fill(grid, ItemStack.EMPTY);
		System.arraycopy(items, 0, grid, 0, items.length);
		return grid;
	}

	/** Leather armour takes our dye (exact RGB), and mixes with a vanilla dye by vanilla's rule. */
	@GameTest
	public void armorDye(GameTestHelper helper) {
		ItemStack dye = new ItemStack(ModContent.dye(first()));
		ItemStack out = craft(helper, grid(new ItemStack(Items.LEATHER_CHESTPLATE), dye));
		helper.assertTrue(out.is(Items.LEATHER_CHESTPLATE), "no dyed chestplate, got " + out);
		DyedItemColor color = out.get(DataComponents.DYED_COLOR);
		helper.assertTrue(color != null && (color.rgb() & 0xFFFFFF) == first().rgb(),
				"expected " + Integer.toHexString(first().rgb()) + ", got " + color);

		ItemStack mixed = craft(helper, grid(new ItemStack(Items.LEATHER_BOOTS), dye, new ItemStack(Items.DYE.white())));
		DyedItemColor mixedColor = mixed.get(DataComponents.DYED_COLOR);
		helper.assertTrue(mixedColor != null && (mixedColor.rgb() & 0xFFFFFF) != first().rgb(), "vanilla dye ignored in mix: " + mixedColor);

		ItemStack wolf = craft(helper, grid(new ItemStack(Items.WOLF_ARMOR), dye));
		helper.assertTrue(wolf.is(Items.WOLF_ARMOR) && wolf.has(DataComponents.DYED_COLOR), "wolf armour not dyed: " + wolf);
		helper.succeed();
	}

	/** Firework stars take our dye as a colour and as a fade colour; shape and trail still work. */
	@GameTest
	public void fireworkStar(GameTestHelper helper) {
		ItemStack dye = new ItemStack(ModContent.dye(first()));
		ItemStack star = craft(helper, grid(new ItemStack(Items.GUNPOWDER), dye, new ItemStack(Items.FIRE_CHARGE), new ItemStack(Items.DIAMOND)));
		helper.assertTrue(star.is(Items.FIREWORK_STAR), "no star, got " + star);
		FireworkExplosion explosion = star.get(DataComponents.FIREWORK_EXPLOSION);
		helper.assertTrue(explosion != null && explosion.colors().equals(IntList.of(first().rgb())), "colours: " + explosion);
		helper.assertTrue(explosion.shape() == FireworkExplosion.Shape.LARGE_BALL && explosion.hasTrail(), "shape/trail: " + explosion);

		ItemStack faded = craft(helper, grid(star, dye, new ItemStack(Items.DYE.red())));
		FireworkExplosion fade = faded.get(DataComponents.FIREWORK_EXPLOSION);
		helper.assertTrue(fade != null && fade.fadeColors().size() == 2 && fade.fadeColors().getInt(0) == first().rgb(), "fade: " + fade);
		helper.assertTrue(fade.colors().equals(explosion.colors()), "fade recipe lost colours");
		helper.succeed();
	}

	/** A bundle re-dyed with our dye keeps its contents and is a bundle for vanilla's tag recipes. */
	@GameTest
	public void bundleDye(GameTestHelper helper) {
		ItemStack bundle = new ItemStack(Items.BUNDLE);
		BundleContents.Mutable contents = BundleContents.EMPTY.asMutable();
		contents.tryInsert(new ItemStack(Items.DIAMOND, 4));
		bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
		ItemStack ours = craft(helper, grid(bundle, new ItemStack(ModContent.dye(first()))));
		helper.assertTrue(ours.is(ModContent.bundle(first())), "no coloured bundle, got " + ours);
		BundleContents kept = ours.get(DataComponents.BUNDLE_CONTENTS);
		helper.assertTrue(kept != null && !kept.isEmpty(), "contents lost");
		helper.assertTrue(ours.is(ItemTags.BUNDLES), "not in #minecraft:bundles");
		ItemStack back = craft(helper, grid(ours, new ItemStack(Items.DYE.white())));
		helper.assertTrue(back.is(Items.DYED_BUNDLE.white()), "vanilla dye did not take our bundle back: " + back);
		helper.succeed();
	}

	/**
	 * Beacon beam spike: a column with one of our glass blocks walks to a white section and then one
	 * of our colour, and the far-player fallback resolves to a real vanilla stained glass.
	 */
	@GameTest
	public void beaconBeamSections(GameTestHelper helper) {
		floor(helper);
		BlockPos beacon = new BlockPos(3, 1, 3);
		helper.setBlock(beacon, Blocks.BEACON);
		helper.setBlock(new BlockPos(3, 3, 3), block(Family.STAINED_GLASS));

		BeamWalk.Result result = BeamWalk.walk(helper.getLevel(), helper.absolutePos(beacon),
				helper.absolutePos(new BlockPos(3, 6, 3)).getY());

		helper.assertTrue(result.lit() && result.tinted(), "column not taken over: " + result);
		helper.assertTrue(result.sections().size() == 2, "expected white + our colour, got " + result.sections());
		int white = 0xFF000000 | DyeColor.WHITE.getTextureDiffuseColor();
		helper.assertTrue(result.sections().get(0).color() == white,
				"first section is not white: " + Integer.toHexString(result.sections().get(0).color()));
		helper.assertTrue(result.sections().get(0).height() == 2, "white height: " + result.sections().get(0));
		helper.assertTrue(result.sections().get(1).color() == first().argb(),
				"second section is not " + first().id() + ": " + Integer.toHexString(result.sections().get(1).color()));
		helper.assertTrue(result.ours().size() == 1, "our glass positions: " + result.ours());

		BeaconBeams.Fallback single = BeaconBeams.fallback(first(), false);
		helper.assertTrue(single.lower().getBlock() instanceof BeaconBeamBlock,
				"fallback is not a vanilla beam block: " + single.lower());
		helper.assertTrue(single.upper() == null, "no room above, but got a second block: " + single.upper());
		// With air above, the pair's average has to be at least as close as the best single dye.
		BeaconBeams.Fallback pair = BeaconBeams.fallback(first(), true);
		helper.assertTrue(pair.lower().getBlock() instanceof BeaconBeamBlock,
				"pair fallback is not a vanilla beam block: " + pair.lower());
		double singleDist = ModColor.labDistance(first().rgb(), beamColor(single) & 0xFFFFFF);
		double pairDist = ModColor.labDistance(first().rgb(), beamColor(pair) & 0xFFFFFF);
		helper.assertTrue(pairDist <= singleDist,
				"pair fallback is worse than the single: " + pairDist + " > " + singleDist);
		ItemStack core = BeaconBeams.beamStack(BeaconBeams.CORE, 8, result.sections().get(1).color());
		helper.assertTrue(core.has(DataComponents.ITEM_MODEL) && core.has(DataComponents.DYED_COLOR),
				"beam stack is missing its model or tint: " + core);
		// The height picks the model: each segment size has its own strip so the pattern repeats once
		// per block, and a stack asking for a size datagen does not write would be an invisible beam.
		helper.assertTrue(BeaconBeams.beamModel(BeaconBeams.CORE, 8).equals(core.get(DataComponents.ITEM_MODEL)),
				"beam stack is not on the 8-block model: " + core.get(DataComponents.ITEM_MODEL));
		for (int blocks : BeaconBeams.segmentSizes()) {
			helper.assertTrue(Integer.bitCount(blocks) == 1 && blocks <= BeaconBeams.SEGMENT_BLOCKS,
					"segment size is not a power of two within a segment: " + blocks);
		}
		helper.succeed();
	}

	/**
	 * The colour a far player's client ends up drawing the beam above the ghost glass: the lower
	 * block raw (the beacon is already section 0, so vanilla's {@code size() <= 1} quirk takes it
	 * raw) averaged with the upper one when there is a second block.
	 */
	private static int beamColor(BeaconBeams.Fallback fallback) {
		int lower = BeamWalk.colorOf(fallback.lower());
		return fallback.upper() == null ? lower : ARGB.average(lower, BeamWalk.colorOf(fallback.upper()));
	}

	/**
	 * Our stained glass wears a <b>spruce</b> leaves donor. Every other leaves block Polymer offers in
	 * that pool spawns falling leaf particles on the client
	 * ({@code FallingParticlesLeavesBlock.animateTick}), and a client runs that on the state it is
	 * sent whatever model the state is wearing — so glass shed leaves. Spruce is the one plain
	 * {@code LeavesBlock} in the pool; this asserts both halves, since the second is a fact about
	 * vanilla that a version bump could take away.
	 */
	@GameTest
	public void glassDonorIsSpruceLeaves(GameTestHelper helper) {
		helper.assertTrue(!(Blocks.SPRUCE_LEAVES instanceof FallingParticlesLeavesBlock),
				"spruce leaves now spawn falling particles; the glass donor has to move to another block");
		helper.assertTrue(Blocks.AZALEA_LEAVES instanceof FallingParticlesLeavesBlock,
				"azalea leaves no longer spawn particles, so the donor no longer has to dodge them");
		for (ModColor color : ModColors.all()) {
			BlockState client = ClientStates.clientStateOf(ModContent.block(color, Family.STAINED_GLASS), null);
			helper.assertTrue(client.is(Blocks.SPRUCE_LEAVES),
					color.id() + " glass wears " + client + ", not a spruce leaves donor");
		}
		// 7 distances x 2 persistent, less the state Polymer keeps back for a client with no pack.
		helper.assertTrue(ClientStates.donorStatesOf(Blocks.SPRUCE_LEAVES, BlockModelType.LEAVES) == 13,
				"spruce leaves offer " + ClientStates.donorStatesOf(Blocks.SPRUCE_LEAVES, BlockModelType.LEAVES)
						+ " donor states, not 13; the glass colour budget in Looks is derived from this");
		helper.succeed();
	}

	/**
	 * What the holder resends has to be the <b>server</b> state, because Polymer maps every
	 * {@code BlockState} written to a packet on its way out. Handing it a donor does not pass through:
	 * {@code BlockExtBlockMapper} runs anything that is not one of ours through the map
	 * {@code BlockResourceCreator} fills with each donor's no-pack look, so a pre-mapped resend lands
	 * on a bare leaf. This pins both halves: our glass maps to a donor, and that donor does not
	 * survive a second pass, while everything else the holder sends does.
	 */
	@GameTest
	public void beaconResendMustCarryTheServerState(GameTestHelper helper) {
		BlockState ours = block(Family.STAINED_GLASS).defaultBlockState();
		BlockState donor = PolymerBlockUtils.getPolymerBlockState(ours, null);
		helper.assertTrue(donor != ours && !(donor.getBlock() instanceof PolymerBlock),
				"our glass does not map to a vanilla donor: " + donor);
		helper.assertTrue(!BeaconBeams.isClientSafe(donor),
				"Polymer now passes an already-mapped donor through (" + donor
						+ "); the double-mapping trap the holder avoids is gone and this can be dropped");
		// The states the holder puts in packets itself, which have to survive Polymer untouched.
		BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
		for (BlockState state : List.of(Blocks.BARRIER.defaultBlockState(), Blocks.BEACON.defaultBlockState(),
				Blocks.STAINED_GLASS.pick(DyeColor.PINK).defaultBlockState(), light)) {
			helper.assertTrue(BeaconBeams.isClientSafe(state), "the holder sends " + state
					+ ", which Polymer re-maps to " + PolymerBlockUtils.getPolymerBlockState(state, null));
		}
		helper.assertTrue(light.getLightEmission() == 15,
				"the ghost light emits " + light.getLightEmission() + ", not 15");
		helper.assertTrue(light.getCollisionShape(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)).isEmpty(),
				"the ghost light has a collision box, so a near player would walk into nothing");
		helper.succeed();
	}

	/**
	 * A near player is shown a barrier where the beacon is, which emits no light, so the holder puts
	 * a ghost {@code minecraft:light} in the first air block above it — and nowhere else. It is never
	 * allowed to stand in for a block that is really there: with the column packed solid it gives up
	 * and the beacon is simply unlit for near players.
	 */
	@GameTest
	public void beaconGhostLightTakesOnlyAir(GameTestHelper helper) {
		floor(helper);
		BlockPos beacon = new BlockPos(1, 1, 1);
		helper.setBlock(beacon, Blocks.BEACON);
		BeaconBeamHolder holder = new BeaconBeamHolder(helper.getLevel(), helper.absolutePos(beacon));
		helper.assertTrue(helper.absolutePos(beacon).above().equals(holder.lightPosition()),
				"the ghost light is at " + holder.lightPosition() + ", not on top of the beacon");

		// Packed solid: the light has nowhere to go and must not take a real block's place.
		for (int i = 1; i <= 8; i++) helper.setBlock(beacon.above(i), Blocks.STONE);
		helper.assertTrue(holder.lightPosition() == null,
				"the ghost light took " + holder.lightPosition() + ", which is not air");

		// One gap in the column, above our own glass: the light goes there, not into the glass.
		helper.setBlock(beacon.above(2), Blocks.AIR);
		helper.setBlock(beacon.above(1), block(Family.STAINED_GLASS));
		helper.assertTrue(helper.absolutePos(beacon.above(2)).equals(holder.lightPosition()),
				"the ghost light is at " + holder.lightPosition() + ", not in the one air block");
		helper.succeed();
	}

	/**
	 * The beam's geometry for the column the spike is verified with: a beacon, two blocks of air, our
	 * glass. White has to start at the beacon's own bottom and cover all three blocks before the
	 * colour starts — the base of the beam is white in vanilla and has to be white here.
	 */
	@GameTest
	public void beaconBeamLayout(GameTestHelper helper) {
		int white = 0xFF000000 | DyeColor.WHITE.getTextureDiffuseColor();
		int ours = first().argb();
		List<BeaconBeams.Slice> slices = BeaconBeams.slice(
				List.of(new BeamWalk.Section(white, 3), new BeamWalk.Section(ours, 1)), 4, 64);

		helper.assertTrue(slices.size() == 4, "expected a full pool of segments: " + slices);
		// 3 blocks of white is a 2 and a 1: only powers of two have a model, and each one shows the
		// beam pattern once per block.
		helper.assertTrue(slices.get(0).equals(new BeaconBeams.Slice(white, 0, 2)), "first: " + slices);
		helper.assertTrue(slices.get(1).equals(new BeaconBeams.Slice(white, 2, 1)), "second: " + slices);
		helper.assertTrue(slices.get(2).equals(new BeaconBeams.Slice(ours, 3, 16)), "third: " + slices);
		helper.assertTrue(slices.get(3).equals(new BeaconBeams.Slice(ours, 19, 16)), "fourth: " + slices);

		// The same read as tuples: no gaps, no overlaps, white from the beacon's bottom up to the glass.
		int next = 0;
		for (BeaconBeams.Slice slice : slices) {
			helper.assertTrue(slice.fromBeacon() == next, "gap or overlap at " + slice + " in " + slices);
			helper.assertTrue(Integer.bitCount(slice.blocks()) == 1
					&& slice.blocks() <= BeaconBeams.SEGMENT_BLOCKS, "bad segment height: " + slice);
			helper.assertTrue((slice.fromBeacon() < 3) == (slice.color() == white),
					"the first three blocks are not the white section: " + slices);
			next += slice.blocks();
		}
		helper.succeed();
	}

	/**
	 * A pool segment reassigned from one shape to another has to be re-sent whole. Putting a second
	 * colour four blocks above the first turns segment 2 from "our colour, 16 blocks, from 3" into
	 * "our colour, 4 blocks, from 3": same element, same colour, same offset, different height — and
	 * the height picks the model and the scale, while the translation is the segment's mid-point and
	 * so depends on the height too. Caching that one on the offset alone left the shortened segment
	 * centred where its 16-block self had been, six blocks up inside the next segment, and the beam
	 * had a hole in it.
	 */
	@GameTest
	public void beaconSegmentReassigned(GameTestHelper helper) {
		int white = 0xFF000000 | DyeColor.WHITE.getTextureDiffuseColor();
		int ours = first().argb();
		int mixed = ARGB.average(ours, white);
		BeaconBeamHolder holder = new BeaconBeamHolder(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));

		// White for three blocks, then ours to the sky: 0h2 and 2h1 of white, then 3h16 of ours.
		// This is also the first-show path - every segment starts hidden, and both layers of each
		// have to come out of it with an item, a scale and a translation, not just the core.
		holder.layoutFor(List.of(new BeamWalk.Section(white, 3), new BeamWalk.Section(ours, 1)));
		assertSegment(helper, holder, 0, white, 2, 0);
		assertSegment(helper, holder, 1, white, 1, 2);
		assertSegment(helper, holder, 2, ours, 16, 3);

		// Now a second colour four blocks up: the same segment becomes 3h4, and the mix follows it.
		holder.layoutFor(List.of(new BeamWalk.Section(white, 3), new BeamWalk.Section(ours, 4),
				new BeamWalk.Section(mixed, 2)));
		assertSegment(helper, holder, 2, ours, 4, 3);
		assertSegment(helper, holder, 3, mixed, 16, 7);
		// And back again, to catch a cache that only ever grows.
		holder.layoutFor(List.of(new BeamWalk.Section(white, 3), new BeamWalk.Section(ours, 1)));
		assertSegment(helper, holder, 2, ours, 16, 3);
		helper.succeed();
	}

	/** Both elements of a segment carry the colour, the model for that height, the scale and the mid-point. */
	private static void assertSegment(GameTestHelper helper, BeaconBeamHolder holder, int index,
			int argb, int blocks, int fromBeacon) {
		Identifier[] layers = {BeaconBeams.CORE, BeaconBeams.GLOW};
		ItemDisplayElement[] elements = holder.segmentElements(index);
		for (int i = 0; i < elements.length; i++) {
			String what = "segment " + index + " " + (i == 0 ? "core" : "glow");
			ItemStack stack = elements[i].getItem();
			helper.assertTrue(ItemStack.matches(stack, BeaconBeams.beamStack(layers[i], blocks, argb)),
					what + " holds " + stack + ", not the " + blocks + "-block stack for "
							+ Integer.toHexString(argb));
			helper.assertTrue(elements[i].getScale().y() == blocks,
					what + " is scaled " + elements[i].getScale().y() + ", not " + blocks);
			float expected = fromBeacon + blocks / 2.0f - 0.5f;
			helper.assertTrue(elements[i].getTranslation().y() == expected,
					what + " sits at " + elements[i].getTranslation().y() + ", not " + expected);
		}
	}

	/** An untinted column is left to vanilla: the walk reports no colours of ours. */
	@GameTest
	public void beaconBeamVanillaColumnUntouched(GameTestHelper helper) {
		floor(helper);
		BlockPos beacon = new BlockPos(5, 1, 5);
		helper.setBlock(beacon, Blocks.BEACON);
		helper.setBlock(new BlockPos(5, 3, 5), Blocks.STAINED_GLASS.pick(DyeColor.RED));
		BeamWalk.Result result = BeamWalk.walk(helper.getLevel(), helper.absolutePos(beacon),
				helper.absolutePos(new BlockPos(5, 6, 5)).getY());
		helper.assertTrue(result.lit() && !result.tinted(), "vanilla column claimed: " + result);
		helper.succeed();
	}
}
