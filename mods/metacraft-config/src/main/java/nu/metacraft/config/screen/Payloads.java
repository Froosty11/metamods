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
			case NumericTag n when field.kind() == OptionKind.WHOLE -> Long.toString(Math.round(n.doubleValue()));
			case NumericTag n -> OptionSpec.number(Double.parseDouble(Float.toString(n.floatValue())));
			case StringTag s -> s.value();
			default -> tag.toString();
		};
	}

	public static List<String> path(CompoundTag payload) {
		String page = payload.getStringOr("page", "");
		return page.isEmpty() ? List.of() : List.of(page.split("/"));
	}
}
