import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import fixtures.*;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestConfigEdits {
	private static final ConfigSpec<Sample> SPEC = ConfigSpec.of(Sample.class);
	private static final Codec<Sample> CODEC = SPEC.codec().codec();

	private static com.mojang.serialization.DataResult<Sample> edit(List<String> page, Map<String, String> values) {
		return ConfigEdits.apply(SPEC, CODEC, Sample.DEFAULT, page, values, JsonOps.INSTANCE);
	}

	private static String error(Map<String, String> values) {
		return edit(List.of(), values).error().orElseThrow().message();
	}

	@Test
	public void appliesEveryKind() {
		Sample s = edit(List.of(), Map.of(
				"enabled", "false", "count", " 12 ", "speed", "7.5", "limit", "2", "title", "new",
				"block", "minecraft:dirt", "mode", "slow", "words", "one\n\n two \n")).getOrThrow();
		assertFalse(s.enabled());
		assertEquals(12, s.count());
		assertEquals(7.5, s.speed());
		assertEquals(Optional.of(2.0), s.limit());
		assertEquals("new", s.title());
		assertEquals("minecraft:dirt", s.block().toString());
		assertEquals(Sample.Mode.SLOW, s.mode());
		assertEquals(List.of("one", "two"), s.words());
		assertEquals(Sample.DEFAULT.store(), s.store());
	}

	@Test
	public void blankOptionalIsAbsent() {
		Sample withLimit = edit(List.of(), Map.of("limit", "5")).getOrThrow();
		Sample cleared = ConfigEdits.apply(SPEC, CODEC, withLimit, List.of(), Map.of("limit", "  "), JsonOps.INSTANCE).getOrThrow();
		assertEquals(Optional.empty(), cleared.limit());
	}

	@Test
	public void numbersWithACommaOrLettersAreRefused() {
		assertTrue(error(Map.of("speed", "1,5")).contains("speed"));
		assertTrue(error(Map.of("speed", "1,5")).contains("."));   // suggests a point
		assertTrue(error(Map.of("count", "lots")).contains("whole number"));
		assertTrue(error(Map.of("count", "")).contains("needs a value"));
		assertTrue(error(Map.of("count", "2.5")).contains("whole number"));
	}

	@Test
	public void outOfRangeAndUnknownChoiceAreRefused() {
		assertTrue(error(Map.of("count", "99")).contains("count"));
		assertTrue(error(Map.of("mode", "warp")).contains("mode"));
		assertTrue(error(Map.of("block", "Not An Id")).contains("block"));
		assertTrue(error(Map.of("enabled", "maybe")).contains("enabled"));
	}

	@Test
	public void unknownAndReadOnlyKeysAreRefused() {
		assertTrue(error(Map.of("nope", "1")).contains("nope"));
		assertTrue(error(Map.of("store", "x")).contains("store"));
	}

	@Test
	public void recordValidationRuns() {
		assertEquals("That title is not allowed.", error(Map.of("title", "forbidden")));
	}

	@Test
	public void nestedPageLeavesEverythingElse() {
		Sample start = edit(List.of(), Map.of("count", "9", "title", "kept")).getOrThrow();
		Sample s = ConfigEdits.apply(SPEC, CODEC, start, List.of("store"), Map.of("url", "db://y", "interval", "20"), JsonOps.INSTANCE).getOrThrow();
		assertEquals(new SampleSection("db://y", 20), s.store());
		assertEquals(9, s.count());
		assertEquals("kept", s.title());
	}

	@Test
	public void resetPutsOnePageBack() {
		Sample start = ConfigEdits.apply(SPEC, CODEC, Sample.DEFAULT, List.of("store"), Map.of("url", "db://z"), JsonOps.INSTANCE).getOrThrow();
		Sample changedTop = ConfigEdits.apply(SPEC, CODEC, start, List.of(), Map.of("count", "2"), JsonOps.INSTANCE).getOrThrow();
		Sample reset = ConfigEdits.reset(SPEC, CODEC, changedTop, List.of("store"), JsonOps.INSTANCE).getOrThrow();
		assertEquals(SampleSection.DEFAULT, reset.store());
		assertEquals(2, reset.count());
	}

	@Test
	public void textForms() {
		assertEquals("60", ConfigEdits.text(SPEC.option("speed").orElseThrow(), 60.0));
		assertEquals("0.5", ConfigEdits.text(SPEC.option("share").orElseThrow(), 0.5));
		assertEquals("", ConfigEdits.text(SPEC.option("limit").orElseThrow(), Optional.empty()));
		assertEquals("a\nb", ConfigEdits.text(SPEC.option("words").orElseThrow(), List.of("a", "b")));
		assertEquals("fast", ConfigEdits.text(SPEC.option("mode").orElseThrow(), Sample.Mode.FAST));
	}
}
