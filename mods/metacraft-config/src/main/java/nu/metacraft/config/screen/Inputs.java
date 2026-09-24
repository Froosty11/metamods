package nu.metacraft.config.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.input.*;
import nu.metacraft.config.source.Field;

import java.util.Optional;

/** The dialog input for a field. Keys are positional ({@code o0}, {@code o1}, …): option keys may hold characters inputs cannot. */
public final class Inputs {
	private static final int WIDTH = 256, LIST_WIDTH = 300;

	private Inputs() {}

	public static String key(int index) {
		return "o" + index;
	}

	public static InputControl forField(Field field, String value) {
		Component label = Component.literal(field.label() + (field.restart() ? " ⟳" : ""));
		return switch (field.kind()) {
			case BOOLEAN -> new BooleanInput(label, value.equals("true"), "true", "false");
			case WHOLE, DECIMAL -> field.slider()
					? new NumberRangeInput(WIDTH, label, "options.generic_value",
							new NumberRangeInput.RangeInfo((float) field.min(), (float) field.max(),
									Optional.of(clamp(parse(value, (float) field.min()), (float) field.min(), (float) field.max())),
									Optional.of((float) field.step())))
					: text(label, value, 64);
			case CHOICE -> new SingleOptionInput(WIDTH, field.choices().stream()
					.map(choice -> new SingleOptionInput.Entry(choice, Optional.empty(), choice.equals(value))).toList(), label, true);
			case TEXT_LIST, IDENTIFIER_LIST -> new TextInput(LIST_WIDTH, label, true, value, 8192,
					Optional.of(new TextInput.MultilineOptions(Optional.of(8), Optional.empty())));
			default -> text(label, value, 1024);
		};
	}

	private static TextInput text(Component label, String value, int maxLength) {
		return new TextInput(WIDTH, label, true, value, maxLength, Optional.empty());
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
