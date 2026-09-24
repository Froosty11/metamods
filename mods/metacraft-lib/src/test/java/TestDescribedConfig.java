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
		return ConfigContainer.Builder.create(ConfigSpec.of(Sample.class).codec(), () -> Sample.DEFAULT)
				.describedBy(Sample.class).build(file);
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
		Files.writeString(dir.resolve("sample.json"), "{\"count\": 8}");   // edited by hand
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
		Files.writeString(dir.resolve("sample.json"), "{\"mode\": \"slow\"}");   // edited by hand while the server was down
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
	public void describingTheWrongRecordIsRefused(@TempDir Path dir) {
		assertThrows(ConfigSpecException.class, () -> ConfigContainer.Builder.create(ConfigSpec.of(SampleSection.class).codec(), () -> SampleSection.DEFAULT)
				.describedBy(Sample.class).build(dir.resolve("wrong.json")));
	}
}
