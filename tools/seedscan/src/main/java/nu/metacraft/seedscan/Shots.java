package nu.metacraft.seedscan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import net.minecraft.client.CloudStatus;

/**
 * Client game test that takes the screenshots. Each world was created beforehand by a dedicated server
 * (Metacraft datapack in place before creation) and is copied into saves/ and opened in singleplayer.
 * Input (-Dseedscan.shots): lines "seed worldDir name x y z yaw pitch". Output: -Dseedscan.shotsOut.
 */
public class Shots implements FabricClientGameTest {
    record Shot(String name, double x, double y, double z, float yaw, float pitch) {
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        String input = System.getProperty("seedscan.shots");
        if (input == null) {
            return;
        }
        Path outDir = Path.of(System.getProperty("seedscan.shotsOut", "shots"));
        Map<String, List<Shot>> byWorld = new LinkedHashMap<>();
        Map<String, String> seeds = new LinkedHashMap<>();
        try {
            Files.createDirectories(outDir);
            for (String line : Files.readAllLines(Path.of(input))) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 8 || line.startsWith("#")) {
                    continue;
                }
                seeds.put(p[1], p[0]);
                byWorld.computeIfAbsent(p[1], k -> new ArrayList<>()).add(new Shot(p[2], Double.parseDouble(p[3]), Double.parseDouble(p[4]),
                    Double.parseDouble(p[5]), Float.parseFloat(p[6]), Float.parseFloat(p[7])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        context.runOnClient(client -> {
            client.options.renderDistance().set(12);
            client.options.simulationDistance().set(5);
            client.options.fov().set(70);
            client.options.cloudStatus().set(CloudStatus.OFF);
        });
        for (var entry : byWorld.entrySet()) {
            String seed = seeds.get(entry.getKey());
            Path save = context.computeOnClient(client -> client.getLevelSource().getBaseDir()).resolve("seed_" + seed);
            copy(Path.of(entry.getKey()), save);
            try (TestSingleplayerContext sp = new TestWorldSaveImpl(context, save).open()) {
                sp.getServer().runCommand("gamerule minecraft:advance_time false");
                sp.getServer().runCommand("gamerule minecraft:advance_weather false");
                sp.getServer().runCommand("gamerule minecraft:spawn_mobs false");
                sp.getServer().runCommand("time set noon");
                sp.getServer().runCommand("weather clear");
                sp.getServer().runCommand("gamemode spectator @a");
                context.runOnClient(client -> {
                    if (!client.gui.hud.isHidden()) {
                        client.gui.hud.toggle();
                    }
                });
                for (Shot shot : entry.getValue()) {
                    sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f",
                        shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch()));
                    context.waitTicks(20);
                    sp.getConnection().waitForChunksRender(true, 20 * 60 * 30);
                    context.waitTicks(100);
                    sp.getConnection().waitForChunksRender(true, 20 * 60 * 10);
                    context.takeScreenshot(TestScreenshotOptions.of(seed + "_" + shot.name()).withSize(1920, 1080)
                        .disableCounterPrefix().withDestinationDir(outDir));
                    SeedScan.LOG.info("shot {} {} taken", seed, shot.name());
                }
            }
        }
    }

    private static void copy(Path from, Path to) {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                Path target = to.resolve(from.relativize(f).toString());
                if (Files.isDirectory(f)) {
                    Files.createDirectories(target);
                } else if (!f.getFileName().toString().equals("session.lock")) {
                    Files.copy(f, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
