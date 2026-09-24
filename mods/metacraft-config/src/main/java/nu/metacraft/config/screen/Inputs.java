package nu.metacraft.config.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.input.*;
import nu.metacraft.config.source.Field;

import java.util.List;
import java.util.Optional;

/** The dialog input for a field. Keys are positional ({@code o0}, {@code o1}, …): option keys may hold characters inputs cannot. */
public final class Inputs {
	private static final int WIDTH = 256, LIST_WIDTH = 300;

	private Inputs() {}

	public static String key(int index) {
		return "o" + index;
	}

	public static InputControl forField(Field field, String value) {
		// Vanilla's font has no ⟳; spell the restart marker out instead of drawing a tofu box.
		Component label = Component.literal(field.label() + (field.restart() ? " (restart)" : ""));
		return switch (field.kind()) {
			case BOOLEAN -> new BooleanInput(label, value.equals("true"), "true", "false");
			case WHOLE, DECIMAL -> field.slider()
					? new NumberRangeInput(WIDTH, label, "options.generic_value",
							new NumberRangeInput.RangeInfo((float) field.min(), (float) field.max(),
									Optional.of(clamp(parse(value, (float) field.min()), (float) field.min(), (float) field.max())),
									Optional.of((float) field.step())))
					: text(label, value, 64);
			case CHOICE -> new SingleOptionInput(choiceWidth(label, field.choices()), field.choices().stream()
					.map(choice -> new SingleOptionInput.Entry(choice, Optional.empty(), choice.equals(value))).toList(), label, true);
			case TEXT_LIST, IDENTIFIER_LIST -> new TextInput(LIST_WIDTH, label, true, truncate(value, 8192), 8192,
					Optional.of(new TextInput.MultilineOptions(Optional.of(8), Optional.empty())));
			default -> text(label, value, 1024);
		};
	}

	// The button draws "<label>: <chosen value>"; a fixed narrow width clips both. About 6 GUI px
	// per character plus 20 px of padding, clamped to a sane range, fits the label beside the
	// longest choice so the button's text does not get cut off.
	private static int choiceWidth(Component label, List<String> choices) {
		int longest = 0;
		for (String choice : choices) longest = Math.max(longest, choice.length());
		int contentLength = label.getString().length() + 2 + longest;
		return Math.max(200, Math.min(1024, 20 + 6 * contentLength));
	}

	private static TextInput text(Component label, String value, int maxLength) {
		return new TextInput(WIDTH, label, true, truncate(value, maxLength), maxLength, Optional.empty());
	}

	// A refused save, or a value edited outside the screen, can be longer than the box that
	// shows it. The vanilla codec rejects an initial text longer than maxLength even on encode,
	// which would fail to send the dialog packet at all; truncate instead of failing to open.
	private static String truncate(String value, int maxLength) {
		return value.length() > maxLength ? value.substring(0, maxLength) : value;
	}

	private static float parse(String value, float fallback) {
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
