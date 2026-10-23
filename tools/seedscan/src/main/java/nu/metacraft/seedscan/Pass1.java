package nu.metacraft.seedscan;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cheap biome-only pass: 64-block surface grid and 128-block underground grid over +-7040 blocks,
 * candidate border centres every 256 blocks within +-5888, scored on biome categories per stage,
 * ocean share and (approximate) stronghold distance.
 */
final class Pass1 {
    static final int G = 64, N = 220, O = -7040;
    static final int G2 = 128, N2 = 110;
    static final int CENTRE_STEP = 256, CENTRE_RANGE = 23;
    static final int[] HALF = {8, 12, 16};
    static final int[] HALF2 = {4, 6, 8};

    static void run(Gen gen, Path out) throws Exception {
        int count = Integer.getInteger("seedscan.count", 5000);
        long rngSeed = Long.getLong("seedscan.rng", 6L);
        double minutes = Double.parseDouble(System.getProperty("seedscan.minutes", "1e9"));
        Path file = out.resolve("pass1.jsonl");
        Set<Long> done = new HashSet<>();
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file)) {
                int i = line.indexOf("\"seed\":");
                if (i >= 0) {
                    done.add(Long.parseLong(line.substring(i + 7, line.indexOf(',', i))));
                }
            }
        }
        SplittableRandom rng = new SplittableRandom(rngSeed);
        List<Long> seeds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long s = rng.nextLong();
            if (!done.contains(s)) {
                seeds.add(s);
            }
        }
        SeedScan.LOG.info("pass1: {} seeds to do ({} already done)", seeds.size(), done.size());
        int[] smask = Cat.surfaceMasks(gen);
        int[] umask = Cat.undergroundMasks(gen);
        long deadline = System.currentTimeMillis() + (long) (minutes * 60_000);
        AtomicInteger progress = new AtomicInteger();
        long start = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(SeedScan.threads());
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (long seed : seeds) {
                pool.submit(() -> {
                    if (System.currentTimeMillis() > deadline) {
                        return;
                    }
                    try {
                        String line = scanSeed(gen, seed, smask, umask);
                        synchronized (w) {
                            w.write(line);
                            w.newLine();
                            w.flush();
                        }
                        int p = progress.incrementAndGet();
                        if (p % 50 == 0) {
                            SeedScan.LOG.info("pass1: {} seeds, {} s/seed", p, (System.currentTimeMillis() - start) / 1000.0 / p);
                        }
                    } catch (Throwable t) {
                        SeedScan.LOG.error("pass1 seed {} failed", seed, t);
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.DAYS);
        }
    }

    static String scanSeed(Gen gen, long seed, int[] smask, int[] umask) {
        Gen.Seed s = gen.new Seed(seed);
        int cats = Cat.ALL.size();
        int[][] pre = new int[cats][(N + 1) * (N + 1)];
        int[][] pre2 = new int[cats][(N2 + 1) * (N2 + 1)];
        int[] cell = new int[N * N];
        for (int iz = 0; iz < N; iz++) {
            for (int ix = 0; ix < N; ix++) {
                int x = O + G / 2 + G * ix, z = O + G / 2 + G * iz;
                int y = Math.max(s.roughSurface(x, z), 63);
                cell[iz * N + ix] = smask[s.biome(x, y, z)];
            }
        }
        prefix(cell, N, pre);
        int[] cell2 = new int[N2 * N2];
        for (int iz = 0; iz < N2; iz++) {
            for (int ix = 0; ix < N2; ix++) {
                int x = O + G2 / 2 + G2 * ix, z = O + G2 / 2 + G2 * iz;
                cell2[iz * N2 + ix] = umask[s.biome(x, -20, z)] | umask[s.biome(x, -50, z)];
            }
        }
        prefix(cell2, N2, pre2);
        List<int[]> strongholds = Strongholds.approximate(gen, seed, 9);

        List<double[]> scored = new ArrayList<>();
        List<String> details = new ArrayList<>();
        for (int kz = -CENTRE_RANGE; kz <= CENTRE_RANGE; kz++) {
            for (int kx = -CENTRE_RANGE; kx <= CENTRE_RANGE; kx++) {
                int cx = kx * CENTRE_STEP, cz = kz * CENTRE_STEP;
                int ci = (cx - O) / G, cj = (cz - O) / G;
                int ci2 = (cx - O) / G2, cj2 = (cz - O) / G2;
                int[] stage = new int[cats];
                for (Cat c : Cat.ALL) {
                    stage[c.ordinal()] = 3;
                    for (int st = 0; st < 3; st++) {
                        int n = c.underground
                            ? rect(pre2[c.ordinal()], N2, ci2 - HALF2[st], cj2 - HALF2[st], ci2 + HALF2[st], cj2 + HALF2[st])
                            : rect(pre[c.ordinal()], N, ci - HALF[st], cj - HALF[st], ci + HALF[st], cj + HALF[st]);
                        if (n >= (c.underground ? 1 : 2)) {
                            stage[c.ordinal()] = st;
                            break;
                        }
                    }
                }
                double ocean = rect(pre[Cat.OCEAN.ordinal()], N, ci - 8, cj - 8, ci + 8, cj + 8) / 256.0;
                double ocean2 = rect(pre[Cat.OCEAN.ordinal()], N, ci - 12, cj - 12, ci + 12, cj + 12) / 576.0;
                double sh = Double.MAX_VALUE;
                for (int[] p : strongholds) {
                    sh = Math.min(sh, Math.max(Math.abs(p[0] - cx), Math.abs(p[1] - cz)));
                }
                double score = Score.biomes(stage) + Score.ocean(ocean, ocean2) + Score.stronghold(sh);
                scored.add(new double[] {score, cx, cz, ocean, sh, details.size()});
                StringBuilder st = new StringBuilder();
                for (int v : stage) {
                    st.append(v);
                }
                details.add(st.toString());
            }
        }
        scored.sort((a, b) -> Double.compare(b[0], a[0]));
        StringBuilder sb = new StringBuilder();
        sb.append("{\"seed\":").append(seed).append(",\"centres\":[");
        List<double[]> picked = new ArrayList<>();
        for (double[] c : scored) {
            boolean far = true;
            for (double[] p : picked) {
                far &= Math.max(Math.abs(p[1] - c[1]), Math.abs(p[2] - c[2])) >= 1024;
            }
            if (!far) {
                continue;
            }
            if (!picked.isEmpty()) {
                sb.append(',');
            }
            picked.add(c);
            sb.append(String.format(Locale.ROOT, "{\"x\":%d,\"z\":%d,\"score\":%.3f,\"ocean\":%.3f,\"sh\":%.0f,\"stages\":\"%s\"}",
                (int) c[1], (int) c[2], c[0], c[3], c[4], details.get((int) c[5])));
            if (picked.size() == 3) {
                break;
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    static void prefix(int[] cell, int n, int[][] pre) {
        for (int c = 0; c < pre.length; c++) {
            int bit = 1 << c;
            int[] p = pre[c];
            for (int z = 0; z < n; z++) {
                int row = 0;
                for (int x = 0; x < n; x++) {
                    row += (cell[z * n + x] & bit) != 0 ? 1 : 0;
                    p[(z + 1) * (n + 1) + x + 1] = p[z * (n + 1) + x + 1] + row;
                }
            }
        }
    }

    /** Sum over cells [x0, x1) x [z0, z1), clamped to the grid. */
    static int rect(int[] p, int n, int x0, int z0, int x1, int z1) {
        x0 = Math.max(0, x0);
        z0 = Math.max(0, z0);
        x1 = Math.min(n, x1);
        z1 = Math.min(n, z1);
        if (x1 <= x0 || z1 <= z0) {
            return 0;
        }
        int w = n + 1;
        return p[z1 * w + x1] - p[z0 * w + x1] - p[z1 * w + x0] + p[z0 * w + x0];
    }
}
