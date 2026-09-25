package nu.metacraft.deploy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static nu.metacraft.deploy.Deployer.MARKER;
import static nu.metacraft.deploy.Deployer.REMOTE_MANIFEST;
import static nu.metacraft.deploy.Deployer.REMOVE_FILE;
import static nu.metacraft.deploy.Deployer.UPDATE_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployerTest {
    @TempDir
    Path tmp;
    InMemoryRemoteFiles remote;

    @BeforeEach
    void server() {
        remote = new InMemoryRemoteFiles();
        remote.put(MARKER, "1.1\n");
        remote.put("mods/fabric-api.jar", "hand-managed");
    }

    /** A deploy dir with one jar per id; {@code versions} maps mod id to version. */
    private Path set(String name, Map<String, String> versions) throws IOException {
        Path dir = tmp.resolve(name);
        SortedMap<String, Manifest.Entry> entries = new TreeMap<>();
        for (Map.Entry<String, String> e : versions.entrySet()) {
            Path jar = TestJars.modJar(dir.resolve(e.getKey() + "-" + e.getValue() + ".jar"), e.getKey(), e.getValue());
            entries.put(e.getKey(), new Manifest.Entry(jar.getFileName().toString(), Sha256.of(jar), e.getValue(), e.getKey()));
        }
        new Manifest(entries).write(dir.resolve("manifest.json"));
        return dir;
    }

    private static Map<String, String> mods(String... idVersion) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < idVersion.length; i += 2) {
            m.put(idVersion[i], idVersion[i + 1]);
        }
        return m;
    }

    private void alreadyDeployed(Path dir) throws IOException {
        remote.files.put(REMOTE_MANIFEST, Manifest.read(dir.resolve("manifest.json")).toJson().getBytes());
    }

    @Test
    void refusesWithoutMarkerAndWritesNothing() throws IOException {
        remote.files.remove(MARKER);
        Path set = set("s", mods("a", "1"));
        DeployException e = assertThrows(DeployException.class, () -> Deployer.deploy("test", set, remote, false));
        assertTrue(e.getMessage().startsWith("update autodeploy.jar on test first (FabricModsUpdate v1.1); nothing was uploaded"), e.getMessage());
        assertEquals(List.of(), remote.writes);
    }

    @Test
    void refusesOlderAutodeploy() throws IOException {
        remote.put(MARKER, "1.0\n");
        Path set = set("s", mods("a", "1"));
        assertThrows(DeployException.class, () -> Deployer.deploy("test", set, remote, false));
        remote.put(MARKER, "garbage");
        assertThrows(DeployException.class, () -> Deployer.deploy("test", set, remote, false));
        assertEquals(List.of(), remote.writes);
    }

    @Test
    void acceptsNewerAutodeploy() throws IOException {
        remote.put(MARKER, "1.10\n");
        Deployer.deploy("test", set("s", mods("a", "1")), remote, false);
        assertTrue(remote.files.containsKey(REMOTE_MANIFEST));
    }

    @Test
    void firstDeployUploadsEverythingAndRemovesTheBundle() throws IOException {
        Path set = set("s", mods("a", "1", "b", "1"));
        Deployer.Report report = Deployer.deploy("test", set, remote, false);
        assertTrue(report.firstDeploy());
        assertEquals(Set.of("a-1.jar", "b-1.jar"), remote.jarsIn(UPDATE_DIR));
        assertEquals("metacraft\n", remote.text(REMOVE_FILE));
        assertEquals(Manifest.read(set.resolve("manifest.json")).toJson(), remote.text(REMOTE_MANIFEST));
        assertEquals(REMOTE_MANIFEST, remote.writes.get(remote.writes.size() - 1), "manifest is written last");
        assertEquals("hand-managed", remote.text("mods/fabric-api.jar"));
        assertEquals(Set.of("a", "b"), report.uploaded());
        assertEquals(Set.of("metacraft"), report.removed());
    }

    @Test
    void anUnchangedSetUploadsNothing() throws IOException {
        Path set = set("s", mods("a", "1", "b", "1"));
        alreadyDeployed(set);
        Deployer.Report report = Deployer.deploy("test", set, remote, false);
        assertEquals(List.of(REMOTE_MANIFEST), remote.writes);
        assertFalse(remote.files.containsKey(REMOVE_FILE));
        assertEquals(Set.of("a", "b"), report.unchanged());
        assertEquals(Set.of(), report.uploaded());
    }

    @Test
    void aChangedModUploadsOnlyThatJar() throws IOException {
        alreadyDeployed(set("old", mods("a", "1", "b", "1")));
        Deployer.Report report = Deployer.deploy("test", set("new", mods("a", "1", "b", "2")), remote, false);
        assertEquals(Set.of("b-2.jar"), remote.jarsIn(UPDATE_DIR));
        assertEquals(Set.of("b"), report.uploaded());
    }

    @Test
    void aModTakenOffTheListGoesInRemoveTxt() throws IOException {
        alreadyDeployed(set("old", mods("a", "1", "b", "1")));
        Deployer.Report report = Deployer.deploy("test", set("new", mods("a", "1")), remote, false);
        assertEquals("b\n", remote.text(REMOVE_FILE));
        assertEquals(Set.of("b"), report.removed());
    }

    @Test
    void secondDeployBeforeRestartKeepsPendingRemovals() throws IOException {
        alreadyDeployed(set("old", mods("a", "1", "b", "1")));
        remote.put(REMOVE_FILE, "metacraft\n");
        Deployer.deploy("test", set("new", mods("a", "1")), remote, false);
        assertEquals("b\nmetacraft\n", remote.text(REMOVE_FILE));
    }

    @Test
    void readdedModIsDroppedFromPendingRemovals() throws IOException {
        alreadyDeployed(set("old", mods("a", "1")));
        remote.put(REMOVE_FILE, "b\n");
        Deployer.deploy("test", set("new", mods("a", "1", "b", "1")), remote, false);
        assertFalse(remote.files.containsKey(REMOVE_FILE));
        assertEquals(Set.of("b-1.jar"), remote.jarsIn(UPDATE_DIR));
    }

    @Test
    void staleUploadFromAnUnrestartedDeployIsDeleted() throws IOException {
        alreadyDeployed(set("old", mods("a", "1", "b", "2", "c", "1")));
        remote.put(UPDATE_DIR + "/b-2.jar", "waiting for a restart");
        remote.put(UPDATE_DIR + "/c-1.jar", "waiting for a restart");
        Deployer.deploy("test", set("new", mods("a", "1", "b", "3")), remote, false);
        assertEquals(Set.of("b-3.jar"), remote.jarsIn(UPDATE_DIR));
        assertEquals("c\n", remote.text(REMOVE_FILE));
    }

    @Test
    void interruptedDeployThenDifferentSetLeavesNoOrphans() throws IOException {
        // Deploy 2 (b-2, plus a new c-1) uploaded its jars then died before writing the manifest,
        // so the server is still on M1 (b-1) with b-2.jar and c-1.jar sitting in mods/update.
        alreadyDeployed(set("m1", mods("b", "1")));
        remote.put(UPDATE_DIR + "/b-2.jar", "left by an interrupted deploy");
        remote.put(UPDATE_DIR + "/c-1.jar", "left by an interrupted deploy");
        Deployer.deploy("test", set("m3", mods("b", "3")), remote, false);
        assertEquals(Set.of("b-3.jar"), remote.jarsIn(UPDATE_DIR));
    }

    @Test
    void pendingJarOfTheSameSetIsReuploaded() throws IOException {
        Path set = set("s", mods("a", "1", "b", "1"));
        alreadyDeployed(set);
        remote.put(UPDATE_DIR + "/b-1.jar", "leftover from an earlier attempt, server manifest agrees");
        Deployer.Report report = Deployer.deploy("test", set, remote, false);
        assertEquals(Set.of("b"), report.uploaded());
        assertEquals(Set.of("a"), report.unchanged());
    }

    @Test
    void aJarNotFromTheManifestInUpdateIsDeleted() throws IOException {
        Path set = set("s", mods("a", "1"));
        remote.put(UPDATE_DIR + "/orphan-1.jar", "not in any manifest");
        Deployer.deploy("test", set, remote, false);
        assertEquals(Set.of("a-1.jar"), remote.jarsIn(UPDATE_DIR));
    }

    @Test
    void refusesWhenALocalJarDoesNotMatchTheManifest() throws IOException {
        Path set = set("s", mods("a", "1"));
        TestJars.modJar(set.resolve("a-1.jar"), "a", "tampered");
        DeployException e = assertThrows(DeployException.class, () -> Deployer.deploy("test", set, remote, false));
        assertTrue(e.getMessage().contains("does not match manifest.json"), e.getMessage());
        assertEquals(List.of(), remote.writes);
    }

    @Test
    void anUnreadableServerManifestSaysWhatToDo() throws IOException {
        remote.put(REMOTE_MANIFEST, "{not json");
        Path set = set("s", mods("a", "1"));
        DeployException e = assertThrows(DeployException.class, () -> Deployer.deploy("test", set, remote, false));
        assertTrue(e.getMessage().contains("delete it to redeploy everything"), e.getMessage());
        assertEquals(List.of(), remote.writes);
    }

    @Test
    void dryRunWritesNothing() throws IOException {
        Deployer.Report report = Deployer.deploy("test", set("s", mods("a", "1")), remote, true);
        assertEquals(List.of(), remote.writes);
        assertTrue(report.dryRun());
        assertEquals(Set.of("a"), report.uploaded());
        assertEquals(Set.of("metacraft"), report.removed());
    }

    @Test
    void comparesVersionsNumerically() {
        assertTrue(Deployer.compareVersions("1.10", "1.1") > 0);
        assertEquals(0, Deployer.compareVersions("1.1", "1.1.0"));
        assertTrue(Deployer.compareVersions("1.0", "1.1") < 0);
        assertTrue(Deployer.compareVersions("2", "1.9") > 0);
    }
}
