package nu.metacraft.deploy;

import groovy.json.JsonSlurper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackJarTest {
    @TempDir
    Path tmp;

    private static final String SHA = "ab".repeat(32);
    private static final URI SOURCE = URI.create("https://example.org/pack.zip");

    private Map<String, String> pack(Path zip, URI source) throws IOException {
        Path jar = tmp.resolve("out.jar");
        DatapackJar.pack(zip, "my_pack", SHA, source, jar);
        Map<String, String> files = new TreeMap<>();
        try (ZipFile in = new ZipFile(jar.toFile())) {
            for (ZipEntry e : in.stream().toList()) {
                files.put(e.getName(), e.isDirectory() ? null : new String(in.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @Test
    void putsThePackAtTheRootWithAFabricModJson() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"), "{\"pack\":{\"pack_format\":81}}",
                "data/my/function/hi.mcfunction", "say hi", "fabric.mod.json", "{\"id\":\"someone_else\"}");
        Map<String, String> files = pack(zip, SOURCE);

        assertEquals("say hi", files.get("data/my/function/hi.mcfunction"));
        assertTrue(files.containsKey("data/my/function/"), "directories are entries too");
        Map<?, ?> json = (Map<?, ?>) new JsonSlurper().parseText(files.get("fabric.mod.json"));
        assertEquals("my_pack", json.get("id"));
        assertEquals(SHA.substring(0, 12), json.get("version"));
        assertNull(json.get("custom"), "no assets, so nothing for Polymer");
        assertFalse(files.containsKey("credits.txt"));
    }

    @Test
    void appliesTheOverlaysForThePacksNewestFormat() throws IOException {
        String mcmeta = """
                {"pack": {"min_format": [100, 0], "max_format": [121, 0]},
                 "overlays": {"entries": [
                   {"directory": "old", "min_format": 100, "max_format": [107, 1]},
                   {"directory": "now", "formats": {"min_inclusive": 108, "max_inclusive": 121}},
                   {"directory": "later", "min_format": [121, 0], "max_format": [121, 0]}]}}""";
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"), mcmeta,
                "data/x/a.json", "base", "data/x/b.json", "base",
                "old/data/x/a.json", "old", "now/data/x/a.json", "now", "now/data/x/b.json", "now",
                "later/data/x/b.json", "later");
        Map<String, String> files = pack(zip, SOURCE);

        assertEquals("now", files.get("data/x/a.json"));
        assertEquals("later", files.get("data/x/b.json"), "a later overlay wins");
        assertTrue(files.keySet().stream().noneMatch(n -> n.startsWith("old/") || n.startsWith("now/") || n.startsWith("later/")),
                files.keySet().toString());
    }

    @Test
    void marksAPackWithAssetsForPolymer() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"), "{\"pack\":{\"pack_format\":81}}",
                "assets/my/textures/item/x.png", "png");
        Map<?, ?> json = (Map<?, ?>) new JsonSlurper().parseText(pack(zip, SOURCE).get("fabric.mod.json"));
        assertEquals(Map.of("polymer:resource_pack_include", true), json.get("custom"));
    }

    @Test
    void creditsVanillaTweaks() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"), "{\"pack\":{\"pack_format\":81}}");
        Map<String, String> files = pack(zip, URI.create("vanillatweaks:26.3/a/b"));
        assertEquals(VanillaTweaks.CREDITS, files.get("credits.txt"));
    }

    @Test
    void theSameZipGivesTheSameBytes() throws IOException {
        Path zip = TestJars.datapackZip(tmp.resolve("p.zip"), "{\"pack\":{\"pack_format\":81}}", "data/x/a.json", "{}");
        DatapackJar.pack(zip, "my_pack", SHA, SOURCE, tmp.resolve("1.jar"));
        DatapackJar.pack(zip, "my_pack", SHA, SOURCE, tmp.resolve("2.jar"));
        assertEquals(Sha256.of(tmp.resolve("1.jar")), Sha256.of(tmp.resolve("2.jar")));
    }

    @Test
    void refusesAZipWithoutPackMcmeta() throws IOException {
        Path zip = TestJars.modJar(tmp.resolve("p.zip"), "not_a_pack", "1");
        DeployException e = assertThrows(DeployException.class, () -> pack(zip, SOURCE));
        assertTrue(e.getMessage().contains("has no pack.mcmeta at its root"), e.getMessage());
    }
}
