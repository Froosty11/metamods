package nu.metacraft.booklet;

import eu.pb4.booklet.api.TextUncenterer;
import eu.pb4.booklet.impl.BookletImageHandler;
import eu.pb4.booklet.impl.ui.UiResourceCreator;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.body.PlainMessage;
import xyz.nucleoid.server.translations.api.LocalizationTarget;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Text on the left, a picture on the right — a layout a dialog body does not have, since bodies
 * stack. A page asks for it with an image under {@code beside/}:
 * <pre>
 *   ### Image: metacraft:beside/ovvar/hero The text that goes to the left of the picture.
 * </pre>
 *
 * <p>Booklet draws an image as rows of 9 px glyph lines, the height of a line of text, and draws it
 * {@code ceil(width / 292)} image pixels to a UI pixel. A picture beside text is therefore made as a
 * wide image ({@link #IMAGE_WIDTH}, four image pixels to a UI pixel, as sharp as a 1080p screen at
 * GUI scale 4 shows) with the picture at its right and nothing to its left. Each line is then that
 * image row, a negative space back to the start of the line, and a line of text wrapped to the empty
 * part — so the text is drawn over the transparent half of the picture. Every line is padded to the
 * same width so the dialog, which centres lines, keeps them flush.
 */
public final class Beside {
	private Beside() {}

	public static final String PREFIX = "beside/";
	/** The image file's width: 4 × 288 UI px (288 is a whole number of Booklet's 16 px tiles). */
	public static final int IMAGE_WIDTH = 1152;
	private static final int GAP = 8;

	/** Negative spaces in Booklet's UI font, registered before its resource pack is built. */
	private static final char BACK_100 = UiResourceCreator.space(-100), BACK_10 = UiResourceCreator.space(-10), BACK_1 = UiResourceCreator.space(-1);

	/** Per image: where the picture starts, in UI px from the image's left edge. */
	private static final Map<Identifier, Integer> CONTENT_LEFT = new HashMap<>();

	/** Reads where each beside/ picture starts; call from the mod's init (it also registers the spaces). */
	public static void init() {
		var self = FabricLoader.getInstance().getModContainer(MetacraftBooklet.MOD_ID).orElseThrow();
		for (Path root : self.getRootPaths()) {
			Path dir = root.resolve("assets");
			if (!Files.isDirectory(dir)) continue;
			try (Stream<Path> files = Files.walk(dir)) {
				for (Path file : files.filter(p -> p.toString().endsWith(".png")).toList()) {
					Path rel = dir.relativize(file);
					String ns = rel.getName(0).toString();
					String path = rel.subpath(1, rel.getNameCount()).toString().replace('\\', '/');
					String prefix = "textures/booklet/image/" + PREFIX;
					if (!path.startsWith(prefix)) continue;
					BufferedImage image;
					try (var in = Files.newInputStream(file)) {
						image = ImageIO.read(in);
					}
					int scale = (int) Math.ceil(image.getWidth() / 292f);
					CONTENT_LEFT.put(Identifier.fromNamespaceAndPath(ns, path.substring("textures/booklet/image/".length(), path.length() - 4)),
							leftEdge(image) / scale);
				}
			} catch (IOException | UnsupportedOperationException e) {
				MetacraftBooklet.LOGGER.warn("[{}] cannot read the beside/ images: {}", MetacraftBooklet.MOD_ID, e.toString());
			}
		}
	}

	private static int leftEdge(BufferedImage image) {
		for (int x = 0; x < image.getWidth(); x++) {
			for (int y = 0; y < image.getHeight(); y++) {
				if ((image.getRGB(x, y) >>> 24) != 0) return x;
			}
		}
		return image.getWidth();
	}

	public static boolean wants(Identifier image) {
		return image.getPath().startsWith(PREFIX);
	}

	public static PlainMessage layout(Identifier id, Component text, PacketContext context) {
		BookletImageHandler.ProcessedImage image = BookletImageHandler.getImage(id);
		String[] rows = image.component().getString().split("\n", -1);
		// A row's advance: each tile glyph is dx wide plus 1, then 'a' (-1); each 'b' is +1.
		int advance = 0;
		{
			int scale = 4, dx = Math.min(128 / scale, 16);
			for (char c : rows[0].toCharArray()) advance += c == 'b' ? 1 : c == 'a' ? 0 : dx;
		}
		int textWidth = Math.max(40, CONTENT_LEFT.getOrDefault(id, advance / 2) - GAP);

		String language = LocalizationTarget.of(context).getLanguageCode();
		List<Component> lines = TextUncenterer.getLeftAligned(text, textWidth, language);

		int count = Math.max(lines.size(), rows.length);
		int textTop = (count - lines.size()) / 2, imageTop = (count - rows.length) / 2;
		List<Component> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int t = i - textTop, r = i - imageTop;
			MutableComponent line = Component.empty();
			if (r >= 0 && r < rows.length) {
				line.append(Component.literal(rows[r]).setStyle(image.component().getStyle()));
				line.append(back(advance));
			}
			line.append(t >= 0 && t < lines.size() ? lines.get(t) : TextUncenterer.filler(textWidth));
			line.append(TextUncenterer.filler(advance - textWidth));
			out.add(line);
		}
		return new PlainMessage(CommonComponents.joinLines(out), Math.max(300, advance + 8));
	}

	private static Component back(int width) {
		StringBuilder b = new StringBuilder();
		while (width >= 100) { b.append(BACK_100); width -= 100; }
		while (width >= 10) { b.append(BACK_10); width -= 10; }
		while (width >= 1) { b.append(BACK_1); width -= 1; }
		return Component.literal(b.toString()).setStyle(UiResourceCreator.STYLE);
	}
}
