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
	private static final Codec<Sample> CODEC = Sample.CODEC.codec();

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
	public void wholeNumbersBeyondAnIntAreRefusedForAnIntOption() {
		String large = error(Map.of("old-name", "3000000000"));
		assertTrue(large.contains("3000000000 is too large"), large);
		String small = error(Map.of("old-name", "-3000000000"));
		assertTrue(small.contains("-3000000000 is too small"), small);
		assertEquals(Integer.MAX_VALUE, edit(List.of(), Map.of("old-name", Integer.toString(Integer.MAX_VALUE))).getOrThrow().renamed());
	}

	@Test
	public void outOfRangeAndUnknownChoiceAreRefused() {
		assertTrue(error(Map.of("count", "99")).contains("count"));
		assertTrue(error(Map.of("mode", "warp")).contains("mode"));
		assertTrue(error(Map.of("block", "Not An Id")).contains("block"));
		assertTrue(error(Map.of("enabled", "maybe")).contains("enabled"));
	}

	@Test
	public void numbersOutsideTheOptionsRangeAreRefusedByTheEdit() {
		assertEquals("count (How many.): 99 is not in (1 – 16)", error(Map.of("count", "99")));
		assertEquals("count (How many.): 0 is not in (1 – 16)", error(Map.of("count", "0")));
		assertEquals("share (Share, in steps of 0.05.): 1.5 is not in (0 – 1)", error(Map.of("share", "1.5")));
		assertEquals("speed (How fast.): -0.5 is not in (0 – ∞)", error(Map.of("speed", "-0.5")));
		assertEquals("limit (Optional limit.): -2 is not in (0 – ∞)", error(Map.of("limit", "-2")));
		assertEquals(16, edit(List.of(), Map.of("count", "16")).getOrThrow().count());   // the bounds are allowed
		assertEquals(1, edit(List.of(), Map.of("count", "1")).getOrThrow().count());
		assertEquals(1.0, edit(List.of(), Map.of("share", "1")).getOrThrow().share());
		assertEquals(0.0, edit(List.of(), Map.of("share", "0")).getOrThrow().share());
	}

	@Test
	public void rangesAreEnforcedEvenWhenTheCodecDoesNot() {
		// A codec with no ranges of its own: the refusal comes from the @Option.
		Codec<SampleSection> lax = com.mojang.serialization.codecs.RecordCodecBuilder.<SampleSection>mapCodec(i -> i.group(
				Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
				Codec.INT.fieldOf("interval").forGetter(SampleSection::interval)
		).apply(i, SampleSection::new)).codec();
		ConfigSpec<SampleSection> spec = ConfigSpec.of(SampleSection.class);
		var result = ConfigEdits.apply(spec, lax, SampleSection.DEFAULT, List.of(), Map.of("interval", "999"), JsonOps.INSTANCE);
		assertEquals("interval (Seconds between syncs.): 999 is not in (1 – 60)", result.error().orElseThrow().message());
		var nested = ConfigEdits.apply(SPEC, CODEC, Sample.DEFAULT, List.of("store"), Map.of("interval", "61"), JsonOps.INSTANCE);
		assertEquals("interval (Seconds between syncs.): 61 is not in (1 – 60)", nested.error().orElseThrow().message());
	}

	@Test
	public void anEditBackToADefaultTheCodecLeavesOutIsSaved() {
		// A section codec with optionalFieldOf("interval", 10): writing 10 leaves the key out.
		Codec<SampleSection> leavesOut = com.mojang.serialization.codecs.RecordCodecBuilder.<SampleSection>mapCodec(i -> i.group(
				Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
				Codec.INT.optionalFieldOf("interval", 10).forGetter(SampleSection::interval)
		).apply(i, SampleSection::new)).codec();
		ConfigSpec<SampleSection> spec = ConfigSpec.of(SampleSection.class);
		SampleSection at30 = new SampleSection("", 30);
		assertEquals(SampleSection.DEFAULT, ConfigEdits.apply(spec, leavesOut, at30, List.of(), Map.of("interval", "10"), JsonOps.INSTANCE).getOrThrow());
		assertEquals(20, ConfigEdits.apply(spec, leavesOut, at30, List.of(), Map.of("interval", "20"), JsonOps.INSTANCE).getOrThrow().interval());
	}

	@Test
	public void valuesTheCodecWritesDifferentlyAreNotMistakenForALostKey() {
		Sample s = edit(List.of(), Map.of("block", "dirt", "speed", "3", "count", "4", "limit", "")).getOrThrow();
		assertEquals("minecraft:dirt", s.block().toString());
		assertEquals(3.0, s.speed());
		assertEquals(Optional.empty(), s.limit());
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
	public void resetOfANestedPageKeepsEveryOtherValue() {
		Sample start = edit(List.of(), Map.of("count", "2", "limit", "4", "words", "x\ny", "mode", "slow")).getOrThrow();
		Sample changed = ConfigEdits.apply(SPEC, CODEC, start, List.of("store"), Map.of("url", "db://z", "interval", "30"), JsonOps.INSTANCE).getOrThrow();
		Sample reset = ConfigEdits.reset(SPEC, CODEC, changed, List.of("store"), JsonOps.INSTANCE).getOrThrow();
		assertEquals(new Sample(start.enabled(), 2, start.speed(), start.share(), Optional.of(4.0), start.title(), start.block(),
				Sample.Mode.SLOW, List.of("x", "y"), SampleSection.DEFAULT, start.renamed()), reset);
	}

	@Test
	public void resetOfAPageTwoLevelsDownReplacesOnlyThatSection() {
		ConfigSpec<Outer> spec = ConfigSpec.of(Outer.class);
		Codec<Outer> codec = Outer.CODEC.codec();
		Outer current = new Outer(7, new Middle(5, new SampleSection("db://deep", 30)));
		Outer reset = ConfigEdits.reset(spec, codec, current, List.of("middle", "store"), JsonOps.INSTANCE).getOrThrow();
		assertEquals(new Outer(7, new Middle(5, SampleSection.DEFAULT)), reset);
		assertEquals(new Outer(7, Middle.DEFAULT), ConfigEdits.reset(spec, codec, current, List.of("middle"), JsonOps.INSTANCE).getOrThrow());
		assertEquals(Outer.DEFAULT, ConfigEdits.reset(spec, codec, current, List.of(), JsonOps.INSTANCE).getOrThrow());
	}

	@Config(name = "Outer")
	public record Outer(@Option(description = "N.") int n, @Option(description = "Middle.") Middle middle) {
		public static final Outer DEFAULT = new Outer(1, Middle.DEFAULT);
		static final com.mojang.serialization.MapCodec<Outer> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.INT.fieldOf("n").forGetter(Outer::n),
				Middle.CODEC.codec().fieldOf("middle").forGetter(Outer::middle)
		).apply(i, Outer::new));
	}

	@Config(name = "Middle")
	public record Middle(@Option(description = "M.") int m, @Option(description = "Store.") SampleSection store) {
		public static final Middle DEFAULT = new Middle(2, SampleSection.DEFAULT);
		static final com.mojang.serialization.MapCodec<Middle> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.INT.fieldOf("m").forGetter(Middle::m),
				SampleSection.CODEC.codec().fieldOf("store").forGetter(Middle::store)
		).apply(i, Middle::new));
	}

	@Test
	public void resetOnUnknownPageIsRefused() {
		String message = ConfigEdits.reset(SPEC, CODEC, Sample.DEFAULT, List.of("nope"), JsonOps.INSTANCE)
				.error().orElseThrow().message();
		assertTrue(message.contains("nope"));
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
