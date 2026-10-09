package nu.metacraft.deploy;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Packs a datapack zip into a Fabric mod jar, the way datapacks published "as a mod" are: the pack's
 * {@code data/} and {@code assets/} at the jar's root, which Fabric loads as the mod's own resources
 * on every world. A jar goes through the deploy like any other, so a datapack is added, updated and
 * removed with its line in {@code deploy/<server>.txt}, and survives a fresh world.
 * <p>
 * Fabric reads a mod's resources without the pack's overlays, so the overlays that apply to the
 * pack's newest format are copied over its base files here, in the order the pack lists them (a later
 * overlay wins, as in Minecraft). A pack with {@code assets/} is marked for Polymer's resource pack.
 * <p>
 * The jar's bytes depend only on the zip's contents, so an unchanged pack is never uploaded again.
 */
public final class DatapackJar {
    /** Every entry's time: the earliest a zip can hold, as Gradle's reproducible archives use. */
    private static final LocalDateTime TIME = LocalDateTime.of(1980, 2, 1, 0, 0);

    private DatapackJar() {
    }

    /** @return the jar's mod version: the first 12 hex digits of the zip's sha256 */
    public static String version(String zipSha256) {
        return zipSha256.substring(0, 12);
    }

    public static void pack(Path zip, String modId, String zipSha256, URI source, Path jar) throws IOException {
        SortedMap<String, byte[]> files = new TreeMap<>();
        try (ZipFile in = new ZipFile(zip.toFile())) {
            Map<String, byte[]> all = new LinkedHashMap<>();
            for (ZipEntry entry : in.stream().toList()) {
                if (!entry.isDirectory()) {
                    try (InputStream data = in.getInputStream(entry)) {
                        all.put(entry.getName(), data.readAllBytes());
                    }
                }
            }
            byte[] mcmeta = all.get("pack.mcmeta");
            if (mcmeta == null) {
                throw new DeployException(source + " has no pack.mcmeta at its root; it is not a datapack zip");
            }
            List<String> overlayDirs = new ArrayList<>();
            List<String> applied = overlaysFor(source, mcmeta, overlayDirs);
            for (Map.Entry<String, byte[]> e : all.entrySet()) {
                if (overlayDirs.stream().noneMatch(dir -> e.getKey().startsWith(dir + "/"))) {
                    files.put(e.getKey(), e.getValue());
                }
            }
            for (String dir : applied) {
                for (Map.Entry<String, byte[]> e : all.entrySet()) {
                    if (e.getKey().startsWith(dir + "/")) {
                        files.put(e.getKey().substring(dir.length() + 1), e.getValue());
                    }
                }
            }
        }
        files.remove("fabric.mod.json");
        files.remove("quilt.mod.json");
        files.put("fabric.mod.json", fabricModJson(modId, zipSha256, source, files.keySet().stream()
                .anyMatch(name -> name.startsWith("assets/"))));
        if (VanillaTweaks.is(source)) {
            files.put("credits.txt", VanillaTweaks.CREDITS.getBytes(StandardCharsets.UTF_8));
        }
        write(files, jar);
    }

    private static byte[] fabricModJson(String modId, String zipSha256, URI source, boolean assets) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schemaVersion", 1);
        json.put("id", modId);
        json.put("version", version(zipSha256));
        json.put("name", modId + " (datapack)");
        json.put("description", "The datapack " + source + " (sha256 " + zipSha256 + "), packed as a mod by the METAmods deploy.");
        json.put("environment", "*");
        if (assets) {
            json.put("custom", Map.of("polymer:resource_pack_include", true));
        }
        return JsonOutput.prettyPrint(JsonOutput.toJson(json)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Fills {@code allDirs} with every overlay directory and returns those that apply, in order: the
     * ones whose formats include the pack's newest.
     */
    private static List<String> overlaysFor(URI source, byte[] mcmeta, List<String> allDirs) {
        Object json;
        try {
            json = new JsonSlurper().parse(mcmeta, "UTF-8");
        } catch (RuntimeException e) {
            throw new DeployException(source + ": pack.mcmeta is not valid JSON: " + e.getMessage(), e);
        }
        if (!(json instanceof Map<?, ?> root) || !(root.get("pack") instanceof Map<?, ?> pack)) {
            throw new DeployException(source + ": pack.mcmeta has no \"pack\"");
        }
        List<String> applied = new ArrayList<>();
        if (!(root.get("overlays") instanceof Map<?, ?> overlays) || !(overlays.get("entries") instanceof List<?> entries)) {
            return applied;
        }
        int[] newest = newestFormat(source, pack);
        for (Object o : entries) {
            if (!(o instanceof Map<?, ?> entry) || !(entry.get("directory") instanceof String dir)) {
                throw new DeployException(source + ": an overlay in pack.mcmeta has no \"directory\"");
            }
            allDirs.add(dir);
            int[][] range = range(source, entry);
            if (compare(range[0], newest) <= 0 && compare(newest, range[1]) <= 0) {
                applied.add(dir);
            }
        }
        return applied;
    }

    private static int[] newestFormat(URI source, Map<?, ?> pack) {
        if (pack.containsKey("max_format") || pack.containsKey("supported_formats")) {
            return range(source, pack)[1];
        }
        return format(source, pack.get("pack_format"));
    }

    /**
     * The inclusive [min, max] formats of a pack or overlay: {@code min_format}/{@code max_format}
     * (from 1.21.9), else {@code supported_formats} or {@code formats} (a number, {@code [min, max]}
     * or {@code {min_inclusive, max_inclusive}}).
     */
    private static int[][] range(URI source, Map<?, ?> map) {
        if (map.containsKey("min_format") || map.containsKey("max_format")) {
            Object min = map.containsKey("min_format") ? map.get("min_format") : map.get("max_format");
            Object max = map.containsKey("max_format") ? map.get("max_format") : map.get("min_format");
            return new int[][]{format(source, min), format(source, max)};
        }
        Object formats = map.containsKey("supported_formats") ? map.get("supported_formats") : map.get("formats");
        if (formats instanceof Number) {
            return new int[][]{format(source, formats), format(source, formats)};
        }
        if (formats instanceof List<?> list && list.size() == 2) {
            return new int[][]{format(source, list.get(0)), format(source, list.get(1))};
        }
        if (formats instanceof Map<?, ?> bounds) {
            return new int[][]{format(source, bounds.get("min_inclusive")), format(source, bounds.get("max_inclusive"))};
        }
        throw new DeployException(source + ": pack.mcmeta gives no formats in " + map);
    }

    /** A format: a number (its major) or {@code [major, minor]}. */
    private static int[] format(URI source, Object value) {
        if (value instanceof Number n) {
            return new int[]{n.intValue(), 0};
        }
        if (value instanceof List<?> list && !list.isEmpty() && list.size() <= 2 && list.stream().allMatch(Number.class::isInstance)) {
            return new int[]{((Number) list.get(0)).intValue(), list.size() == 2 ? ((Number) list.get(1)).intValue() : 0};
        }
        throw new DeployException(source + ": '" + value + "' in pack.mcmeta is not a pack format");
    }

    private static int compare(int[] a, int[] b) {
        return a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]);
    }

    /** Sorted entries, with their directories, all at {@link #TIME}. */
    private static void write(SortedMap<String, byte[]> files, Path jar) throws IOException {
        SortedMap<String, byte[]> entries = new TreeMap<>(files);
        for (String name : files.keySet()) {
            for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
                entries.putIfAbsent(name.substring(0, slash + 1), null);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTimeLocal(TIME);
                out.putNextEntry(entry);
                if (e.getValue() != null) {
                    out.write(e.getValue());
                }
                out.closeEntry();
            }
        }
        try (OutputStream file = Files.newOutputStream(jar)) {
            bytes.writeTo(file);
        }
    }
}
