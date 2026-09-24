import fixtures.Demo;
import nu.metacraft.config.source.*;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestDescribedSource {
	@BeforeEach
	public void clear() {
		ConfigRegistry.clearForTests();
		Sources.clearAdaptersForTests();
	}

	private static ConfigContainer<Demo> demo(Path dir) {
		return ConfigContainer.Builder.create(ConfigSpec.of(Demo.class).codec(), () -> Demo.DEFAULT)
				.describedBy(Demo.class).build(dir.resolve("demo.json"));
	}

	@Test
	public void listsRegisteredConfigs(@TempDir Path dir) {
		demo(dir);
		assertEquals(List.of("demo"), Sources.all().stream().map(ConfigSource::id).toList());
		assertEquals("Demo", Sources.find("demo").orElseThrow().name());
	}

	@Test
	public void describesThePage(@TempDir Path dir) {
		demo(dir);
		Page page = Sources.find("demo").orElseThrow().page(List.of());
		assertEquals("Demo", page.title());
		assertEquals(List.of("on", "stitches", "speed", "cap", "heavy"), page.fields().stream().map(Field::key).toList());
		Field stitches = page.fields().get(1);
		assertTrue(stitches.slider());
		assertEquals("6", stitches.value());
		assertEquals("Stitches. (1 – 16)", stitches.label());
		assertEquals("", page.fields().get(3).value());
		assertTrue(page.fields().get(4).restart());
		assertEquals(List.of(new Link("inner", "Inner.")), page.sections());
		assertEquals("Inner", Sources.find("demo").orElseThrow().page(List.of("inner")).title());
	}

	@Test
	public void savesAndReportsOutcomes(@TempDir Path dir) {
		var container = demo(dir);
		ConfigSource source = Sources.find("demo").orElseThrow();
		assertEquals(new EditOutcome.Saved(), source.apply(List.of(), Map.of("speed", "4"), source.hash()));
		assertEquals(4.0, container.get().speed());
		assertInstanceOf(EditOutcome.Refused.class, source.apply(List.of(), Map.of("speed", "fast"), source.hash()));
		assertEquals(new EditOutcome.Stale(), source.apply(List.of(), Map.of("speed", "5"), source.hash() + 1));
	}

	@Test
	public void unknownPageThrows(@TempDir Path dir) {
		demo(dir);
		assertThrows(IllegalArgumentException.class, () -> Sources.find("demo").orElseThrow().page(List.of("nope")));
	}
}
