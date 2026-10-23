package nu.metacraft.seedscan;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Detailed pass for a list of (seed, centre) candidates read from pass2-input.txt: 16-block surface grid
 * with heights, 32-block underground grid, exact structure starts, valleys and terrain types. The centre
 * is refined within +-448 blocks. With -Dseedscan.final=true heights are the real terrain heights, the
 * centre is kept, and map data is dumped for rendering.
 */
final class Pass2 {
    static final int R = 1800, S = 16, N = 2 * R / S;
    static final int S3 = 32, N3 = 2 * R / S3;
    static final int REFINE = 448, REFINE_STEP = 64;
    static final int SURFACE_MIN_CELLS = 32, UNDER_MIN_CELLS = 8, LIST_MIN_CELLS = 8;
    static final int VALLEY_DEPTH = 40, VALLEY_REACH = 256;

    static final Map<String, Double> STRUCTURE_WEIGHTS = Map.ofEntries(
        Map.entry("trial_chambers", 1.5), Map.entry("ancient_city", 1.2), Map.entry("stronghold", 1.5),
        Map.entry("desert_pyramid", 0.4), Map.entry("jungle_pyramid", 0.4), Map.entry("pillager_outpost", 0.4),
        Map.entry("monument", 0.4), Map.entry("mansion", 0.6), Map.entry("trail_ruins", 0.4), Map.entry("swamp_hut", 0.3),
        Map.entry("igloo", 0.2), Map.entry("abandoned_camp", 0.1));

    static void run(Gen gen, Path out) throws Exception {
        boolean fin = Boolean.getBoolean("seedscan.final");
        List<String> input = Files.readAllLines(out.resolve(System.getProperty("seedscan.input", "pass2-input.txt")));
        Path file = out.resolve(fin ? "final.jsonl" : "pass2.jsonl");
        AtomicInteger progress = new AtomicInteger();
        long start = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(SeedScan.threads());
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (String line : input) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 3) {
                    continue;
                }
                long seed = Long.parseLong(p[0]);
                int x = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                pool.submit(() -> {
                    try {
                        String result = new Pass2(gen, seed, x, z, fin, out).evaluate();
                        synchronized (w) {
                            w.write(result);
                            w.newLine();
                            w.flush();
                        }
                        int n = progress.incrementAndGet();
                        SeedScan.LOG.info("pass2: {} of {} done, {} s each", n, input.size(), (System.currentTimeMillis() - start) / 1000.0 / n);
                    } catch (Throwable t) {
                        SeedScan.LOG.error("pass2 {} failed", line, t);
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.DAYS);
        }
    }

    final Gen gen;
    final Gen.Seed s;
    final int gx, gz;
    final boolean fin;
    final Path out;
    final int nb;
    final int[] height = new int[N * N];
    final byte[] biome = new byte[N * N];
    final boolean[] water = new boolean[N * N];
    final boolean[] valley = new boolean[N * N];
    final int[][] biomePre;
    final int[][] underPre;
    int[] landPre, valleyPre, lowPre, hillPre, plateauPre, peakPre;
    List<Structures.Found> structures;

    Pass2(Gen gen, long seed, int x, int z, boolean fin, Path out) {
        this.gen = gen;
        this.s = gen.new Seed(seed);
        this.gx = x;
        this.gz = z;
        this.fin = fin;
        this.out = out;
        this.nb = gen.biomeNames.size();
        this.biomePre = new int[nb][];
        this.underPre = new int[nb][];
    }

    int cellX(int i) {
        return gx - R + S / 2 + S * i;
    }

    int cellZ(int j) {
        return gz - R + S / 2 + S * j;
    }

    String evaluate() throws IOException {
        Set<String> waterBiomes = Set.of("river", "frozen_river");
        int[] smask = Cat.surfaceMasks(gen);
        boolean[] isWater = new boolean[nb];
        for (int b = 0; b < nb; b++) {
            isWater[b] = waterBiomes.contains(gen.biomeNames.get(b)) || (smask[b] & (1 << Cat.OCEAN.ordinal())) != 0;
        }
        if (fin) {
            // Real heights are ~1 ms per column, so spread them over a few threads of our own.
            Gen.Seed[] perThread = new Gen.Seed[N];
            IntStream.range(0, N).parallel().forEach(j -> {
                Gen.Seed local = gen.new Seed(s.seed);
                for (int i = 0; i < N; i++) {
                    height[j * N + i] = Math.max(local.surface(cellX(i), cellZ(j)), 63);
                }
            });
        }
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                int x = cellX(i), z = cellZ(j);
                int y = fin ? height[j * N + i] - 1 : Math.max(s.roughSurface(x, z), 63);
                if (!fin) {
                    height[j * N + i] = y;
                }
                int b = s.biome(x, Math.max(y, 63), z);
                biome[j * N + i] = (byte) b;
                water[j * N + i] = isWater[b];
            }
        }
        int[] under = new int[N3 * N3 * 2];
        for (int j = 0; j < N3; j++) {
            for (int i = 0; i < N3; i++) {
                int x = gx - R + S3 / 2 + S3 * i, z = gz - R + S3 / 2 + S3 * j;
                under[(j * N3 + i) * 2] = s.biome(x, -20, z);
                under[(j * N3 + i) * 2 + 1] = s.biome(x, -50, z);
            }
        }
        // Valleys: land cells with ground at least VALLEY_DEPTH higher within VALLEY_REACH on two opposite sides.
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        int steps = VALLEY_REACH / S;
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                if (water[j * N + i]) {
                    continue;
                }
                int h = height[j * N + i];
                boolean[] high = new boolean[8];
                for (int d = 0; d < 8; d++) {
                    int max = Integer.MIN_VALUE;
                    for (int k = 1; k <= steps; k++) {
                        int ii = i + dirs[d][0] * k, jj = j + dirs[d][1] * k;
                        if (ii < 0 || jj < 0 || ii >= N || jj >= N) {
                            break;
                        }
                        max = Math.max(max, height[jj * N + ii]);
                    }
                    high[d] = max >= h + VALLEY_DEPTH;
                }
                valley[j * N + i] = (high[0] && high[1]) || (high[2] && high[3]) || (high[4] && high[5]) || (high[6] && high[7]);
            }
        }
        // Prefix sums.
        for (int b = 0; b < nb; b++) {
            final int bb = b;
            biomePre[b] = prefix(N, k -> biome[k] == bb);
            underPre[b] = prefix(N3, k -> under[k * 2] == bb || under[k * 2 + 1] == bb);
        }
        landPre = prefix(N, k -> !water[k]);
        valleyPre = prefix(N, k -> valley[k]);
        lowPre = prefix(N, k -> !water[k] && height[k] <= 85);
        hillPre = prefix(N, k -> !water[k] && height[k] > 85 && height[k] <= 140);
        peakPre = prefix(N, k -> !water[k] && height[k] >= 200);
        plateauPre = prefix(N, k -> {
            int i = k % N, j = k / N;
            if (water[k] || height[k] < 100 || i == 0 || j == 0 || i == N - 1 || j == N - 1) {
                return false;
            }
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    int v = height[(j + dj) * N + i + di];
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
            return max - min <= 6;
        });

        var state = Structures.state(gen, s);
        structures = Structures.find(gen, s, state, gx, gz, fin ? 4600 : 2600);

        // Refine the centre.
        double[] best = null;
        int range = fin ? 0 : REFINE;
        for (int dz = -range; dz <= range; dz += REFINE_STEP) {
            for (int dx = -range; dx <= range; dx += REFINE_STEP) {
                double[] parts = score(gx + dx, gz + dz);
                if (best == null || parts[0] > best[0]) {
                    best = parts;
                    best[best.length - 2] = gx + dx;
                    best[best.length - 1] = gz + dz;
                }
            }
        }
        int cx = (int) best[best.length - 2], cz = (int) best[best.length - 1];
        String json = describe(cx, cz, best);
        if (fin) {
            dump(cx, cz, json);
        }
        return json;
    }

    interface CellTest {
        boolean test(int k);
    }

    static int[] prefix(int n, CellTest test) {
        int[] p = new int[(n + 1) * (n + 1)];
        for (int z = 0; z < n; z++) {
            int row = 0;
            for (int x = 0; x < n; x++) {
                row += test.test(z * n + x) ? 1 : 0;
                p[(z + 1) * (n + 1) + x + 1] = p[z * (n + 1) + x + 1] + row;
            }
        }
        return p;
    }

    /** Count of cells whose centre lies within the square of half width `half` around (cx, cz). */
    int count(int[] pre, int n, int step, int cx, int cz, int half) {
        int base = -R + step / 2;
        int i0 = ceilDiv(cx - half - gx - base, step), i1 = Math.floorDiv(cx + half - gx - base, step) + 1;
        int j0 = ceilDiv(cz - half - gz - base, step), j1 = Math.floorDiv(cz + half - gz - base, step) + 1;
        return Pass1.rect(pre, n, i0, j0, i1, j1);
    }

    static int ceilDiv(int a, int b) {
        return -Math.floorDiv(-a, b);
    }

    int surfaceCount(int b, int cx, int cz, int half) {
        return count(biomePre[b], N, S, cx, cz, half);
    }

    int[] catStages(int cx, int cz) {
        int[] stage = new int[Cat.ALL.size()];
        for (Cat c : Cat.ALL) {
            stage[c.ordinal()] = 3;
            for (int st = 0; st < 3; st++) {
                int n = 0;
                for (int b = 0; b < nb; b++) {
                    if (c.biomes.contains(gen.biomeNames.get(b))) {
                        n += c.underground ? count(underPre[b], N3, S3, cx, cz, Cat.STAGE_HALF[st]) : surfaceCount(b, cx, cz, Cat.STAGE_HALF[st]);
                    }
                }
                if (n >= (c.underground ? UNDER_MIN_CELLS : SURFACE_MIN_CELLS)) {
                    stage[c.ordinal()] = st;
                    break;
                }
            }
        }
        return stage;
    }

    double oceanShare(int cx, int cz, int half) {
        int land = count(landPre, N, S, cx, cz, half);
        int all = count(prefix1(), N, S, cx, cz, half);
        int ocean = 0;
        int[] smask = Cat.surfaceMasks(gen);
        for (int b = 0; b < nb; b++) {
            if ((smask[b] & (1 << Cat.OCEAN.ordinal())) != 0) {
                ocean += surfaceCount(b, cx, cz, half);
            }
        }
        return all == 0 ? 0 : ocean / (double) all;
    }

    private int[] ones;

    int[] prefix1() {
        if (ones == null) {
            ones = prefix(N, k -> true);
        }
        return ones;
    }

    static int stageOf(Structures.Found f, int cx, int cz) {
        return Cat.stageOf(Math.max(Math.abs(f.x() - cx), Math.abs(f.z() - cz)));
    }

    double structureScore(int cx, int cz) {
        int[] villages = new int[4];
        Map<String, Integer> bestStage = new HashMap<>();
        double strongholdDist = Double.MAX_VALUE;
        for (Structures.Found f : structures) {
            int st = stageOf(f, cx, cz);
            if (f.type().equals("village")) {
                villages[st]++;
            }
            if (f.type().equals("stronghold")) {
                strongholdDist = Math.min(strongholdDist, Math.max(Math.abs(f.x() - cx), Math.abs(f.z() - cz)));
            }
            bestStage.merge(f.type(), st, Math::min);
        }
        double score = 0;
        score += villages[0] >= 1 ? 1.5 : 0;
        score += villages[0] + villages[1] >= 2 ? 1.0 : 0;
        score += villages[0] + villages[1] + villages[2] >= 3 ? 0.5 : 0;
        for (var e : STRUCTURE_WEIGHTS.entrySet()) {
            if (e.getKey().equals("stronghold")) {
                continue;
            }
            score += e.getValue() * Cat.STAGE_FACTOR[bestStage.getOrDefault(e.getKey(), 3)];
        }
        score += Score.stronghold(strongholdDist);
        return score;
    }

    double valleyShare(int cx, int cz, int half) {
        int land = count(landPre, N, S, cx, cz, half);
        return land == 0 ? 0 : count(valleyPre, N, S, cx, cz, half) / (double) land;
    }

    boolean[] terrain(int cx, int cz) {
        int half = Cat.STAGE_HALF[1];
        int land = Math.max(1, count(landPre, N, S, cx, cz, half));
        int beach = 0;
        for (String b : new String[] {"beach", "snowy_beach", "stony_shore"}) {
            int id = gen.biomeId(b);
            if (id >= 0) {
                beach += surfaceCount(id, cx, cz, half);
            }
        }
        return new boolean[] {
            count(lowPre, N, S, cx, cz, half) >= 0.10 * land,
            count(hillPre, N, S, cx, cz, half) >= 0.10 * land,
            count(plateauPre, N, S, cx, cz, half) >= 0.03 * land,
            count(peakPre, N, S, cx, cz, half) >= 0.01 * land,
            beach >= SURFACE_MIN_CELLS
        };
    }

    static final String[] TERRAIN = {"lowland", "hills", "plateau", "peaks", "coast"};

    /** [total, biomes, ocean, structures, valleys, terrain, x, z]. */
    double[] score(int cx, int cz) {
        double biomes = Score.biomes(catStages(cx, cz));
        double ocean = Score.ocean(oceanShare(cx, cz, 500), oceanShare(cx, cz, 750));
        double struct = structureScore(cx, cz);
        double valleys = 2.0 * Math.min(1, valleyShare(cx, cz, 750) / 0.15);
        double terrain = 0;
        for (boolean t : terrain(cx, cz)) {
            terrain += t ? 0.3 : 0;
        }
        return new double[] {biomes + ocean + struct + valleys + terrain, biomes, ocean, struct, valleys, terrain, 0, 0};
    }

    /** Connected valley regions (4-neighbour) with their size and centroid, largest first. */
    List<int[]> valleyRegions(int cx, int cz) {
        int[] label = new int[N * N];
        List<int[]> regions = new ArrayList<>();
        int[] stack = new int[N * N];
        for (int k = 0; k < N * N; k++) {
            if (!valley[k] || label[k] != 0) {
                continue;
            }
            int id = regions.size() + 1;
            int sp = 0, size = 0;
            long sx = 0, sz = 0;
            stack[sp++] = k;
            label[k] = id;
            while (sp > 0) {
                int c = stack[--sp];
                size++;
                sx += cellX(c % N);
                sz += cellZ(c / N);
                int i = c % N, j = c / N;
                int[] nbrs = {i > 0 ? c - 1 : -1, i < N - 1 ? c + 1 : -1, j > 0 ? c - N : -1, j < N - 1 ? c + N : -1};
                for (int nk : nbrs) {
                    if (nk >= 0 && valley[nk] && label[nk] == 0) {
                        label[nk] = id;
                        stack[sp++] = nk;
                    }
                }
            }
            int x = (int) (sx / size), z = (int) (sz / size);
            regions.add(new int[] {size * S * S, x, z, Cat.stageOf(Math.max(Math.abs(x - cx), Math.abs(z - cz)))});
        }
        regions.sort(Comparator.comparingInt((int[] r) -> -r[0]));
        return regions;
    }

    String describe(int cx, int cz, double[] parts) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "{\"seed\":%d,\"x\":%d,\"z\":%d,\"score\":%.3f,\"parts\":{\"biomes\":%.3f,\"ocean\":%.3f,\"structures\":%.3f,\"valleys\":%.3f,\"terrain\":%.3f}",
            s.seed, cx, cz, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]));
        sb.append(String.format(Locale.ROOT, ",\"oceanStart\":%.3f,\"oceanStage2\":%.3f,\"valleyStart\":%.3f,\"valleyStage2\":%.3f,\"valleyStage3\":%.3f,\"realHeights\":%b",
            oceanShare(cx, cz, 500), oceanShare(cx, cz, 750), valleyShare(cx, cz, 500), valleyShare(cx, cz, 750), valleyShare(cx, cz, 1000), fin));
        int[] cats = catStages(cx, cz);
        sb.append(",\"cats\":{");
        for (Cat c : Cat.ALL) {
            sb.append(c.ordinal() > 0 ? "," : "").append('"').append(c.name().toLowerCase(Locale.ROOT)).append("\":").append(cats[c.ordinal()]);
        }
        boolean[] terrain = terrain(cx, cz);
        sb.append("},\"terrain\":{");
        for (int t = 0; t < TERRAIN.length; t++) {
            sb.append(t > 0 ? "," : "").append('"').append(TERRAIN[t]).append("\":").append(terrain[t]);
        }
        // Biomes: first stage present and share of each stage-3 area.
        sb.append("},\"biomes\":{");
        int total3 = count(prefix1(), N, S, cx, cz, 1000);
        boolean first = true;
        for (int b = 0; b < nb; b++) {
            int stage = 3;
            for (int st = 0; st < 3; st++) {
                int n = surfaceCount(b, cx, cz, Cat.STAGE_HALF[st]);
                int u = count(underPre[b], N3, S3, cx, cz, Cat.STAGE_HALF[st]);
                if (n >= LIST_MIN_CELLS || u >= 2) {
                    stage = st;
                    break;
                }
            }
            if (stage == 3) {
                continue;
            }
            double share = surfaceCount(b, cx, cz, 1000) / (double) total3;
            sb.append(first ? "" : ",").append(String.format(Locale.ROOT, "\"%s\":[%d,%.4f]", gen.biomeNames.get(b), stage, share));
            first = false;
        }
        sb.append("},\"structures\":[");
        first = true;
        List<Structures.Found> sorted = new ArrayList<>(structures);
        sorted.sort(Comparator.comparingDouble(f -> Math.hypot(f.x() - cx, f.z() - cz)));
        for (Structures.Found f : sorted) {
            sb.append(first ? "" : ",").append(String.format(Locale.ROOT, "[\"%s\",%d,%d,%d]", f.type(), f.x(), f.z(), stageOf(f, cx, cz)));
            first = false;
        }
        sb.append("],\"valleyRegions\":[");
        first = true;
        int listed = 0;
        for (int[] r : valleyRegions(cx, cz)) {
            if (r[3] > 2 || listed >= 8) {
                continue;
            }
            sb.append(first ? "" : ",").append(String.format(Locale.ROOT, "[%d,%d,%d,%d]", r[0], r[1], r[2], r[3]));
            first = false;
            listed++;
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Map data for rendering: detail biomes (4 blocks/px, 3000 wide), real heights (8 blocks/px), context biomes (16 blocks/px, 8000 wide). */
    void dump(int cx, int cz, String json) throws IOException {
        Path dir = out.resolve("dump").resolve(Long.toString(s.seed));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("summary.json"), json);
        Files.write(dir.resolve("biomes.txt"), gen.biomeNames);
        int dn = 750;
        byte[] detail = new byte[dn * dn];
        IntStream.range(0, dn).parallel().forEach(j -> {
            Gen.Seed local = gen.new Seed(s.seed);
            for (int i = 0; i < dn; i++) {
                int x = cx - 1500 + 2 + 4 * i, z = cz - 1500 + 2 + 4 * j;
                detail[j * dn + i] = (byte) local.biome(x, Math.max(local.roughSurface(x, z), 63), z);
            }
        });
        Files.write(dir.resolve("detail_biome_750.u8"), detail);
        int hn = 375;
        short[] heights = new short[hn * hn];
        IntStream.range(0, hn).parallel().forEach(j -> {
            Gen.Seed local = gen.new Seed(s.seed);
            for (int i = 0; i < hn; i++) {
                heights[j * hn + i] = (short) local.surface(cx - 1500 + 4 + 8 * i, cz - 1500 + 4 + 8 * j);
            }
        });
        try (DataOutputStream o = new DataOutputStream(Files.newOutputStream(dir.resolve("detail_height_375.i16")))) {
            for (short h : heights) {
                o.writeShort(h);
            }
        }
        int cn = 500;
        byte[] context = new byte[cn * cn];
        IntStream.range(0, cn).parallel().forEach(j -> {
            Gen.Seed local = gen.new Seed(s.seed);
            for (int i = 0; i < cn; i++) {
                int x = cx - 4000 + 8 + 16 * i, z = cz - 4000 + 8 + 16 * j;
                context[j * cn + i] = (byte) local.biome(x, Math.max(local.roughSurface(x, z), 63), z);
            }
        });
        Files.write(dir.resolve("context_biome_500.u8"), context);
        // Valley mask on the analysis grid (16 blocks/px around the analysis origin).
        byte[] v = new byte[N * N];
        for (int k = 0; k < N * N; k++) {
            v[k] = (byte) (valley[k] ? 1 : 0);
        }
        Files.write(dir.resolve("valley_" + N + ".u8"), v);
        Files.writeString(dir.resolve("grid.json"), String.format(Locale.ROOT,
            "{\"seed\":%d,\"centre\":[%d,%d],\"analysisOrigin\":[%d,%d],\"analysisStep\":%d,\"analysisN\":%d}", s.seed, cx, cz, gx - R, gz - R, S, N));
    }
}
