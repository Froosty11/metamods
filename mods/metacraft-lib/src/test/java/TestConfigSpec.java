import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fixtures.*;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.stream.Stream;

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

	@Test
	public void aCodecThatWritesEveryKeyPassesTheCheck() {
		assertDoesNotThrow(() -> ConfigSpec.of(Sample.class).checkWrittenBy(Sample.CODEC));   // an empty Optional may be left out
		assertDoesNotThrow(() -> ConfigSpec.of(WithList.class).checkWrittenBy(WithList.CODEC));
	}

	@Test
	public void checksHandWrittenCodecsForMissingKeys() {
		MapCodec<SampleSection> handWritten = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
				Codec.INT.fieldOf("seconds").forGetter(SampleSection::interval)   // not "interval"
		).apply(i, SampleSection::new));
		var error = assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(SampleSection.class).checkWrittenBy(handWritten));
		assertEquals("SampleSection.interval: the codec writes no \"interval\"", error.getMessage());
	}

	@Config(name = "Defaulted")
	public record Defaulted(@Option(description = "Size.") int size, @Option(description = "Name.") String name) {
		public static final Defaulted DEFAULT = new Defaulted(4, "x");
		static final MapCodec<Defaulted> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.INT.optionalFieldOf("size", 4).forGetter(Defaulted::size),   // left out when it is 4
				Codec.STRING.fieldOf("name").forGetter(Defaulted::name)
		).apply(i, Defaulted::new));
	}

	@Test
	public void aKeyLeftOutWhenAtItsDefaultStillCounts() {
		assertFalse(Defaulted.CODEC.codec().encodeStart(JsonOps.INSTANCE, Defaulted.DEFAULT).getOrThrow().getAsJsonObject().has("size"));
		assertDoesNotThrow(() -> ConfigSpec.of(Defaulted.class).checkWrittenBy(Defaulted.CODEC));
	}

	@Test
	public void aCodecThatDeclaresNoKeysIsCheckedByWritingDefault() {
		assertEquals("SampleSection.interval: the codec writes no \"interval\"", assertThrows(ConfigSpecException.class,
				() -> ConfigSpec.of(SampleSection.class).checkWrittenBy(undeclared(false))).getMessage());
		assertDoesNotThrow(() -> ConfigSpec.of(SampleSection.class).checkWrittenBy(undeclared(true)));
	}

	/** A custom codec whose {@code keys} is empty; it writes {@code interval} only when asked to. */
	private static MapCodec<SampleSection> undeclared(boolean writesInterval) {
		return new MapCodec<>() {
			@Override
			public <T> Stream<T> keys(DynamicOps<T> ops) {
				return Stream.empty();
			}

			@Override
			public <T> DataResult<SampleSection> decode(DynamicOps<T> ops, MapLike<T> input) {
				return DataResult.success(SampleSection.DEFAULT);
			}

			@Override
			public <T> RecordBuilder<T> encode(SampleSection input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				prefix.add("url", ops.createString(input.url()));
				if (writesInterval) prefix.add("interval", ops.createInt(input.interval()));
				return prefix;
			}
		};
	}

	/** SampleSection's codec, but writing {@code interval} as {@code interval_seconds}. */
	private static final Codec<SampleSection> DRIFTED_SECTION = RecordCodecBuilder.<SampleSection>mapCodec(i -> i.group(
			Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
			Codec.INT.fieldOf("interval_seconds").forGetter(SampleSection::interval)
	).apply(i, SampleSection::new)).codec();

	@Config(name = "Holder")
	public record Holder(@Option(description = "Store.") SampleSection store) {
		public static final Holder DEFAULT = new Holder(SampleSection.DEFAULT);
	}

	@Config(name = "Deep")
	public record Deep(@Option(description = "Inner.") HolderOff inner) {
		public static final Deep DEFAULT = new Deep(HolderOff.DEFAULT);
	}

	@Test
	public void aSectionCodecWithADifferentKeyFailsNamingThePath() {
		// HolderOff's interval is not SampleSection's default, so a missing "interval" is not a left-out default.
		MapCodec<HolderOff> drifted = RecordCodecBuilder.mapCodec(i -> i.group(
				DRIFTED_SECTION.fieldOf("store").forGetter(HolderOff::store)
		).apply(i, HolderOff::new));
		assertEquals("SampleSection.interval: the codec writes no \"store.interval\"",
				assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(HolderOff.class).checkWrittenBy(drifted)).getMessage());

		MapCodec<Deep> deep = RecordCodecBuilder.mapCodec(i -> i.group(
				drifted.codec().fieldOf("inner").forGetter(Deep::inner)
		).apply(i, Deep::new));
		assertEquals("SampleSection.interval: the codec writes no \"inner.store.interval\"",
				assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(Deep.class).checkWrittenBy(deep)).getMessage());
	}

	@Test
	public void aSectionLeftOutAtItsDefaultIsNotLookedInto() {
		// ovvar's shape: optionalFieldOf("designs", DesignStoreConfig.DEFAULT) writes nothing for DEFAULT.
		MapCodec<Holder> leftOut = RecordCodecBuilder.mapCodec(i -> i.group(
				SampleSection.CODEC.codec().optionalFieldOf("store", SampleSection.DEFAULT).forGetter(Holder::store)
		).apply(i, Holder::new));
		assertDoesNotThrow(() -> ConfigSpec.of(Holder.class).checkWrittenBy(leftOut));
	}

	@Test
	public void aSectionWrittenAsSomethingElseFails() {
		MapCodec<Holder> flat = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.xmap(url -> new SampleSection(url, 10), SampleSection::url).fieldOf("store").forGetter(Holder::store)
		).apply(i, Holder::new));
		assertEquals("Holder.store: the codec does not write \"store\" as an object",
				assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(Holder.class).checkWrittenBy(flat)).getMessage());
	}

	@Config(name = "Holder off default")
	public record HolderOff(@Option(description = "Store.") SampleSection store) {
		public static final HolderOff DEFAULT = new HolderOff(new SampleSection("", 30));   // interval not SampleSection's default
	}

	@Test
	public void aWrittenSectionMayLeaveOutAFieldAtItsDefault() {
		MapCodec<Holder> codec = RecordCodecBuilder.mapCodec(i -> i.group(
				RecordCodecBuilder.<SampleSection>mapCodec(j -> j.group(
						Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
						Codec.INT.optionalFieldOf("interval", 10).forGetter(SampleSection::interval)   // left out: 10 is the default
				).apply(j, SampleSection::new)).codec().fieldOf("store").forGetter(Holder::store)
		).apply(i, Holder::new));
		assertDoesNotThrow(() -> ConfigSpec.of(Holder.class).checkWrittenBy(codec));
	}

	@Test
	public void aRenamedOptionalFieldAwayFromItsDefaultFails() {
		MapCodec<HolderOff> codec = RecordCodecBuilder.mapCodec(i -> i.group(
				RecordCodecBuilder.<SampleSection>mapCodec(j -> j.group(
						Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
						Codec.INT.optionalFieldOf("interval_seconds", 10).forGetter(SampleSection::interval)
				).apply(j, SampleSection::new)).codec().fieldOf("store").forGetter(HolderOff::store)
		).apply(i, HolderOff::new));
		assertEquals("SampleSection.interval: the codec writes no \"store.interval\"",
				assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(HolderOff.class).checkWrittenBy(codec)).getMessage());
	}

	@Test
	public void aSectionLeftOutAwayFromItsDefaultFails() {
		MapCodec<HolderOff> codec = RecordCodecBuilder.mapCodec(i -> i.group(
				SampleSection.CODEC.codec().optionalFieldOf("store", HolderOff.DEFAULT.store()).forGetter(HolderOff::store)
		).apply(i, HolderOff::new));
		assertEquals("HolderOff.store: the codec writes no \"store\"",
				assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(HolderOff.class).checkWrittenBy(codec)).getMessage());
	}
}
