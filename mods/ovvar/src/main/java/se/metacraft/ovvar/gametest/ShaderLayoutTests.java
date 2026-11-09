package se.metacraft.ovvar.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.datagen.GeneratedAssets;
import se.metacraft.ovvar.datagen.Tex;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The base garment layout the shader and an unpatched shaderpack read differently. Every base
 * layer (kind 0) carries its right-limb box twice: copied unchanged into the head rows at
 * ({@link Spot#RIGHT_COPY_X}, box y − {@link Spot#MIRROR_SHIFT}), which is what our shader draws the
 * right limbs from, and in place as a magenta/black checker wherever the copy has cloth, which is
 * what any other shader draws. The left limb's mirror strip is where it always was.
 */
public final class ShaderLayoutTests {
	private static final int D = Spot.DETAIL;
	/** A limb box is 16 skin texels square, and so are both its copies. */
	private static final int BOX = 16 * D;

	/** One generated base layer, with what datagen started it from. */
	private record Layer(Chapter chapter, GeneratedAssets.BaseLayer base, Tex tex) {
		String label() {
			return chapter.id + "/" + base.name();
		}

		int boxX() { return base.box()[0] * D; }

		int boxY() { return base.box()[1] * D; }

		int copyX() { return Spot.RIGHT_COPY_X * D; }

		int copyY() { return (base.box()[1] - Spot.MIRROR_SHIFT) * D; }
	}

	@GameTest
	public void copyHasTheArt(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Layer layer : layers(wrong)) {
			Tex tex = layer.tex();
			Tex copy = tex.crop(layer.copyX(), layer.copyY(), BOX, BOX);
			if (copy.isEmpty()) {
				wrong.add(layer.label() + ": nothing at the right-limb copy (" + Spot.RIGHT_COPY_X + ", " + layer.copyY() / D + ")");
				continue;
			}
			// Copied unchanged: the cut's own right-limb box, texel for texel.
			Tex art = layer.base().cut().scale(D).crop(layer.boxX(), layer.boxY(), BOX, BOX);
			if (!same(copy, art)) wrong.add(layer.label() + ": the right-limb copy is not the right-limb box's art unchanged");
			for (int y = 0; y < BOX; y++) {
				for (int x = 0; x < BOX; x++) {
					boolean inCopy = Tex.a(copy.get(x, y)) > 0, inBox = Tex.a(tex.get(layer.boxX() + x, layer.boxY() + y)) > 0;
					if (inCopy != inBox) {
						wrong.add(layer.label() + ": the copy and the box disagree on cloth at box texel (" + x + ", " + y + ")");
						y = BOX;
						break;
					}
				}
			}
		}
		finish(helper, wrong);
	}

	@GameTest
	public void checkerOnlyUnderCloth(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Layer layer : layers(wrong)) {
			Tex tex = layer.tex();
			scan:
			for (int y = 0; y < BOX; y++) {
				for (int x = 0; x < BOX; x++) {
					int copy = tex.get(layer.copyX() + x, layer.copyY() + y);
					int px = layer.boxX() + x, py = layer.boxY() + y;
					int got = tex.get(px, py);
					int square = Spot.CHECKER_SQUARE * D;
					if (Tex.a(copy) == 0) {
						if (Tex.a(got) != 0) {
							wrong.add(layer.label() + ": box pixel (" + px + ", " + py + ") is " + Integer.toHexString(got)
									+ ", wanted transparent (no cloth in the copy)");
							break scan;
						}
						continue;
					}
					int want = (px / square + py / square) % 2 == 0 ? Spot.CHECKER_A : Spot.CHECKER_B;
					if (got != want) {
						wrong.add(layer.label() + ": box pixel (" + px + ", " + py + ") is " + Integer.toHexString(got)
								+ ", wanted the checker's " + Integer.toHexString(want));
						break scan;
					}
				}
			}
		}
		finish(helper, wrong);
	}

	/**
	 * The left limb's mirror strip is what {@code withLeft} has always made of the chapter's art,
	 * and nothing outside the right-limb box and its copy moved either.
	 */
	@GameTest
	public void leftCopyUntouched(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Layer layer : layers(wrong)) {
			Tex tex = layer.tex(), before = layer.base().leftOnly();
			int lx = layer.boxX(), ly = layer.copyY();
			if (!same(tex.crop(lx, ly, BOX, BOX), before.crop(lx, ly, BOX, BOX))) {
				wrong.add(layer.label() + ": the left limb's mirror strip changed");
			}
			scan:
			for (int y = 0; y < tex.height; y++) {
				for (int x = 0; x < tex.width; x++) {
					if (inside(x, y, layer.boxX(), layer.boxY()) || inside(x, y, layer.copyX(), layer.copyY())) continue;
					if (y == GeneratedAssets.MARKER_Y && x >= GeneratedAssets.LAYER_X) continue;   // the marker row's data texels
					if (tex.get(x, y) != before.get(x, y)) {
						wrong.add(layer.label() + ": pixel (" + x + ", " + y + ") outside the right-limb box and its copy changed");
						break scan;
					}
				}
			}
		}
		finish(helper, wrong);
	}

	@GameTest
	public void glslPinsCopyColumn(GameTestHelper helper) {
		String glsl = resource("/assets/ovvar/shaders/include/ovvar.glsl");
		Matcher m = Pattern.compile("const float OVVAR_RIGHT_COPY_X = ([0-9]+\\.[0-9]*);").matcher(glsl);
		if (!m.find()) {
			helper.fail("ovvar.glsl does not declare `const float OVVAR_RIGHT_COPY_X = <n>.0;`, so the right limbs read the checker");
			return;
		}
		double value = Double.parseDouble(m.group(1));
		if (value != Spot.RIGHT_COPY_X) {
			helper.fail("ovvar.glsl's OVVAR_RIGHT_COPY_X is " + value + ", Spot.RIGHT_COPY_X is " + Spot.RIGHT_COPY_X);
			return;
		}
		helper.succeed();
	}

	// ---- helpers

	/** Every chapter's generated base layers, each checked to be a base garment texture (kind 0). */
	private static List<Layer> layers(List<String> wrong) {
		List<Layer> out = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) {
			for (GeneratedAssets.BaseLayer base : GeneratedAssets.baseLayers(chapter)) {
				Tex tex = generated(base.piece().layer + "/" + chapter.id + "/" + base.name());
				if (tex.get(GeneratedAssets.MARKER_X, GeneratedAssets.MARKER_Y) != GeneratedAssets.MARKER) {
					wrong.add(chapter.id + "/" + base.name() + ": no marker texel");
					continue;
				}
				if (Tex.r(tex.get(GeneratedAssets.MARKER_KIND_X, GeneratedAssets.MARKER_Y)) != 0) {
					wrong.add(chapter.id + "/" + base.name() + ": kind texel R is not 0 (a base garment)");
					continue;
				}
				out.add(new Layer(chapter, base, tex));
			}
		}
		if (out.isEmpty()) wrong.add("no base layers at all");
		return out;
	}

	private static boolean inside(int x, int y, int x0, int y0) {
		return x >= x0 && x < x0 + BOX && y >= y0 && y < y0 + BOX;
	}

	private static boolean same(Tex a, Tex b) {
		if (a.width != b.width || a.height != b.height) return false;
		for (int y = 0; y < a.height; y++) for (int x = 0; x < a.width; x++) if (a.get(x, y) != b.get(x, y)) return false;
		return true;
	}

	private static void finish(GameTestHelper helper, List<String> wrong) {
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/** A generated equipment layer texture, off the runtime classpath (datagen has to have run). */
	private static Tex generated(String name) {
		String path = "/assets/" + Ovvar.MOD_ID + "/textures/entity/equipment/" + name + ".png";
		try (var in = ShaderLayoutTests.class.getResourceAsStream(path)) {
			if (in == null) throw new IOException("missing " + path + " — run ./gradlew runDatagen");
			return Tex.read(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String resource(String path) {
		try (var in = ShaderLayoutTests.class.getResourceAsStream(path)) {
			if (in == null) throw new IOException("missing " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
