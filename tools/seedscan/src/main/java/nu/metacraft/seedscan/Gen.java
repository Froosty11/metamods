package nu.metacraft.seedscan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** The loaded overworld generator (Tectonic + datapack), usable for any seed without generating chunks. */
public final class Gen {
    public final MinecraftServer server;
    public final ServerLevel level;
    public final NoiseBasedChunkGenerator generator;
    public final BiomeSource biomeSource;
    public final NoiseGeneratorSettings settings;
    public final HolderGetter<NormalNoise> noises;
    public final HolderLookup<StructureSet> structureSets;
    public final DensityFunction preliminarySurface;
    /** Biome index <-> name (path only, all overworld biomes are minecraft:). */
    public final List<String> biomeNames = new ArrayList<>();
    private final Map<Holder<Biome>, Integer> biomeIds = new HashMap<>();

    public Gen(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
        this.generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        this.biomeSource = generator.getBiomeSource();
        this.settings = generator.generatorSettings().value();
        this.noises = server.registryAccess().lookupOrThrow(Registries.NOISE);
        this.structureSets = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        this.preliminarySurface = server.registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION)
            .getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace("overworld/preliminary_surface_level")))
            .value();
        biomeSource.possibleBiomes().stream()
            .map(h -> h.unwrapKey().orElseThrow().identifier().toString())
            .sorted()
            .forEach(name -> biomeNames.add(name.replace("minecraft:", "")));
        for (Holder<Biome> h : biomeSource.possibleBiomes()) {
            biomeIds.put(h, biomeNames.indexOf(h.unwrapKey().orElseThrow().identifier().toString().replace("minecraft:", "")));
        }
    }

    public int biomeId(Holder<Biome> biome) {
        Integer id = biomeIds.get(biome);
        return id == null ? -1 : id;
    }

    public int biomeId(String name) {
        return biomeNames.indexOf(name);
    }

    /** Per seed, per thread sampling state. Not thread safe. */
    public final class Seed {
        public final long seed;
        public final RandomState randomState;
        public final Climate.Sampler climate;
        public final BiomeResolver biomes;
        private final SamplerContext context;
        private final DensitySampler surfaceSampler;

        public Seed(long seed) {
            this.seed = seed;
            this.randomState = RandomState.create(noises, seed, settings);
            this.context = SamplerContext.builder().enableCaches().build();
            this.climate = randomState.createClimateSampler(context);
            this.biomes = biomeSource.createResolver(climate);
            this.surfaceSampler = randomState.getSampler(preliminarySurface);
        }

        /** Tectonic's preliminary surface estimate (cheap, smooth; no 3D noise). */
        public int roughSurface(int x, int z) {
            return (int) Math.floor(surfaceSampler.sampleValue(context, x, 0, z));
        }

        /** The real terrain height (top non-air block + 1, water counts), as used by worldgen. */
        public int surface(int x, int z) {
            return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        }

        /** The real terrain height ignoring water. */
        public int floor(int x, int z) {
            return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
        }

        public int biome(int x, int y, int z) {
            return biomeId(biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)));
        }
    }
}
