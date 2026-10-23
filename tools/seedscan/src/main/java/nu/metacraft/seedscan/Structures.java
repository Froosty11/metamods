package nu.metacraft.seedscan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Finds structure starts the way ChunkGenerator.createStructures does (placement, weighted pick,
 * Structure.generate with biome and terrain checks), without generating any chunks.
 */
final class Structures {
    record Found(String type, int x, int z) {
    }

    static final Set<String> SETS = Set.of(
        "villages", "desert_pyramids", "igloos", "jungle_temples", "swamp_huts", "pillager_outposts", "ocean_monuments",
        "woodland_mansions", "ancient_cities", "strongholds", "trail_ruins", "trial_chambers", "abandoned_camp");

    static ChunkGeneratorStructureState state(Gen gen, Gen.Seed s) {
        return gen.generator.createState(gen.structureSets, s.randomState, s.seed);
    }

    static List<Found> find(Gen gen, Gen.Seed s, ChunkGeneratorStructureState state, int cx, int cz, int radius) {
        List<Found> found = new ArrayList<>();
        int minChunkX = (cx - radius) >> 4, maxChunkX = (cx + radius) >> 4;
        int minChunkZ = (cz - radius) >> 4, maxChunkZ = (cz + radius) >> 4;
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            String name = set.unwrapKey().orElseThrow().identifier().getPath();
            if (!SETS.contains(name)) {
                continue;
            }
            StructurePlacement placement = set.value().placement();
            if (placement instanceof RandomSpreadStructurePlacement spread) {
                int spacing = spread.spacing();
                for (int rz = Math.floorDiv(minChunkZ, spacing); rz <= Math.floorDiv(maxChunkZ, spacing); rz++) {
                    for (int rx = Math.floorDiv(minChunkX, spacing); rx <= Math.floorDiv(maxChunkX, spacing); rx++) {
                        ChunkPos p = spread.getPotentialStructureChunk(s.seed, rx * spacing, rz * spacing);
                        if (p.x() < minChunkX || p.x() > maxChunkX || p.z() < minChunkZ || p.z() > maxChunkZ) {
                            continue;
                        }
                        if (spread.isStructureChunk(state, p.x(), p.z())) {
                            Found f = tryGenerate(gen, s, set.value(), p);
                            if (f != null) {
                                found.add(f);
                            }
                        }
                    }
                }
            } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                for (ChunkPos p : state.getRingPositionsFor(rings)) {
                    if (p.x() >= minChunkX && p.x() <= maxChunkX && p.z() >= minChunkZ && p.z() <= maxChunkZ) {
                        // The locate position of a stronghold chunk; its start is always valid in land biomes.
                        found.add(new Found("stronghold", p.x() * 16 + 8, p.z() * 16 + 8));
                    }
                }
            }
        }
        return found;
    }

    private static Found tryGenerate(Gen gen, Gen.Seed s, StructureSet set, ChunkPos pos) {
        List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(set.structures());
        if (options.size() == 1) {
            return generate(gen, s, options.get(0), pos);
        }
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(s.seed, pos.x(), pos.z());
        int total = 0;
        for (StructureSet.StructureSelectionEntry option : options) {
            total += option.weight();
        }
        while (!options.isEmpty()) {
            int choice = random.nextInt(total);
            int index = 0;
            for (StructureSet.StructureSelectionEntry option : options) {
                choice -= option.weight();
                if (choice < 0) {
                    break;
                }
                index++;
            }
            StructureSet.StructureSelectionEntry selected = options.get(index);
            Found f = generate(gen, s, selected, pos);
            if (f != null) {
                return f;
            }
            options.remove(index);
            total -= selected.weight();
        }
        return null;
    }

    private static Found generate(Gen gen, Gen.Seed s, StructureSet.StructureSelectionEntry entry, ChunkPos pos) {
        var structure = entry.structure().value();
        StructureStart start = structure.generate(
            entry.structure(), Level.OVERWORLD, gen.server.registryAccess(), gen.generator, gen.biomeSource, s.climate, s.randomState,
            gen.server.getStructureTemplateManager(), s.seed, pos, 0, gen.level, structure.biomes()::contains);
        if (!start.isValid()) {
            return null;
        }
        BoundingBox box = start.getBoundingBox();
        String name = entry.structure().unwrapKey().orElseThrow().identifier().getPath();
        if (name.startsWith("village_")) {
            name = "village";
        } else if (name.startsWith("abandoned_camp")) {
            name = "abandoned_camp";
        }
        return new Found(name, box.getCenter().getX(), box.getCenter().getZ());
    }
}
