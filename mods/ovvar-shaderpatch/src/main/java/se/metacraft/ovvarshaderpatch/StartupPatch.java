package se.metacraft.ovvarshaderpatch;

import metacraft.ovvar.shaderpatcher.PackPatch;
import metacraft.ovvar.shaderpatcher.Patcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Writes a {@code +ovvar} copy of every shaderpack in {@code <game dir>/shaderpacks} that lacks a current one,
 * and points Iris at the copy of the pack it has selected. Originals are never touched. Nothing here throws:
 * startup must never fail because of this mod.
 */
public final class StartupPatch {
	private static final String SUFFIX = "+ovvar";
	private static final String PREFIX = "[ovvar-shaderpatch] ";

	private StartupPatch() {
	}

	/** Runs the patch and returns the lines it also wrote to the game log. */
	public static List<String> run(Path gameDir) {
		List<String> log = new ArrayList<>();
		try {
			Path packs = gameDir.resolve("shaderpacks");
			if (Files.isDirectory(packs)) {
				List<Path> entries;
				try (Stream<Path> s = Files.list(packs)) {
					entries = s.sorted().toList();
				}
				for (Path pack : entries) {
					try {
						patchPack(pack, log);
					} catch (Throwable t) {
						say(log, pack.getFileName() + ": failed, " + t);
					}
				}
			} else if (Files.exists(packs)) {
				say(log, "shaderpacks is not a folder, nothing to patch");
			}
			switchIris(gameDir.resolve("config").resolve("iris.properties"), packs, log);
		} catch (Throwable t) {
			say(log, "failed, " + t);
		}
		return log;
	}

	private static void say(List<String> log, String line) {
		log.add(line);
		System.out.println(PREFIX + line);
	}

	private static boolean isZip(Path p) {
		return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
	}

	/** The name of the patched copy of a pack. */
	private static String copyName(String name, boolean zip) {
		return zip ? name.substring(0, name.length() - 4) + SUFFIX + ".zip" : name + SUFFIX;
	}

	private static void patchPack(Path pack, List<String> log) throws IOException {
		String name = pack.getFileName().toString();
		boolean zip = isZip(pack);
		if (name.contains(SUFFIX) || name.startsWith(".") || (!zip && !Files.isDirectory(pack))) return;
		if (zip && !Files.isRegularFile(pack)) return;
		Path copy = pack.resolveSibling(copyName(name, zip));
		if (PackPatch.stamp().equals(stampOf(copy))) return;
		PackPatch.Result result = PackPatch.patch(zip ? Patcher.readZip(pack) : Patcher.readFolder(pack));
		if (result.status() != PackPatch.Status.PATCHED) {
			// Not a shaderpack, already a patched copy, or nothing the patcher could do: no copy.
			if (result.status() == PackPatch.Status.NOTHING_PATCHED) {
				say(log, name + ": a shaderpack, but nothing in it could be patched");
			}
			return;
		}
		if (zip) {
			Path tmp = pack.resolveSibling(name + ".ovvar-tmp");
			try {
				Patcher.writeZip(tmp, result.files());
				Files.move(tmp, copy, StandardCopyOption.REPLACE_EXISTING);
			} finally {
				Files.deleteIfExists(tmp);
			}
		} else {
			deleteTree(copy);
			Patcher.writeFolder(copy, result.files());
		}
		say(log, name + " -> " + copy.getFileName());
	}

	/** The stamp of a patched copy (zip or folder), or null if there is none. */
	private static String stampOf(Path copy) {
		try {
			byte[] bytes = null;
			if (Files.isDirectory(copy)) {
				Path f = copy.resolve(PackPatch.STAMP_FILE);
				if (Files.isRegularFile(f)) bytes = Files.readAllBytes(f);
			} else if (Files.isRegularFile(copy)) {
				try (ZipFile z = new ZipFile(copy.toFile())) {
					ZipEntry e = z.getEntry(PackPatch.STAMP_FILE);
					if (e != null) {
						try (InputStream in = z.getInputStream(e)) {
							bytes = in.readAllBytes();
						}
					}
				}
			}
			if (bytes == null) return null;
			for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
				if (line.startsWith("stamp=")) return line.substring("stamp=".length()).trim();
			}
		} catch (IOException | RuntimeException e) {
			// An unreadable copy counts as stale and is rewritten.
		}
		return null;
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (Stream<Path> s = Files.walk(root)) {
			for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
		}
	}

	/** Iris keeps its selection in {@code shaderPack=<name>}; when that names a pack with a current copy, name the copy. */
	private static void switchIris(Path props, Path packs, List<String> log) throws IOException {
		if (!Files.isRegularFile(props)) return;
		String text = Files.readString(props, StandardCharsets.UTF_8);
		StringBuilder out = new StringBuilder();
		boolean changed = false;
		for (String line : text.split("(?<=\n)")) {
			String content = line.stripTrailing();
			String ending = line.substring(content.length());
			if (content.startsWith("shaderPack=")) {
				String current = content.substring("shaderPack=".length());
				if (!current.isEmpty() && !current.contains(SUFFIX)) {
					Path original = packs.resolve(current);
					String copy = copyName(current, isZip(original));
					if ((Files.exists(original)) && PackPatch.stamp().equals(stampOf(packs.resolve(copy)))) {
						out.append("shaderPack=").append(copy).append(ending);
						changed = true;
						say(log, "iris.properties: shaderPack " + current + " -> " + copy);
						continue;
					}
				}
			}
			out.append(line);
		}
		if (changed) Files.writeString(props, out.toString(), StandardCharsets.UTF_8);
	}
}
