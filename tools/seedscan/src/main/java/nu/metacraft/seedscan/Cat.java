package nu.metacraft.seedscan;

import java.util.List;
import java.util.Set;

/** Biome categories the server cares about, with their score weights. */
enum Cat {
    OAK(0.5, false, "forest", "flower_forest", "plains", "sunflower_plains", "windswept_forest", "wooded_badlands", "swamp", "dark_forest", "meadow", "dappled_forest"),
    BIRCH(0.8, false, "birch_forest", "old_growth_birch_forest", "forest", "flower_forest", "meadow"),
    SPRUCE(0.8, false, "taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "grove", "windswept_forest", "windswept_hills"),
    DARK_OAK(1.0, false, "dark_forest"),
    JUNGLE(1.0, false, "jungle", "sparse_jungle", "bamboo_jungle"),
    ACACIA(1.0, false, "savanna", "savanna_plateau", "windswept_savanna"),
    MANGROVE(1.0, false, "mangrove_swamp"),
    CHERRY(1.0, false, "cherry_grove"),
    PALE_OAK(1.0, false, "pale_garden"),
    DESERT(1.0, false, "desert"),
    BADLANDS(1.2, false, "badlands", "eroded_badlands", "wooded_badlands"),
    SNOWY(1.0, false, "snowy_plains", "ice_spikes", "snowy_taiga", "snowy_slopes", "grove", "frozen_peaks", "jagged_peaks"),
    SWAMP(0.8, false, "swamp"),
    OCEAN(0.6, false, "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean"),
    PEAKS(1.2, false, "jagged_peaks", "frozen_peaks", "stony_peaks"),
    MUSHROOM(0.3, false, "mushroom_fields"),
    LUSH(1.0, true, "lush_caves"),
    DEEP_DARK(1.0, true, "deep_dark"),
    DRIPSTONE(0.2, true, "dripstone_caves");

    static final List<Cat> ALL = List.of(values());
    /** Score factor by stage: start, stage 2, stage 3, outside. */
    static final double[] STAGE_FACTOR = {1.0, 0.6, 0.25, 0.0};
    /** Half widths of the border at each stage. */
    static final int[] STAGE_HALF = {500, 750, 1000};

    final double weight;
    final boolean underground;
    final Set<String> biomes;

    Cat(double weight, boolean underground, String... biomes) {
        this.weight = weight;
        this.underground = underground;
        this.biomes = Set.of(biomes);
    }

    /** Bit mask of surface categories per biome id. */
    static int[] surfaceMasks(Gen gen) {
        return masks(gen, false);
    }

    static int[] undergroundMasks(Gen gen) {
        return masks(gen, true);
    }

    private static int[] masks(Gen gen, boolean underground) {
        int[] masks = new int[gen.biomeNames.size()];
        for (int i = 0; i < masks.length; i++) {
            for (Cat c : ALL) {
                if (c.underground == underground && c.biomes.contains(gen.biomeNames.get(i))) {
                    masks[i] |= 1 << c.ordinal();
                }
            }
        }
        return masks;
    }

    /** Which stage (0..2, 3 = none) a Chebyshev distance from the centre falls in. */
    static int stageOf(double chebyshev) {
        for (int s = 0; s < 3; s++) {
            if (chebyshev <= STAGE_HALF[s]) {
                return s;
            }
        }
        return 3;
    }
}
