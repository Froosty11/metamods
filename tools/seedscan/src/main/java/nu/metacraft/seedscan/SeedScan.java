package nu.metacraft.seedscan;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a scan job inside a dedicated server once it has started, then stops the server.
 * The job is chosen with -Dseedscan.job=bench|pass1|pass2|verify and writes to -Dseedscan.out.
 */
public class SeedScan implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("seedscan");

    @Override
    public void onInitialize() {
        String job = System.getProperty("seedscan.job");
        if (job == null) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Thread thread = new Thread(() -> runJob(server, job), "seedscan");
            thread.setDaemon(true);
            thread.start();
        });
    }

    private static void runJob(MinecraftServer server, String job) {
        try {
            Path out = Path.of(System.getProperty("seedscan.out", "seedscan-out"));
            Files.createDirectories(out);
            Gen gen = new Gen(server);
            LOG.info("seedscan job {} starting, {} biomes", job, gen.biomeNames.size());
            switch (job) {
                case "bench" -> Bench.run(gen);
                case "sanity" -> Sanity.run(gen, out);
                case "pass1" -> Pass1.run(gen, out);
                case "pass2" -> Pass2.run(gen, out);
                case "verify" -> Verify.run(gen, out);
                default -> LOG.error("unknown job {}", job);
            }
            LOG.info("seedscan job {} done", job);
        } catch (Throwable t) {
            LOG.error("seedscan job failed", t);
        } finally {
            if (!Boolean.getBoolean("seedscan.keepRunning")) {
                server.halt(false);
            }
        }
    }

    static int threads() {
        return Integer.getInteger("seedscan.threads", Runtime.getRuntime().availableProcessors());
    }
}
