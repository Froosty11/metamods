package nu.metacraft.seedscan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Checks the datapack is active: pack order, biome size versus vanilla scale, no river lanterns. */
final class Sanity {
    static void run(Gen gen, Path out) throws Exception {
        List<String> report = new ArrayList<>();
        report.add("datapack list: " + Cmd.run(gen.server, 0, 100, 0, "datapack list"));
        int lanterns = 0;
        for (Holder<Biome> biome : gen.biomeSource.possibleBiomes()) {
            for (HolderSet<PlacedFeature> step : biome.value().getGenerationSettings().features()) {
                for (Holder<PlacedFeature> f : step) {
                    if (f.unwrapKey().map(k -> k.identifier().toString().contains("lantern")).orElse(false)) {
                        lanterns++;
                    }
                }
            }
        }
        report.add("biomes with a lantern feature: " + lanterns);
        // Mean length of a run of the same surface biome along x (16-block steps), a proxy for biome width.
        for (long seed : new long[] {1, 2, 3}) {
            Gen.Seed s = gen.new Seed(seed);
            long runs = 0, cells = 0, climateRuns = 0;
            for (int line = 0; line < 20; line++) {
                int z = -8000 + line * 800;
                int prev = -2, prevClimate = -2;
                for (int x = -8000; x < 8000; x += 16) {
                    int b = s.biome(x, Math.max(s.roughSurface(x, z), 63), z);
                    if (b != prev) {
                        runs++;
                        prev = b;
                    }
                    var p = s.climate.sample(x >> 2, 16, z >> 2);
                    int climate = band(p.temperature() / 10000f, -0.45f, -0.15f, 0.2f, 0.55f) * 8 + band(p.humidity() / 10000f, -0.35f, -0.1f, 0.1f, 0.3f);
                    if (climate != prevClimate) {
                        climateRuns++;
                        prevClimate = climate;
                    }
                    cells++;
                }
            }
            report.add(String.format(Locale.ROOT, "seed %d: mean surface biome run length %.0f blocks, mean temperature/humidity band run length %.0f blocks", seed, cells * 16.0 / runs, cells * 16.0 / climateRuns));
        }
        for (String biome : new String[] {"desert", "cherry_grove", "badlands", "mangrove_swamp", "pale_garden", "jagged_peaks", "lush_caves", "deep_dark"}) {
            report.add("/locate biome " + biome + ": " + Cmd.run(gen.server, 0, 100, 0, "locate biome minecraft:" + biome));
        }
        for (String line : report) {
            SeedScan.LOG.info("sanity: {}", line);
        }
        Files.write(out.resolve("sanity.txt"), report);
    }

    private static int band(float v, float... thresholds) {
        int i = 0;
        while (i < thresholds.length && v >= thresholds[i]) {
            i++;
        }
        return i;
    }
}
