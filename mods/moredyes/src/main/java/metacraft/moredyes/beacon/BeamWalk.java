package metacraft.moredyes.beacon;

import metacraft.moredyes.content.GlassBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla's beam walk, server-side, with our glass added as a colour source.
 *
 * Re-implemented from {@code BeaconBlockEntity.tick} (26.3-rc-1 bytecode): start at the beacon block
 * itself — {@code BeaconBlock} implements {@code BeaconBeamBlock} and returns white, which is where
 * the first section's colour comes from — and walk up to the {@code WORLD_SURFACE} heightmap. A
 * {@code BeaconBeamBlock} contributes its dye colour; anything with light dampening 15 that is not
 * bedrock ends the beam and clears the sections (no beam at all); everything else extends the
 * current section. The merge rule is vanilla's, quirk included: while there are at most two sections
 * a new colour starts a raw section, after that a differing colour starts a section of
 * {@code ARGB.average(current, new)}.
 *
 * Pure apart from reading blocks, so a game test can call it on a hand-built column.
 */
public final class BeamWalk {
	/** Hard stop so a broken heightmap cannot spin the server. */
	private static final int MAX_STEPS = 512;

	private BeamWalk() {}

	/** One run of constant colour; {@code color} is {@code 0xAARRGGBB}, {@code height} is in blocks. */
	public record Section(int color, int height) {}

	/**
	 * @param sections vanilla's beam sections, bottom-up; empty when the beam is blocked
	 * @param ours     positions of our own glass in the column, for the far-player block resends
	 */
	public record Result(List<Section> sections, List<BlockPos> ours) {
		/** A beam is drawn at all (the column reaches the sky). */
		public boolean lit() {
			return !sections.isEmpty();
		}

		/** The column contains at least one of our colours, so vanilla would get the beam wrong. */
		public boolean tinted() {
			return !ours.isEmpty();
		}
	}

	public static Result walk(Level level, BlockPos beacon) {
		return walk(level, beacon, level.getHeight(Heightmap.Types.WORLD_SURFACE, beacon.getX(), beacon.getZ()));
	}

	public static Result walk(BlockGetter level, BlockPos beacon, int topY) {
		List<Section> sections = new ArrayList<>();
		List<BlockPos> ours = new ArrayList<>();
		int[] current = null; // {colour, height} of the section being extended
		BlockPos.MutableBlockPos pos = beacon.mutable();
		for (int step = 0; step < MAX_STEPS && pos.getY() <= topY; step++) {
			BlockState state = level.getBlockState(pos);
			Integer color = colorOf(state);
			if (color != null) {
				if (state.getBlock() instanceof GlassBlocks.Glass || state.getBlock() instanceof GlassBlocks.Pane) {
					ours.add(pos.immutable());
				}
				if (sections.size() <= 1) {
					current = new int[]{color, 1};
					sections.add(new Section(color, 1));
				} else if (color != current[0]) {
					current = new int[]{ARGB.average(current[0], color), 1};
					sections.add(new Section(current[0], 1));
				} else {
					current[1]++;
				}
			} else if (current == null || (state.getLightDampening() >= 15 && !state.is(Blocks.BEDROCK))) {
				sections.clear();
				ours.clear();
				break;
			} else {
				current[1]++;
			}
			sections.set(sections.size() - 1, new Section(current[0], current[1]));
			pos.move(0, 1, 0);
		}
		return new Result(List.copyOf(sections), List.copyOf(ours));
	}

	/** The beam colour a block contributes, {@code 0xAARRGGBB}, or null if it is not a beam block. */
	public static @Nullable Integer colorOf(BlockState state) {
		if (state.getBlock() instanceof GlassBlocks.Glass glass) return glass.color().argb();
		if (state.getBlock() instanceof GlassBlocks.Pane pane) return pane.color().argb();
		if (state.getBlock() instanceof BeaconBeamBlock beam) return opaque(beam.getColor());
		return null;
	}

	private static int opaque(DyeColor color) {
		return 0xFF000000 | color.getTextureDiffuseColor();
	}
}
