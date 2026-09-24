import net.minecraft.nbt.CompoundTag;
import nu.metacraft.config.screen.Payloads;
import nu.metacraft.config.source.Field;
import nu.metacraft.config.source.Page;
import nu.metacraft.lib.config.describe.OptionKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestPayloads {
	private static Field field(String key, OptionKind kind, boolean slider) {
		return new Field(key, key, kind, "", 0, 16, 1, slider, List.of("a", "b"), false, true);
	}

	private static final Page PAGE = new Page(List.of(), "T", List.of(
			field("on", OptionKind.BOOLEAN, false),
			field("count", OptionKind.WHOLE, true),
			field("share", OptionKind.DECIMAL, true),
			field("speed", OptionKind.DECIMAL, false),
			field("mode", OptionKind.CHOICE, false)), List.of());

	@Test
	public void readsEachInputAsText() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("o0", false);
		tag.putFloat("o1", 7.0f);
		tag.putFloat("o2", 0.25f);
		tag.putString("o3", " 3.5 ");
		tag.putString("o4", "b");
		assertEquals(Map.of("on", "false", "count", "7", "share", "0.25", "speed", " 3.5 ", "mode", "b"), Payloads.values(PAGE, tag));
	}

	@Test
	public void missingOrWrongTypedInputsAreLeftOutOrPassedAsText() {
		CompoundTag tag = new CompoundTag();
		tag.putString("o1", "lots");   // a modified client sends text for a slider
		tag.putInt("o9", 1);          // no such field
		Map<String, String> values = Payloads.values(PAGE, tag);
		assertEquals(Map.of("count", "lots"), values);   // ConfigEdits then refuses "lots" with a message
	}

	@Test
	public void nonFiniteNumbersAreNeverParsedAsAValidNumber() {
		CompoundTag tag = new CompoundTag();
		tag.putFloat("o1", Float.NaN);           // count: WHOLE
		tag.putDouble("o2", Double.POSITIVE_INFINITY);   // share: DECIMAL
		Map<String, String> values = Payloads.values(PAGE, tag);
		assertThrows(NumberFormatException.class, () -> Long.parseLong(values.get("count")));
		assertTrue(Double.isInfinite(Double.parseDouble(values.get("share"))));
	}

	@Test
	public void readsThePath() {
		CompoundTag tag = new CompoundTag();
		tag.putString("page", "designs/store");
		assertEquals(List.of("designs", "store"), Payloads.path(tag));
		tag.putString("page", "");
		assertEquals(List.of(), Payloads.path(tag));
	}
}
