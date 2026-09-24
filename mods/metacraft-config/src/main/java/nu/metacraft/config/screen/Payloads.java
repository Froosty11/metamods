package nu.metacraft.config.screen;

import net.minecraft.nbt.*;
import nu.metacraft.config.source.Field;
import nu.metacraft.config.source.Page;
import nu.metacraft.lib.config.describe.OptionKind;
import nu.metacraft.lib.config.describe.OptionSpec;

import java.util.*;

/** A dialog's payload read back into text per field, the form {@code ConfigEdits} takes. */
public final class Payloads {
	private Payloads() {}

	public static Map<String, String> values(Page page, CompoundTag payload) {
		Map<String, String> values = new LinkedHashMap<>();
		for (int i = 0; i < page.fields().size(); i++) {
			Field field = page.fields().get(i);
			Tag tag = payload.get(Inputs.key(i));
			if (tag == null || !field.editable()) continue;
			values.put(field.key(), text(field, tag));
		}
		return values;
	}

	private static String text(Field field, Tag tag) {
		return switch (tag) {
			case ByteTag b when field.kind() == OptionKind.BOOLEAN -> b.byteValue() != 0 ? "true" : "false";
			case NumericTag n when field.kind() == OptionKind.WHOLE -> wholeText(n);
			case NumericTag n -> decimalText(n);
			case StringTag s -> s.value();
			default -> tag.toString();
		};
	}

	// A modified client can send NaN or infinity for a slider; BigDecimal.valueOf(NaN) throws,
	// and Math.round(NaN) silently becomes 0. Pass the raw text through instead, so ConfigEdits
	// refuses it with a message.
	private static String wholeText(NumericTag n) {
		double value = n.doubleValue();
		return Double.isFinite(value) ? Long.toString(Math.round(value)) : Double.toString(value);
	}

	private static String decimalText(NumericTag n) {
		// A double round-trips exactly; a float is rounded through its shortest decimal string
		// first, so 0.25f reads back as "0.25" and not "0.25000000372...".
		double value = n instanceof DoubleTag d ? d.doubleValue() : Double.parseDouble(Float.toString(n.floatValue()));
		return Double.isFinite(value) ? OptionSpec.number(value) : Double.toString(value);
	}

	public static List<String> path(CompoundTag payload) {
		String page = payload.getStringOr("page", "");
		return page.isEmpty() ? List.of() : List.of(page.split("/"));
	}
}
