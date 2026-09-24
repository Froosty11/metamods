package nu.metacraft.lib.config.describe;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.List;

/**
 * One described component. {@code valueType} is the component's class, or the class inside its
 * {@code Optional}; {@code section} is set for {@link OptionKind#SECTION}.
 */
public record OptionSpec(
		String key, String name, String description, OptionKind kind, Class<?> valueType, boolean optional,
		double min, double max, double step, boolean restart, List<String> choices,
		@Nullable ConfigSpec<?> section, RecordComponent component
) {
	/** Sliders take at most this many steps; longer ranges are typed. */
	public static final int MAX_SLIDER_STEPS = 50;

	public Object read(Record owner) {
		try {
			return component.getAccessor().invoke(owner);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot read " + name, e);
		}
	}

	public boolean editable() {
		return kind != OptionKind.READ_ONLY && kind != OptionKind.SECTION;
	}

	/** The slider step: {@code step}, or 1 for a whole number without one, or 0 (typed). */
	public double effectiveStep() {
		if (step > 0) return step;
		return kind == OptionKind.WHOLE ? 1 : 0;
	}

	public boolean slider() {
		if (kind != OptionKind.WHOLE && kind != OptionKind.DECIMAL) return false;
		if (optional || Double.isInfinite(min) || Double.isInfinite(max) || effectiveStep() <= 0) return false;
		return (max - min) / effectiveStep() <= MAX_SLIDER_STEPS;
	}

	public boolean bounded() {
		return !Double.isInfinite(min) || !Double.isInfinite(max);
	}

	/** "(0 – ∞)", or "" for an unbounded option. */
	public String rangeText() {
		if (!bounded()) return "";
		return "(" + number(min) + " – " + number(max) + ")";
	}

	public static String number(double value) {
		if (value == Double.POSITIVE_INFINITY) return "∞";
		if (value == Double.NEGATIVE_INFINITY) return "-∞";
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}
}
