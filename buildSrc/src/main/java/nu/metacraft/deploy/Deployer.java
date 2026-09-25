package nu.metacraft.deploy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Deploys a set to a server: checks autodeploy can delete, uploads what differs from the
 * server's manifest, lists removals in remove.txt, and writes the manifest last so an
 * interrupted deploy is retried in full.
 */
public final class Deployer {
    /** The first FabricModsUpdate version that handles remove.txt. */
    public static final String MIN_AUTODEPLOY_VERSION = "1.1";
    public static final String UPDATE_DIR = "mods/update";
    public static final String MARKER = UPDATE_DIR + "/.autodeploy-version";
    public static final String REMOVE_FILE = UPDATE_DIR + "/remove.txt";
    public static final String REMOTE_MANIFEST = "mods/metacraft-deploy.json";

    public record Report(String server, boolean firstDeploy, boolean dryRun, SortedSet<String> uploaded,
                         SortedSet<String> removed, SortedSet<String> unchanged, Manifest manifest) {}

    private Deployer() {
    }

    public static Report deploy(String server, Path deployDir, RemoteFiles remote, boolean dryRun) throws IOException {
        checkAutodeploy(server, remote);
        Manifest now = Manifest.read(deployDir.resolve("manifest.json"));
        checkLocalJars(deployDir, now);

        Optional<byte[]> oldBytes = remote.read(REMOTE_MANIFEST);
        boolean first = oldBytes.isEmpty();
        Manifest old = first ? Manifest.empty() : parseServerManifest(server, oldBytes.get());
        ManifestDiff diff = ManifestDiff.of(old, now);

        // Removals still pending from deploys the server has not restarted for stay listed.
        SortedSet<String> remove = new TreeSet<>(diff.remove());
        remote.read(REMOVE_FILE).ifPresent(bytes -> remove.addAll(ids(bytes)));
        if (first) {
            remove.add(DeployList.BUNDLE_ID);
        }
        remove.removeAll(now.entries().keySet());

        if (!dryRun) {
            deleteStaleUploads(remote, old, now);
            for (String id : diff.upload()) {
                String file = now.entries().get(id).file();
                remote.upload(UPDATE_DIR + "/" + file, deployDir.resolve(file));
            }
            if (remove.isEmpty()) {
                remote.delete(REMOVE_FILE);
            } else {
                remote.write(REMOVE_FILE, (String.join("\n", remove) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            remote.write(REMOTE_MANIFEST, now.toJson().getBytes(StandardCharsets.UTF_8));
        }
        return new Report(server, first, dryRun, diff.upload(), Collections.unmodifiableSortedSet(remove),
                diff.unchanged(), now);
    }

    private static void checkAutodeploy(String server, RemoteFiles remote) throws IOException {
        Optional<String> version = remote.read(MARKER).map(b -> new String(b, StandardCharsets.UTF_8).strip());
        if (version.isEmpty() || compareVersions(version.get(), MIN_AUTODEPLOY_VERSION) < 0) {
            throw new DeployException("update autodeploy.jar on " + server + " first (FabricModsUpdate v"
                    + MIN_AUTODEPLOY_VERSION + "); nothing was uploaded ("
                    + version.map(v -> "it runs " + v).orElse("no " + MARKER) + ")");
        }
    }

    private static void checkLocalJars(Path deployDir, Manifest now) throws IOException {
        for (Map.Entry<String, Manifest.Entry> e : now.entries().entrySet()) {
            Path jar = deployDir.resolve(e.getValue().file());
            if (!Files.isRegularFile(jar) || !Sha256.of(jar).equals(e.getValue().sha256())) {
                throw new DeployException(jar + " (" + e.getKey() + ") does not match manifest.json; nothing was uploaded");
            }
        }
    }

    private static Manifest parseServerManifest(String server, byte[] bytes) {
        try {
            return Manifest.parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (DeployException e) {
            throw new DeployException(REMOTE_MANIFEST + " on " + server + " is unreadable (" + e.getMessage()
                    + "); delete it to redeploy everything; nothing was uploaded", e);
        }
    }

    /** Jars an earlier deploy put in mods/update that this deploy replaces under another name or removes. */
    private static void deleteStaleUploads(RemoteFiles remote, Manifest old, Manifest now) throws IOException {
        for (Map.Entry<String, Manifest.Entry> e : old.entries().entrySet()) {
            Manifest.Entry next = now.entries().get(e.getKey());
            if (next == null || !next.file().equals(e.getValue().file())) {
                remote.delete(UPDATE_DIR + "/" + e.getValue().file());
            }
        }
    }

    private static SortedSet<String> ids(byte[] removeTxt) {
        SortedSet<String> ids = new TreeSet<>();
        for (String line : new String(removeTxt, StandardCharsets.UTF_8).split("\n")) {
            String id = line.strip();
            if (!id.isEmpty() && !id.startsWith("#")) {
                ids.add(id);
            }
        }
        return ids;
    }

    static int compareVersions(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? leadingInt(x[i]) : 0;
            int q = i < y.length ? leadingInt(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static int leadingInt(String part) {
        int n = 0;
        for (int i = 0; i < part.length() && i < 9 && Character.isDigit(part.charAt(i)); i++) {
            n = n * 10 + (part.charAt(i) - '0');
        }
        return n;
    }
}
