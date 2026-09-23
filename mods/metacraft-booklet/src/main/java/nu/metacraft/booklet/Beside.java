package nu.metacraft.booklet;

import eu.pb4.booklet.api.TextUncenterer;
import eu.pb4.booklet.impl.BookletImageHandler;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.body.PlainMessage;
import xyz.nucleoid.server.translations.api.LocalizationTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * Text on the left, a picture on the right — a layout a dialog body does not have, since bodies
 * stack. A page asks for it with an image under {@code beside/}:
 * <pre>
 *   ### Image: metacraft:beside/ovvar/hero The text that goes to the left of the picture.
 * </pre>
 * Booklet draws an image as rows of 9 px glyph lines, the same height as a line of text; so the text
 * is wrapped to the room left of the picture (every line padded to exactly that width, as Booklet's
 * own left-aligned text is), and each text line and image row are joined into one line. The shorter
 * of the two is centred against the other. One pixel of image is one UI pixel here, so a picture
 * beside text is made at its display size.
 */
public final class Beside {
	private Beside() {}

	public static final String PREFIX = "beside/";
	/** The page body's width, as Booklet's (### Width defaults to 300), and its text inset. */
	private static final int WIDTH = 300, INSET = 8, GAP = 8;

	public static boolean wants(Identifier image) {
		return image.getPath().startsWith(PREFIX);
	}

	public static PlainMessage layout(Identifier id, Component text, PacketContext context) {
		BookletImageHandler.ProcessedImage image = BookletImageHandler.getImage(id);
		String glyphs = image.component().getString();
		String[] rows = glyphs.split("\n", -1);
		int imageWidth = image.width() - 8;   // ProcessedImage.width carries 8 px of body margin
		int textWidth = Math.max(40, WIDTH - INSET - GAP - imageWidth);

		String language = LocalizationTarget.of(context).getLanguageCode();
		List<Component> lines = TextUncenterer.getLeftAligned(text, textWidth, language);

		int count = Math.max(lines.size(), rows.length);
		int textTop = (count - lines.size()) / 2, imageTop = (count - rows.length) / 2;
		List<Component> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int t = i - textTop, r = i - imageTop;
			MutableComponent line = Component.empty();
			line.append(t >= 0 && t < lines.size() ? lines.get(t) : TextUncenterer.filler(textWidth));
			line.append(TextUncenterer.filler(GAP));
			if (r >= 0 && r < rows.length) line.append(Component.literal(rows[r]).setStyle(image.component().getStyle()));
			out.add(line);
		}
		return new PlainMessage(CommonComponents.joinLines(out), WIDTH);
	}
}
