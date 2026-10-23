package nu.metacraft.seedscan;

/** Measures sampling costs and how good the cheap surface estimate is. */
final class Bench {
    static void run(Gen gen) {
        for (int round = 0; round < 3; round++) {
            Gen.Seed s = gen.new Seed(12345L + round);
            int m = 1500;
            double bias = 0, abs = 0, absMaxed = 0;
            int agreeRough = 0, agreeFloor = 0, agreeMaxed = 0, agree63 = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < m; i++) {
                int x = (i % 39) * 104 - 2000, z = (i / 39) * 104 - 2000;
                int real = s.surface(x, z);
                int rough = s.roughSurface(x, z);
                int maxed = Math.max(rough, 63);
                bias += real - rough;
                abs += Math.abs(real - rough);
                absMaxed += Math.abs(real - maxed);
                int b = s.biome(x, real - 1, z);
                if (s.biome(x, rough, z) == b) agreeRough++;
                if (s.biome(x, maxed, z) == b) agreeMaxed++;
                if (s.biome(x, 63, z) == b) agree63++;
            }
            long t1 = System.nanoTime();
            SeedScan.LOG.info("bench: real height+biomes {} us; real-rough bias {} abs {} abs(max63) {}; biome agreement rough {} max63 {} y63 {}",
                (t1 - t0) / 1000.0 / m, bias / m, abs / m, absMaxed / m, agreeRough / (double) m, agreeMaxed / (double) m, agree63 / (double) m);
        }
    }
}
