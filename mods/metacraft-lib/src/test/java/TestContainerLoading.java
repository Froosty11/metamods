import com.mojang.serialization.Codec;
import fixtures.*;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.ConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestContainerLoading {

	private static ConfigContainer<SampleSection> container(Path file) {
		return ConfigContainer.Builder.create(ConfigSpec.of(SampleSection.class).codec(), () -> SampleSection.DEFAULT).build(file);
	}

	@Test
	public void writesDefaultsWhenThereIsNoFile(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		assertEquals(SampleSection.DEFAULT, container(file).get());
		assertTrue(Files.readString(file).contains("\"interval\""));
	}

	@Test
	public void aBrokenFileIsLeftAloneAndReported(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		Files.writeString(file, "{\"interval\": 999}");
		var container = container(file);
		assertEquals(SampleSection.DEFAULT, container.get());
		assertEquals("{\"interval\": 999}", Files.readString(file));
		assertTrue(container.loadError().orElseThrow().contains("interval"));
		try (var files = Files.list(dir)) {
			assertEquals(1, files.count());   // no .bak
		}
	}

	@Test
	public void aReloadThatBreaksKeepsTheLastGoodValue(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		Files.writeString(file, "{\"url\": \"a\", \"interval\": 5}");
		var container = container(file);
		assertEquals(5, container.get().interval());
		Files.writeString(file, "{\"interval\": ");
		container.reload();
		assertEquals(5, container.get().interval());
		assertTrue(container.loadError().isPresent());
		Files.writeString(file, "{\"url\": \"a\", \"interval\": 6}");
		container.reload();
		assertEquals(6, container.get().interval());
		assertTrue(container.loadError().isEmpty());
	}

	@Test
	public void updateSavesAndNotifies(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		var container = container(file);
		List<String> seen = new ArrayList<>();
		container.addChangeListener((old, current) -> seen.add(old.interval() + "->" + current.interval()));
		container.update(s -> new SampleSection(s.url(), 42 % 60));
		assertEquals(List.of("10->42"), seen);
		assertTrue(Files.readString(file).contains("42"));
		container.update(s -> s);   // no change, no event
		assertEquals(1, seen.size());
	}

	@Test
	public void savingOverABrokenFileClearsTheError(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		Files.writeString(file, "not json");
		var container = container(file);
		container.get();
		assertTrue(container.loadError().isPresent());
		container.update(s -> new SampleSection("fixed", 3));
		assertTrue(container.loadError().isEmpty());
		assertTrue(Files.readString(file).contains("fixed"));
	}

	@Test
	public void reloadNotifiesWhenTheFileChanged(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("s.json");
		var container = container(file);
		container.get();
		List<Integer> seen = new ArrayList<>();
		container.addChangeListener((old, current) -> seen.add(current.interval()));
		Files.writeString(file, "{\"interval\": 7}");
		container.reload();
		assertEquals(List.of(7), seen);
	}
}
