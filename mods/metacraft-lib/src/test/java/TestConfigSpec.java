import fixtures.*;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestConfigSpec {

	@Test
	public void readsEveryComponent() {
		ConfigSpec<Sample> spec = ConfigSpec.of(Sample.class);
		assertEquals("Sample", spec.name());
		assertEquals(List.of("enabled", "count", "speed", "share", "limit", "title", "block", "mode", "words", "store", "old-name"),
				spec.options().stream().map(OptionSpec::key).toList());
		assertEquals(OptionKind.BOOLEAN, spec.option("enabled").orElseThrow().kind());
		assertEquals(OptionKind.WHOLE, spec.option("count").orElseThrow().kind());
		assertEquals(OptionKind.DECIMAL, spec.option("speed").orElseThrow().kind());
		OptionSpec limit = spec.option("limit").orElseThrow();
		assertEquals(OptionKind.DECIMAL, limit.kind());
		assertTrue(limit.optional());
		assertEquals(OptionKind.TEXT, spec.option("title").orElseThrow().kind());
		assertEquals(OptionKind.IDENTIFIER, spec.option("block").orElseThrow().kind());
		OptionSpec mode = spec.option("mode").orElseThrow();
		assertEquals(OptionKind.CHOICE, mode.kind());
		assertEquals(List.of("fast", "slow"), mode.choices());
		assertTrue(mode.restart());
		assertEquals(OptionKind.TEXT_LIST, spec.option("words").orElseThrow().kind());
		OptionSpec store = spec.option("store").orElseThrow();
		assertEquals(OptionKind.SECTION, store.kind());
		assertEquals("Store", store.section().name());
		assertSame(Sample.DEFAULT, spec.defaults());
	}

	@Test
	public void slidersOnlyForShortRanges() {
		ConfigSpec<Sample> spec = ConfigSpec.of(Sample.class);
		assertTrue(spec.option("count").orElseThrow().slider());   // 1-16, step 1
		assertTrue(spec.option("share").orElseThrow().slider());   // 0-1, step 0.05 = 20 steps
		assertFalse(spec.option("speed").orElseThrow().slider());  // unbounded
		assertFalse(spec.option("limit").orElseThrow().slider());  // optional
		assertEquals("(0 – ∞)", spec.option("speed").orElseThrow().rangeText());
		assertEquals("(1 – 16)", spec.option("count").orElseThrow().rangeText());
	}

	@Test
	public void validatesThroughTheRecord() {
		ConfigSpec<Sample> spec = ConfigSpec.of(Sample.class);
		assertEquals("That title is not allowed.", spec.validate(new Sample(true, 6, 60, 0.5, java.util.Optional.empty(),
				"forbidden", Sample.DEFAULT.block(), Sample.Mode.FAST, List.of(), SampleSection.DEFAULT, 3)).orElseThrow());
		assertTrue(spec.validate(Sample.DEFAULT).isEmpty());
	}

	@Test
	public void refusesBadDescriptions() {
		assertTrue(assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(BadRange.class)).getMessage().contains("BadRange.x"));
		assertTrue(assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(NoDefault.class)).getMessage().contains("DEFAULT"));
		assertTrue(assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(Unannotated.class)).getMessage().contains("Unannotated.y"));
	}

	@Test
	public void unsupportedTypesAreReadOnly() {
		assertEquals(OptionKind.READ_ONLY, ConfigSpec.of(WithList.class).option("weights").orElseThrow().kind());
	}
}
