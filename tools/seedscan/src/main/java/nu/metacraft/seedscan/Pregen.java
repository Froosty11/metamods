package nu.metacraft.seedscan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Generates the chunks around each screenshot position (-Dseedscan.shots, lines for this world's seed)
 * by force-loading them in the real server, so the client only has to render.
 */
final class Pregen {
    static final int RADIUS = 14;

    static void run(Gen gen, Path out) throws Exception {
        long seed = gen.level.getSeed();
        Set<Long> chunks = new LinkedHashSet<>();
        for (String line : Files.readAllLines(Path.of(System.getProperty("seedscan.shots")))) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 8 || !p[0].equals(Long.toString(seed))) {
                continue;
            }
            int cx = (int) Math.floor(Double.parseDouble(p[3])) >> 4, cz = (int) Math.floor(Double.parseDouble(p[5])) >> 4;
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    chunks.add(((long) (cx + dx) << 32) | ((cz + dz) & 0xffffffffL));
                }
            }
        }
        List<Long> all = new ArrayList<>(chunks);
        SeedScan.LOG.info("pregen: {} chunks for seed {}", all.size(), seed);
        long t0 = System.currentTimeMillis();
        int batch = 1024;
        for (int i = 0; i < all.size(); i += batch) {
            List<Long> part = all.subList(i, Math.min(all.size(), i + batch));
            onServer(gen, () -> part.forEach(c -> gen.level.setChunkForced((int) (c >> 32), (int) (long) c, true)));
            while (true) {
                boolean done = onServer(gen, () -> part.stream().allMatch(c -> gen.level.getChunkSource().hasChunk((int) (c >> 32), (int) (long) c)));
                if (done) {
                    break;
                }
                Thread.sleep(500);
            }
            onServer(gen, () -> part.forEach(c -> gen.level.setChunkForced((int) (c >> 32), (int) (long) c, false)));
            SeedScan.LOG.info("pregen: {} / {} chunks, {} s", Math.min(all.size(), i + batch), all.size(), (System.currentTimeMillis() - t0) / 1000);
        }
        Cmd.run(gen.server, 0, 100, 0, "save-all flush");
    }

    private static void onServer(Gen gen, Runnable r) {
        onServer(gen, () -> {
            r.run();
            return true;
        });
    }

    private static <T> T onServer(Gen gen, java.util.function.Supplier<T> s) {
        CompletableFuture<T> f = new CompletableFuture<>();
        gen.server.execute(() -> {
            try {
                f.complete(s.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f.join();
    }
}
