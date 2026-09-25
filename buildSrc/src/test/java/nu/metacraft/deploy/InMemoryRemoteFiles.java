package nu.metacraft.deploy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/** A server's files in memory; {@link #writes} records every write in order. */
final class InMemoryRemoteFiles implements RemoteFiles {
    final SortedMap<String, byte[]> files = new TreeMap<>();
    final List<String> writes = new ArrayList<>();

    @Override
    public Optional<byte[]> read(String path) {
        return Optional.ofNullable(files.get(path)).map(byte[]::clone);
    }

    @Override
    public void write(String path, byte[] data) {
        files.put(path, data.clone());
        writes.add(path);
    }

    @Override
    public boolean delete(String path) {
        return files.remove(path) != null;
    }

    @Override
    public void close() {
    }

    void put(String path, String text) {
        files.put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    String text(String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    SortedSet<String> jarsIn(String dir) {
        SortedSet<String> jars = new TreeSet<>();
        for (String path : files.keySet()) {
            String rest = path.startsWith(dir + "/") ? path.substring(dir.length() + 1) : null;
            if (rest != null && !rest.contains("/") && rest.endsWith(".jar")) {
                jars.add(rest);
            }
        }
        return jars;
    }
}
