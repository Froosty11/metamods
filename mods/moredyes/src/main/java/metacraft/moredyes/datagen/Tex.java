package metacraft.moredyes.datagen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** ARGB texture with the few operations the generator needs. Immutable; every op returns a copy. */
final class Tex {
	final int width, height;
	private final int[] argb;

	private Tex(int width, int height, int[] argb) {
		this.width = width;
		this.height = height;
		this.argb = argb;
	}

	static Tex read(InputStream in) {
		try (in) {
			BufferedImage img = ImageIO.read(in);
			if (img == null) throw new IOException("not an image");
			int w = img.getWidth(), h = img.getHeight();
			int bands = img.getRaster().getNumBands();
			int[] px;
			if (bands <= 2 && img.getColorModel().getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY) {
				// Greyscale PNGs: getRGB would run the samples through ImageIO's linear grey colour
				// space and shift them; take the raw 8-bit samples like every other PNG decoder does.
				px = new int[w * h];
				int[] s = new int[bands];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						img.getRaster().getPixel(x, y, s);
						int v = s[0], a = bands == 2 ? s[1] : 255;
						px[y * w + x] = (a << 24) | (v << 16) | (v << 8) | v;
					}
				}
			} else {
				px = img.getRGB(0, 0, w, h, null, 0, w);
			}
			return new Tex(w, h, px);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	byte[] png() {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, width, height, argb, 0, width);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(img, "png", out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	private static int a(int p) { return (p >>> 24) & 0xFF; }
	private static int r(int p) { return (p >>> 16) & 0xFF; }
	private static int g(int p) { return (p >>> 8) & 0xFF; }
	private static int b(int p) { return p & 0xFF; }
	private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
	/** Channels are clamped: fully transparent pixels can land outside the ramp and must not leak into alpha. */
	private static int pack(int a, int r, int g, int b) { return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
	private static double lum(int p) { return 0.2126 * r(p) + 0.7152 * g(p) + 0.0722 * b(p); }

	/**
	 * Map the source's luminance range onto a two-stop ramp, preserving alpha. Luminance is
	 * normalised to the source's own min/max over visible pixels, so a texture keeps its full
	 * contrast (wool knit, concrete grain) whatever its absolute brightness.
	 */
	Tex recolour(int dark, int light) {
		double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
		for (int p : argb) {
			if (a(p) > 0) {
				double l = lum(p);
				lo = Math.min(lo, l);
				hi = Math.max(hi, l);
			}
		}
		if (lo > hi) { lo = 0; hi = 255; }
		double span = hi - lo == 0 ? 1.0 : hi - lo;
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			double t = (lum(p) - lo) / span;
			out[i] = pack(a(p),
					(int) Math.round(r(dark) + (r(light) - r(dark)) * t),
					(int) Math.round(g(dark) + (g(light) - g(dark)) * t),
					(int) Math.round(b(dark) + (b(light) - b(dark)) * t));
		}
		return new Tex(width, height, out);
	}

	/**
	 * Vanilla's hurt overlay baked in: the overlay texel is red at alpha 178/255 and the entity
	 * shader does mix(overlay, texel, overlay.a), i.e. texel * 0.7 + (76, 0, 0).
	 */
	Tex hurt() {
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			out[i] = pack(a(p), Math.min(255, (int) Math.round(r(p) * 0.7 + 76.5)),
					(int) Math.round(g(p) * 0.7), (int) Math.round(b(p) * 0.7));
		}
		return new Tex(width, height, out);
	}

	/**
	 * Every channel multiplied by as much as the brightest pixel allows, so nothing clips. A pure
	 * scale keeps the pattern's contrast exactly; it just spends the headroom between the texture's
	 * brightest pixel and white.
	 */
	Tex scaleToWhite() {
		int max = 0;
		for (int p : argb) {
			if (a(p) == 0) continue;
			max = Math.max(max, Math.max(r(p), Math.max(g(p), b(p))));
		}
		if (max == 0) return this;
		double factor = 255.0 / max;
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			out[i] = pack(a(p), (int) Math.round(r(p) * factor),
					(int) Math.round(g(p) * factor), (int) Math.round(b(p) * factor));
		}
		return new Tex(width, height, out);
	}

	/** Every pixel's alpha scaled by {@code factor} (the beacon beam's translucent glow layer). */
	Tex alpha(double factor) {
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			out[i] = pack((int) Math.round(a(p) * factor), r(p), g(p), b(p));
		}
		return new Tex(width, height, out);
	}

	/** Nearest-neighbour vertical resample; the horizontal resolution is untouched. */
	Tex scaleRows(int rows) {
		int[] out = new int[width * rows];
		for (int y = 0; y < rows; y++) {
			int src = Math.min(height - 1, y * height / rows) * width;
			System.arraycopy(argb, src, out, y * width, width);
		}
		return new Tex(width, rows, out);
	}

	/**
	 * The beacon beam's animation sheet: {@code frames} frames <b>side by side</b>, each one this tile
	 * stacked up to {@code frameHeight} pixels and rolled up by {@code round(f * height / frames)}
	 * rows, so playing the frames scrolls the pattern up by one tile every {@code frames} ticks.
	 *
	 * Side by side, not stacked, because the frame is what has to stay small: a 16-block core frame is
	 * 1280 px tall on its own, and five of those stacked would be a 6400 px sprite that drags the
	 * whole block atlas up a power of two. The client reads either layout —
	 * {@code SpriteContents.AnimatedTexture} takes frame {@code i} from
	 * {@code (i % (imageWidth / frameWidth), i / ...)} — as long as the {@code .mcmeta} gives the frame
	 * size.
	 *
	 * {@code frameHeight} need not be a whole number of tiles: the core's is 2.5 tiles a block, which
	 * is vanilla's stretch.
	 */
	Tex beamSheet(int frameHeight, int frames) {
		int sheetWidth = width * frames;
		int[] out = new int[sheetWidth * frameHeight];
		for (int f = 0; f < frames; f++) {
			int shift = Math.round((float) f * height / frames);
			for (int y = 0; y < frameHeight; y++) {
				int src = Math.floorMod(y + shift, height) * width;
				System.arraycopy(argb, src, out, y * sheetWidth + f * width, width);
			}
		}
		return new Tex(sheetWidth, frameHeight, out);
	}

	/** {@code over} alpha-composited on top of this (same size). */
	Tex composite(Tex over) {
		if (over.width != width || over.height != height) throw new IllegalArgumentException("size mismatch");
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int base = argb[i], top = over.argb[i];
			double ta = a(top) / 255.0, ba = a(base) / 255.0;
			double oa = ta + ba * (1 - ta);
			if (oa == 0) { out[i] = 0; continue; }
			int rr = (int) Math.round((r(top) * ta + r(base) * ba * (1 - ta)) / oa);
			int gg = (int) Math.round((g(top) * ta + g(base) * ba * (1 - ta)) / oa);
			int bb = (int) Math.round((b(top) * ta + b(base) * ba * (1 - ta)) / oa);
			out[i] = pack((int) Math.round(oa * 255), rr, gg, bb);
		}
		return new Tex(width, height, out);
	}
}
