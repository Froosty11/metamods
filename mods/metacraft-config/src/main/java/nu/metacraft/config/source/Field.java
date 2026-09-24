package nu.metacraft.config.source;

import nu.metacraft.lib.config.describe.OptionKind;

import java.util.List;

/** One option on a page, as the screen draws it; {@code value} is its current value as text. */
public record Field(String key, String label, OptionKind kind, String value, double min, double max, double step,
		boolean slider, List<String> choices, boolean restart, boolean editable) {
}
