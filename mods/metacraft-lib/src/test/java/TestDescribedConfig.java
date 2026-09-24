import fixtures.*;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestDescribedConfig {

	@BeforeEach
	public void clear() {
		ConfigRegistry.clearForTests();
	}

	private static ConfigContainer<Sample> build(Path file) {
		return ConfigContainer.Builder.create(Sample.CODEC, () -> Sample.DEFAULT)
				.describedBy(Sample.class).build(file);
	}

	/** The file {@code DEFAULT} writes, with one key changed by hand. */
	private static String defaultsWith(String key, com.google.gson.JsonElement value) {
		var json = Sample.CODEC.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, Sample.DEFAULT).getOrThrow().getAsJsonObject();
		json.add(key, value);
		return json.toString();
	}

	@Test
	public void registersUnderTheFileName(@TempDir Path dir) {
		build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		assertEquals("Sample", config.spec().name());
	}

	@Test
	public void appliesAnEditThroughTheContainer(@TempDir Path dir) throws Exception {
		var container = build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		assertTrue(config.apply(List.of(), Map.of("count", "3"), config.hash()).error().isEmpty());
		assertEquals(3, container.get().count());
		assertTrue(Files.readString(dir.resolve("sample.json")).contains("\"count\": 3"));
	}

	@Test
	public void aStaleSaveIsRefused(@TempDir Path dir) throws Exception {
		var container = build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		int opened = config.hash();
		Files.writeString(dir.resolve("sample.json"), defaultsWith("count", new com.google.gson.JsonPrimitive(8)));   // edited by hand
		container.reload();
		var result = config.apply(List.of(), Map.of("count", "3"), opened);
		assertEquals(DescribedConfig.STALE, result.error().orElseThrow().message());
		assertEquals(8, container.get().count());
	}

	@Test
	public void restartOptionsArePendingUntilRestart(@TempDir Path dir) {
		build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		assertEquals(List.of(), config.pendingRestart());   // the listener/pendingRestart itself records the start value
		config.apply(List.of(), Map.of("mode", "slow"), config.hash());
		assertEquals(List.of("Mode."), config.pendingRestart());
		config.apply(List.of(), Map.of("mode", "fast"), config.hash());
		assertEquals(List.of(), config.pendingRestart());
	}

	@Test
	public void aReloadBeforeTheScreenOpensStillLeavesARestartOptionPending(@TempDir Path dir) throws Exception {
		var container = build(dir.resolve("sample.json"));
		ConfigRegistry.serverStarted();   // what a real server does at startup, before /config is ever opened
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		Files.writeString(dir.resolve("sample.json"), defaultsWith("mode", new com.google.gson.JsonPrimitive("slow")));   // edited by hand while the server was down
		container.reload();
		assertEquals(List.of("Mode."), config.pendingRestart());
	}

	@Test
	public void afterAServerStopAndRestartNothingIsPending(@TempDir Path dir) {
		build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		ConfigRegistry.serverStarted();
		config.apply(List.of(), Map.of("mode", "slow"), config.hash());
		assertEquals(List.of("Mode."), config.pendingRestart());
		ConfigRegistry.serverStopped();
		ConfigRegistry.serverStarted();   // as if a new world/server starts, now running "slow"
		assertEquals(List.of(), config.pendingRestart());
	}

	@Test
	public void resetsOnePage(@TempDir Path dir) {
		var container = build(dir.resolve("sample.json"));
		DescribedConfig<?> config = ConfigRegistry.find("sample").orElseThrow();
		config.apply(List.of("store"), Map.of("url", "x"), config.hash());
		config.apply(List.of(), Map.of("count", "2"), config.hash());
		config.reset(List.of("store"), config.hash());
		assertEquals(SampleSection.DEFAULT, container.get().store());
		assertEquals(2, container.get().count());
	}

	@Test
	public void aDuplicateIdIsRefused(@TempDir Path dir) {
		build(dir.resolve("sample.json"));
		assertThrows(IllegalStateException.class, () -> build(dir.resolve("other/sample.json")));
	}

	@Test
	public void aHandWrittenCodecMustWriteEveryKey(@TempDir Path dir) {
		var handWritten = com.mojang.serialization.codecs.RecordCodecBuilder.<SampleSection>mapCodec(i -> i.group(
				com.mojang.serialization.Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
				com.mojang.serialization.Codec.INT.fieldOf("seconds").forGetter(SampleSection::interval)
		).apply(i, SampleSection::new));
		ConfigSpecException thrown = assertThrows(ConfigSpecException.class, () -> ConfigContainer.Builder.create(handWritten, () -> SampleSection.DEFAULT)
				.describedBy(SampleSection.class).build(dir.resolve("s.json")));
		assertTrue(thrown.getMessage().contains("interval"));
	}

	@Test
	public void anOptionTheScreenCannotEditIsShownReadOnly(@TempDir Path dir) {
		ConfigContainer.Builder.create(WithList.CODEC, () -> WithList.DEFAULT).describedBy(WithList.class).build(dir.resolve("weights.json"));
		DescribedConfig<?> config = ConfigRegistry.find("weights").orElseThrow();
		OptionSpec weights = config.spec().option("weights").orElseThrow();
		assertEquals(OptionKind.READ_ONLY, weights.kind());
		assertFalse(weights.editable());
	}

	@Test
	public void describingTheWrongRecordIsRefused(@TempDir Path dir) {
		assertThrows(ConfigSpecException.class, () -> ConfigContainer.Builder.create(SampleSection.CODEC, () -> SampleSection.DEFAULT)
				.describedBy(Sample.class).build(dir.resolve("wrong.json")));
	}

	/** Stores SampleSection's interval as "interval_seconds", left out at 10: the startup check cannot see it. */
	@Config(name = "Renamed")
	public record Renamed(@Option(description = "Store.") SampleSection store) {
		public static final Renamed DEFAULT = new Renamed(SampleSection.DEFAULT);
		static final com.mojang.serialization.MapCodec<Renamed> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
				com.mojang.serialization.codecs.RecordCodecBuilder.<SampleSection>mapCodec(j -> j.group(
						com.mojang.serialization.Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
						com.mojang.serialization.Codec.INT.optionalFieldOf("interval_seconds", 10).forGetter(SampleSection::interval)
				).apply(j, SampleSection::new)).codec().fieldOf("store").forGetter(Renamed::store)
		).apply(i, Renamed::new));
	}

	@Test
	public void anEditTheCodecDoesNotStoreIsRefusedAndNothingIsSaved(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("renamed.json");
		var container = ConfigContainer.Builder.create(Renamed.CODEC, () -> Renamed.DEFAULT).describedBy(Renamed.class).build(file);
		DescribedConfig<?> config = ConfigRegistry.find("renamed").orElseThrow();
		container.get();   // writes the file
		String before = Files.readString(file);

		var result = config.apply(List.of("store"), Map.of("interval", "30"), config.hash());

		assertEquals("interval (Seconds between syncs.): the mod's config file does not store this option under \"interval\"; "
				+ "nothing was saved. Edit the file instead.", result.error().orElseThrow().message());
		assertEquals(before, Files.readString(file));
		assertEquals(Renamed.DEFAULT, container.get());
		assertTrue(config.apply(List.of("store"), Map.of("url", "db://ok"), config.hash()).error().isEmpty());   // other keys still save
	}
}
