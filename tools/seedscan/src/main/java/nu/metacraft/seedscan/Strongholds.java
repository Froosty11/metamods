package nu.metacraft.seedscan;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

/**
 * Stronghold ring positions before the biome nudge (vanilla moves each by at most 112 blocks to a
 * preferred biome). Mirrors ChunkGeneratorStructureState.generateRingPositions, minus the biome search.
 */
final class Strongholds {
    static List<int[]> approximate(Gen gen, long seed, int limit) {
        ConcentricRingsStructurePlacement placement =
            (ConcentricRingsStructurePlacement) gen.structureSets.getOrThrow(BuiltinStructureSets.STRONGHOLDS).value().placement();
        int distance = placement.distance();
        int count = Math.min(limit, placement.count());
        int spread = placement.spread();
        RandomSource random = RandomSource.create();
        random.setSeed(seed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int positionInCircle = 0;
        int circle = 0;
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double dist = 4 * distance + distance * circle * 6 + (random.nextDouble() - 0.5) * (distance * 2.5);
            int chunkX = (int) Math.round(Math.cos(angle) * dist);
            int chunkZ = (int) Math.round(Math.sin(angle) * dist);
            random.fork();
            result.add(new int[] {chunkX * 16 + 8, chunkZ * 16 + 8});
            angle += (Math.PI * 2) / spread;
            if (++positionInCircle == spread) {
                circle++;
                positionInCircle = 0;
                spread += 2 * spread / (circle + 1);
                spread = Math.min(spread, placement.count() - i);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }
        return result;
    }
}
