package nu.metacraft.seedscan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs in a real server created on the seed (datapack in place before creation): /datapack list, then
 * /locate structure and /locate biome from the border centre given as -Dseedscan.centre=x,z.
 */
final class Verify {
    static final String[] STRUCTURES = {
        "#minecraft:village", "minecraft:trial_chambers", "minecraft:ancient_city", "minecraft:stronghold", "minecraft:mansion",
        "minecraft:monument", "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:pillager_outpost",
        "minecraft:trail_ruins", "minecraft:swamp_hut", "minecraft:igloo", "#minecraft:ocean_ruin"};
    static final String[] BIOMES = {
        "plains", "forest", "birch_forest", "taiga", "dark_forest", "jungle", "bamboo_jungle", "savanna", "mangrove_swamp",
        "cherry_grove", "pale_garden", "desert", "badlands", "eroded_badlands", "wooded_badlands", "snowy_plains", "ice_spikes",
        "snowy_taiga", "swamp", "ocean", "jagged_peaks", "frozen_peaks", "stony_peaks", "meadow", "mushroom_fields",
        "lush_caves", "deep_dark", "dripstone_caves"};
    static final Pattern POS = Pattern.compile("\\[(-?\\d+), (~|-?\\d+), (-?\\d+)\\]");

    static void run(Gen gen, Path out) throws Exception {
        String[] c = System.getProperty("seedscan.centre").split(",");
        int cx = Integer.parseInt(c[0].trim()), cz = Integer.parseInt(c[1].trim());
        long seed = gen.level.getSeed();
        List<String> rows = new ArrayList<>();
        List<String> packs = Cmd.run(gen.server, cx, 80, cz, "datapack list");
        rows.add(String.format(Locale.ROOT, "{\"seed\":%d,\"x\":%d,\"z\":%d,\"datapacks\":\"%s\"}", seed, cx, cz, esc(String.join(" ", packs))));
        for (String s : STRUCTURES) {
            rows.add(locate(gen, cx, cz, "structure", s));
        }
        for (String b : BIOMES) {
            rows.add(locate(gen, cx, cz, "biome", "minecraft:" + b));
        }
        Files.write(out.resolve("verify-" + seed + ".jsonl"), rows);
    }

    private static String locate(Gen gen, int cx, int cz, String kind, String what) {
        long t0 = System.currentTimeMillis();
        List<String> output = Cmd.run(gen.server, cx + 0.5, 64, cz + 0.5, "locate " + kind + " " + what);
        String text = String.join(" ", output);
        Matcher m = POS.matcher(text);
        String pos = "null";
        String cheb = "null";
        if (m.find()) {
            int x = Integer.parseInt(m.group(1)), z = Integer.parseInt(m.group(3));
            pos = "[" + x + "," + z + "]";
            cheb = Integer.toString(Math.max(Math.abs(x - cx), Math.abs(z - cz)));
        }
        SeedScan.LOG.info("verify {} {}: {} ({} ms)", kind, what, text, System.currentTimeMillis() - t0);
        return String.format(Locale.ROOT, "{\"kind\":\"%s\",\"what\":\"%s\",\"pos\":%s,\"chebyshev\":%s,\"text\":\"%s\"}",
            kind, what, pos, cheb, esc(text));
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
