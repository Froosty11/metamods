package metacraft.moredyes.beacon;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.color.ModColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Beacon beam spike: a vanilla client computes the beam colour itself, from the vanilla stained
 * glass in the column ({@code BeaconBeamBlock}), so our glass — sent as a leaves donor — lets the
 * beam through white. Nothing we send about the block can change that, so the beam is replaced.
 *
 * Near players (within {@link #near()} blocks, horizontally) get the beacon hidden behind a barrier
 * plus display entities: a block display of the vanilla beacon so the block still looks right, and
 * two item displays per beam section (opaque core, translucent glow) tinted through a
 * {@code minecraft:dye} tint with a {@code dyed_color} component. Far players keep the vanilla
 * beacon and get our glass swapped for ghost vanilla stained glass — a pair whose average lands on
 * our colour where there is room above, see {@link #fallback} — so their own client tints the beam
 * approximately. {@link BeaconBeamHolder} owns both per player.
 *
 * Only columns that actually contain one of our colours are taken over; a plain vanilla beacon is
 * left completely alone. {@code -Dmoredyes.beacon=off} disables the feature,
 * {@code -Dmoredyes.beacon.near=<blocks>} moves the boundary.
 */
public final class BeaconBeams {
	/** Item-definition ids (assets/moredyes/items/<path>.json), see {@code GeneratedAssets}. */
	public static final Identifier CORE = Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "beacon_beam_core");
	public static final Identifier GLOW = Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "beacon_beam_glow");

	/** How often the column is re-walked and players re-classified. */
	public static final int REFRESH_TICKS = 20;

	/**
	 * Beam geometry budget. The beam is drawn as a fixed pool of segments, two display entities each
	 * (core and glow), allocated once per beacon and never added to or removed afterwards — see
	 * {@link BeaconBeamHolder} for why that is not just an optimisation. 16 × 16 blocks reaches 256
	 * blocks up for 1 + 2 × 16 = 33 entities per beacon.
	 */
	public static final int SEGMENTS = 16;

	/** Blocks per segment; the beam texture repeats once per segment rather than once per beam. */
	public static final int SEGMENT_BLOCKS = 16;

	/** How far above the world's build height the beam aims, vanilla-style "into the sky". */
	public static final int SKY_MARGIN = 64;

	/**
	 * Degrees the beam turns about Y per {@value #REFRESH_TICKS}-tick refresh. Vanilla's
	 * {@code BeaconRenderer.submitBeaconBeam} rotates the whole beam by
	 * {@code animationTime * 2.25f - 45.0f} degrees about {@code Axis.YP}, and {@code animationTime}
	 * is {@code floorMod(gameTime, 40) + partialTick}, so it is 2.25° a tick — 45° a second. We can
	 * only send packets, so we send that second's worth once and let the client interpolate it.
	 */
	public static final float SPIN_DEGREES = 2.25f * REFRESH_TICKS;

	private static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("moredyes.beacon", "on"));
	private static final double NEAR = Double.parseDouble(System.getProperty("moredyes.beacon.near", "128"));

	private static final Map<ModColor, Fallback> SINGLE = new ConcurrentHashMap<>();
	private static final Map<ModColor, Fallback> PAIR = new ConcurrentHashMap<>();

	private BeaconBeams() {}

	public static boolean enabled() {
		return ENABLED;
	}

	public static double near() {
		return NEAR;
	}

	public static void init() {
		if (!ENABLED) {
			MoreDyes.LOGGER.info("[{}] beacon beam takeover disabled (-Dmoredyes.beacon=off)", MoreDyes.MOD_ID);
			return;
		}
		// Polymer keeps the holder factory in a field on the Block (BlockMixin#polymerVE$setElementHolderCreator),
		// so a vanilla block can carry one without a mixin of ours. Registered before any chunk loads.
		BlockWithElementHolder.registerOverlay(Blocks.BEACON, new BlockWithElementHolder() {
			@Override
			public @Nullable ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
				return new BeaconBeamHolder(level, pos);
			}

			@Override
			public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
				return true;
			}
		});
		MoreDyes.LOGGER.info("[{}] beacon beam takeover on, near distance {} blocks", MoreDyes.MOD_ID, NEAR);
	}

	/** Horizontal distance, like vanilla's own beam scaling; height should not flip a player. */
	public static boolean isNear(BlockPos pos, Player player) {
		double dx = player.getX() - (pos.getX() + 0.5);
		double dz = player.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz <= NEAR * NEAR;
	}

	/**
	 * The ghost vanilla glass a far player is shown in place of one of ours: {@code lower} at our
	 * block's own position, and {@code upper} one above it when that block is air.
	 */
	public record Fallback(BlockState lower, @Nullable BlockState upper) {}

	/**
	 * The vanilla stained glass a far player's own client should tint the beam with. One glass can
	 * only ever reproduce one of the 16 dye colours, which for a saturated colour like cerise is a
	 * visible jump from the display beam — so when there is air above we spend a second ghost block
	 * and search every ordered pair too.
	 *
	 * That works because of the merge rule {@link BeamWalk} mirrors: the beacon is section 0, so the
	 * first glass is taken <i>raw</i> (the {@code size() <= 1} quirk) and a second, differing glass
	 * above it makes {@code ARGB.average} of the two. Averaging two dyes reaches colours no single
	 * dye does, so the beam above the pair lands much closer to ours. Distance is CIELAB, the same
	 * rule as {@code ModColor.nearestMapColor}: plain RGB distance picks badly for saturated colours.
	 *
	 * @param roomAbove whether the block above ours is air, so a second ghost block is free to place
	 */
	public static Fallback fallback(ModColor color, boolean roomAbove) {
		return (roomAbove ? PAIR : SINGLE).computeIfAbsent(color, c -> search(c, roomAbove));
	}

	private static Fallback search(ModColor color, boolean roomAbove) {
		DyeColor bestLower = DyeColor.WHITE;
		DyeColor bestUpper = null;
		double bestDist = Double.MAX_VALUE;
		for (DyeColor lower : DyeColor.values()) {
			double single = ModColor.labDistance(color.rgb(), opaque(lower) & 0xFFFFFF);
			if (single < bestDist) {
				bestDist = single;
				bestLower = lower;
				bestUpper = null;
			}
			if (!roomAbove) continue;
			for (DyeColor upper : DyeColor.values()) {
				// Same colour twice just extends the lower section, which the single case covers.
				if (upper == lower) continue;
				int merged = ARGB.average(opaque(lower), opaque(upper));
				double d = ModColor.labDistance(color.rgb(), merged & 0xFFFFFF);
				if (d < bestDist) {
					bestDist = d;
					bestLower = lower;
					bestUpper = upper;
				}
			}
		}
		MoreDyes.LOGGER.info("[{}] beacon fallback: {} -> vanilla {}{} stained glass",
				MoreDyes.MOD_ID, color.id(), bestLower.getName(),
				bestUpper == null ? "" : " + " + bestUpper.getName());
		return new Fallback(glass(bestLower), bestUpper == null ? null : glass(bestUpper));
	}

	private static BlockState glass(DyeColor dye) {
		return Blocks.STAINED_GLASS.pick(dye).defaultBlockState();
	}

	private static int opaque(DyeColor dye) {
		return 0xFF000000 | dye.getTextureDiffuseColor();
	}

	/**
	 * <b>Never pre-map a state into a block update packet.</b> Polymer maps every {@code BlockState}
	 * written to a packet at encode time, per player
	 * ({@code ByteBufCodecsEntriesMixin} on {@code Block.BLOCK_STATE_REGISTRY} →
	 * {@code PolymerBlockUtils.getPolymerBlockState} → {@code BlockMapper}), so a resend has to carry
	 * the <i>server</i> state exactly like vanilla's own block updates do.
	 *
	 * Handing it a state that is already a donor does not pass through, it maps a second time and
	 * lands somewhere else: {@code BlockExtBlockMapper.toClientSideState} sends anything that is not
	 * one of our {@code PolymerTexturedBlock}s through its {@code stateMap}, and
	 * {@code BlockResourceCreator.requestBlockImpl} fills that map with an entry per donor pointing at
	 * the look for a client <i>without</i> the pack — for a leaves donor
	 * {@code defaultBlockState().setValue(PERSISTENT, true)}, which {@code DefaultModelData} keeps out
	 * of the pool on purpose so no model is ever attached to it. That is why our glass by the beacon
	 * came out a plain leaf while the same block placed anywhere else was fine.
	 *
	 * @return whether Polymer maps {@code state} to itself, i.e. it is safe to put in a packet as-is
	 */
	public static boolean isClientSafe(BlockState state) {
		return PolymerBlockUtils.getPolymerBlockState(state, null) == state;
	}

	/**
	 * A beam quad stack: a vanilla item carrying one of our item-model definitions (Polymer passes
	 * vanilla stacks through untouched) plus the section colour in {@code dyed_color}, which the
	 * definition's {@code minecraft:dye} tint multiplies into the beam texture. Block displays
	 * cannot be tinted to an arbitrary RGB, which is why the beam is item displays.
	 *
	 * The glow rides a translucent block's item and the core a solid one — the same split vanilla's
	 * {@code BeaconRenderer} makes between its inner and outer beam. In 26.3 that choice is belt and
	 * braces rather than the thing that decides the render layer: a quad's layer is baked per quad
	 * from its own sprite ({@code FaceBakery.computeMaterialTransparency} →
	 * {@code BakedQuad.MaterialInfo.of}, which picks {@code Sheets.translucentBlockItemSheet} when
	 * the sprite region has translucent texels or the material sets {@code force_translucent}), not
	 * from the carrier item. {@code GeneratedAssets} pins the glow's material translucent so the
	 * layer never depends on how the alpha analysis reads our strip.
	 *
	 * @param model  {@link #CORE} or {@link #GLOW}; the stack gets that model's {@code blocks}-tall
	 *               variant, so the beam pattern repeats once per block at any segment height
	 * @param blocks the segment's height in blocks, one of the powers of two up to
	 *               {@value #SEGMENT_BLOCKS}
	 */
	public static ItemStack beamStack(Identifier model, int blocks, int argb) {
		Item carrier = (GLOW.equals(model) ? Blocks.STAINED_GLASS.pick(DyeColor.WHITE) : Blocks.STONE).asItem();
		ItemStack stack = new ItemStack(carrier);
		stack.set(DataComponents.ITEM_MODEL, beamModel(model, blocks));
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(argb & 0xFFFFFF));
		return stack;
	}

	/**
	 * The item-definition id of a {@code blocks}-tall beam part, e.g.
	 * {@code moredyes:beacon_beam_glow_16}. A block model cannot tile a face's UV, so the repeat
	 * count has to be baked into the texture: one model and one animation strip per segment height,
	 * each carrying {@code blocks} copies of the 16 × 16 beam tile. {@code GeneratedAssets} writes
	 * exactly the sizes {@link #segmentSizes()} lists.
	 */
	public static Identifier beamModel(Identifier model, int blocks) {
		return model.withSuffix("_" + blocks);
	}

	/** One drawn segment: {@code blocks} tall, sitting {@code fromBeacon} blocks above the beacon. */
	public record Slice(int color, int fromBeacon, int blocks) {}

	/**
	 * Cut the walk's sections into segments, bottom up — the beam's whole geometry, as a pure
	 * function of the walk, so a test can assert it without a client.
	 *
	 * Each segment is a power of two blocks up to {@value #SEGMENT_BLOCKS}, largest first, because the
	 * repeat count is baked into each model's texture and only those sizes have one: a 3-block section
	 * is a 2 and a 1, each showing the pattern once per block. Like vanilla's {@code BeaconRenderer},
	 * which draws the last section with height 2048 whatever the walk said, the last section is
	 * stretched to {@code toSky} — rounded down to whole segments, and never past what the pool can
	 * draw.
	 *
	 * @param pool  how many segments there are to spend
	 * @param toSky blocks above the beacon's own bottom the beam should reach
	 */
	public static List<Slice> slice(List<BeamWalk.Section> sections, int pool, int toSky) {
		List<Slice> slices = new ArrayList<>();
		int offset = 0; // blocks above the beacon block's own bottom
		for (int i = 0; i < sections.size() && slices.size() < pool; i++) {
			BeamWalk.Section section = sections.get(i);
			int left = (pool - slices.size()) * SEGMENT_BLOCKS;
			int height = section.height();
			if (i == sections.size() - 1) {
				int reach = Math.min(Math.max(toSky - offset, 0), left);
				// Whole segments only: a tail of 8 + 4 + 2 + 1 would spend four of them on 15 blocks.
				height = Math.max(height, reach - reach % SEGMENT_BLOCKS);
			}
			height = Math.min(height, left);
			while (height > 0 && slices.size() < pool) {
				int blocks = Integer.highestOneBit(Math.min(height, SEGMENT_BLOCKS));
				slices.add(new Slice(section.color(), offset, blocks));
				offset += blocks;
				height -= blocks;
			}
		}
		return List.copyOf(slices);
	}

	/**
	 * The segment heights the beam is built from: powers of two up to {@value #SEGMENT_BLOCKS}, so
	 * any section height is a handful of segments that each show the pattern once per block.
	 */
	public static int[] segmentSizes() {
		int count = Integer.numberOfTrailingZeros(SEGMENT_BLOCKS) + 1;
		int[] sizes = new int[count];
		for (int i = 0; i < count; i++) sizes[i] = 1 << i;
		return sizes;
	}
}
