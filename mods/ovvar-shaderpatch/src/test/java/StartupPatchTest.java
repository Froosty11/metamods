import metacraft.ovvar.shaderpatcher.Patcher;
import metacraft.ovvar.shaderpatcher.PackPatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.metacraft.ovvarshaderpatch.StartupPatch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the launcher mod does to a game directory at startup. */
public class StartupPatchTest {
	private static final Path FIXTURE = Paths.get("../ovvar/tools/shaderpatcher/fixtures/split-programs/in");

	@TempDir
	Path game;
	Path packs;
	Path original;
	Path copy;

	@BeforeEach
	void setUp() throws IOException {
		packs = Files.createDirectories(game.resolve("shaderpacks"));
		original = packs.resolve("bsl.zip");
		copy = packs.resolve("bsl+ovvar.zip");
		Patcher.writeZip(original, Patcher.readFolder(FIXTURE));
	}

	private Map<String, byte[]> copyFiles() throws IOException {
		return Patcher.readZip(copy);
	}

	@Test
	void patchesNewPack() throws IOException {
		StartupPatch.run(game);
		assertTrue(Files.isRegularFile(copy));
		String stamp = new String(copyFiles().get(PackPatch.STAMP_FILE), StandardCharsets.UTF_8);
		assertEquals("stamp=" + PackPatch.stamp() + "\n", stamp);
	}

	@Test
	void skipsCurrentCopy() throws IOException {
		StartupPatch.run(game);
		FileTime old = FileTime.fromMillis(1_000_000_000_000L);
		Files.setLastModifiedTime(copy, old);
		StartupPatch.run(game);
		assertEquals(old, Files.getLastModifiedTime(copy));
	}

	@Test
	void rewritesStaleCopy() throws IOException {
		StartupPatch.run(game);
		Map<String, byte[]> files = new LinkedHashMap<>(copyFiles());
		files.put(PackPatch.STAMP_FILE, "stamp=00000000\n".getBytes(StandardCharsets.UTF_8));
		Patcher.writeZip(copy, files);
		StartupPatch.run(game);
		String stamp = new String(copyFiles().get(PackPatch.STAMP_FILE), StandardCharsets.UTF_8);
		assertEquals("stamp=" + PackPatch.stamp() + "\n", stamp);
	}

	@Test
	void leavesOriginalAlone() throws IOException {
		byte[] before = Files.readAllBytes(original);
		StartupPatch.run(game);
		assertArrayEquals(before, Files.readAllBytes(original));
	}

	@Test
	void patchesFolderPack() throws IOException {
		Path folder = packs.resolve("folderpack");
		Patcher.writeFolder(folder, Patcher.readFolder(FIXTURE));
		StartupPatch.run(game);
		assertTrue(Files.isRegularFile(packs.resolve("folderpack+ovvar").resolve(PackPatch.STAMP_FILE)));
	}

	@Test
	void switchesIris() throws IOException {
		Path config = Files.createDirectories(game.resolve("config"));
		Path props = config.resolve("iris.properties");
		Files.writeString(props, "enableShaders=true\nshaderPack=bsl.zip\n");
		StartupPatch.run(game);
		assertEquals("enableShaders=true\nshaderPack=bsl+ovvar.zip\n", Files.readString(props));
	}

	@Test
	void leavesIrisAloneWithoutCopy() throws IOException {
		Path config = Files.createDirectories(game.resolve("config"));
		Path props = config.resolve("iris.properties");
		Files.writeString(props, "shaderPack=other.zip\n");
		StartupPatch.run(game);
		assertEquals("shaderPack=other.zip\n", Files.readString(props));
	}

	@Test
	void ignoresNonShaderpacks() throws IOException {
		Map<String, byte[]> resourcePack = new LinkedHashMap<>();
		resourcePack.put("pack.mcmeta", "{}".getBytes(StandardCharsets.UTF_8));
		Patcher.writeZip(packs.resolve("faithful.zip"), resourcePack);
		StartupPatch.run(game);
		assertFalse(Files.exists(packs.resolve("faithful+ovvar.zip")));
	}

	@Test
	void neverThrows() throws IOException {
		Path odd = Files.createTempDirectory(game, "odd");
		Files.writeString(odd.resolve("shaderpacks"), "not a folder");
		List<String> log = StartupPatch.run(odd);
		assertFalse(log.isEmpty());
		// and a game dir that does not exist
		assertNotEquals(null, StartupPatch.run(game.resolve("missing")));
	}
}
