package nu.metacraft.nether_rifts.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Captures the nether panorama the rift shows through its cracks (tools/panorama, then tools/gen_panorama.py).
 * Runs only with -Dnether_rifts.capturePanorama=true (./gradlew :mods:nether-rifts:runClientGameTest
 * -PcapturePanorama); otherwise it does nothing. On a fixed seed it finds an open spot in the nether
 * near a crimson forest, with lava below, and takes Minecraft's own six-face panorama there.
 */
public final class PanoramaCapture implements FabricClientGameTest {

	static final boolean CAPTURING = Boolean.getBoolean("nether_rifts.capturePanorama");
	private static final String SEED = "6202610";
	private static final int CLEAR = 6;
	private static final int LAVA_BELOW = 30;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!CAPTURING) {
			return;
		}
		Path out = Path.of(System.getProperty("nether_rifts.panoramaDir", "panorama")).toAbsolutePath();
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", String.valueOf(RiftClientTests.freePort()));
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("level-seed", SEED);
		props.setProperty("spawn-monsters", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ctx.waitTicks(20);
				ctx.waitFor(client -> client.gui.overlay() == null, 20 * 90);
				server.runCommand("gamemode spectator Tester");
				BlockPos spot = server.computeOnServer(s -> findSpot(s.getLevel(Level.NETHER)));
				System.out.println("[nether-rifts-clienttest] panorama seed=" + SEED + " spot=" + spot.toShortString());
				server.runCommand("execute in minecraft:the_nether run tp Tester " + spot.getX() + ".5 " + spot.getY() + " " + spot.getZ() + ".5 0 0");
				conn.waitForClientboundPackets();
				ctx.waitTicks(40);
				// sections are only built once seen: look every way first, then take the panorama twice, keeping the second
				float[][] looks = {{0, 0}, {90, 0}, {180, 0}, {270, 0}, {0, -90}, {0, 90}};
				for (float[] look : looks) {
					ctx.runOnClient(client -> {
						client.player.setYRot(look[0]);
						client.player.setXRot(look[1]);
					});
					ctx.waitTicks(30);
				}
				ctx.runOnClient(client -> {
					client.player.setYRot(0);
					client.player.setXRot(0);
				});
				conn.waitForChunksRender();
				ctx.waitTicks(60);
				Path warmUp = Files.createTempDirectory("nether-rifts-panorama-warmup");
				ctx.runOnClient(client -> client.grabPanoramixScreenshot(warmUp.toFile()));
				ctx.waitTicks(100);
				conn.waitForChunksRender();
				Path folder = Files.createTempDirectory("nether-rifts-panorama");
				ctx.runOnClient(client -> client.grabPanoramixScreenshot(folder.toFile()));
				ctx.waitTicks(40);
				Files.createDirectories(out);
				for (int i = 0; i < 6; i++) {
					Path face = folder.resolve("screenshots/panorama_" + i + ".png");
					ctx.waitFor(client -> Files.exists(face), 20 * 30);
					ctx.waitTicks(10);
					Files.copy(face, out.resolve("panorama_" + i + ".png"), StandardCopyOption.REPLACE_EXISTING);
				}
				System.out.println("[nether-rifts-clienttest] panorama written to " + out);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * The best open spot near the closest crimson forest: nothing solid within {@link #CLEAR} blocks of the
	 * camera, lava straight below within {@link #LAVA_BELOW}, scored by how much scenery is in view.
	 */
	private static BlockPos findSpot(ServerLevel nether) {
		var forest = nether.findClosestBiome3d(b -> b.is(Biomes.CRIMSON_FOREST), new BlockPos(0, 64, 0), 3000, 32, 64);
		BlockPos centre = forest == null ? new BlockPos(0, 64, 0) : forest.getFirst();
		System.out.println("[nether-rifts-clienttest] crimson forest near " + centre.toShortString());
		BlockPos best = null;
		int bestScore = -1;
		for (int dx = -64; dx <= 64; dx += 4) {
			for (int dz = -64; dz <= 64; dz += 4) {
				for (int y = 40; y <= 100; y += 2) {
					var at = new BlockPos(centre.getX() + dx, y, centre.getZ() + dz);
					if (!clear(nether, at, CLEAR) || !lavaBelow(nether, at)) {
						continue;
					}
					int score = scenery(nether, at) + (nether.getBiome(at).is(Biomes.CRIMSON_FOREST) || nether.getBiome(at).is(Biomes.WARPED_FOREST) ? 200 : 0);
					if (score > bestScore) {
						bestScore = score;
						best = at;
					}
				}
			}
		}
		if (best == null) {
			throw new AssertionError("no open spot with lava below near " + centre.toShortString());
		}
		System.out.println("[nether-rifts-clienttest] best spot score=" + bestScore + " biome=" + nether.getBiome(best).getRegisteredName());
		return best;
	}

	private static boolean clear(ServerLevel level, BlockPos at, int r) {
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (!level.getBlockState(at.offset(x, y, z)).isAir()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static boolean lavaBelow(ServerLevel level, BlockPos at) {
		for (int d = 1; d <= LAVA_BELOW; d++) {
			var state = level.getBlockState(at.below(d));
			if (!state.isAir()) {
				return state.getFluidState().is(FluidTags.LAVA);
			}
		}
		return false;
	}

	/**
	 * Scenery in view: of 48 rays (16 round the horizon, 16 tilted up and 16 down by 30°), the ones that hit
	 * something between 10 and 40 blocks out, near enough to see through the fog and far enough to look like
	 * a place. A cavern so big every ray runs out shows nothing but fog.
	 */
	private static int scenery(ServerLevel level, BlockPos at) {
		int n = 0;
		for (int tilt = -1; tilt <= 1; tilt++) {
			for (int i = 0; i < 16; i++) {
				double yaw = i * Math.PI / 8;
				double pitch = tilt * Math.PI / 6;
				double dx = Math.cos(yaw) * Math.cos(pitch), dy = Math.sin(pitch), dz = Math.sin(yaw) * Math.cos(pitch);
				for (int d = 1; d <= 40; d++) {
					var p = BlockPos.containing(at.getX() + 0.5 + dx * d, at.getY() + 1.6 + dy * d, at.getZ() + 0.5 + dz * d);
					if (!level.getBlockState(p).isAir()) {
						if (d >= 10) {
							n += 10;
						}
						break;
					}
				}
			}
		}
		return n;
	}
}
