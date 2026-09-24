import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fixtures.*;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.ConfigSpec;
import nu.metacraft.lib.config.extensions.Modifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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

	@Test
	public void modifyNeverSavesOverABrokenFile(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("m.json");
		String broken = "not json";
		Files.writeString(file, broken);
		var container = ConfigContainer.Builder.create(MutableSection.CODEC, () -> new MutableSection("", 10)).build(file);

		//noinspection deprecation
		container.modify(s -> {
			s.interval = 42;
			return true;
		});
		container.get();   // loads (fails), queues nothing more
		container.get();   // used to flush the queued modifier and save over the broken file

		assertEquals(broken, Files.readString(file));
		assertTrue(container.loadError().isPresent());
	}

	@Test
	public void aPartialParseKeepsWhatParsedAndReportsTheError(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("p.json");
		String content = "{\"url\": \"good\", \"interval\": 999}";
		Files.writeString(file, content);
		var container = ConfigContainer.Builder.create(PartialSection.CODEC, () -> PartialSection.DEFAULT).build(file);

		PartialSection result = container.get();

		assertEquals("good", result.url());   // the part that did parse is kept
		assertTrue(container.loadError().orElseThrow().contains("interval"));
		assertEquals(content, Files.readString(file));   // never saved over
	}

	/** A mutable, {@link Modifiable} config: the only shape {@code modify} needs. */
	private static final class MutableSection implements Modifiable {
		String url;
		int interval;
		private boolean modified;

		MutableSection(String url, int interval) {
			this.url = url;
			this.interval = interval;
		}

		@Override
		public void setModified(boolean modified) {
			this.modified = modified;
		}

		@Override
		public boolean isModified() {
			return modified;
		}

		static final MapCodec<MutableSection> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.STRING.fieldOf("url").forGetter(s -> s.url),
				Codec.INT.fieldOf("interval").forGetter(s -> s.interval)
		).apply(instance, MutableSection::new));
	}

	/**
	 * A record with a hand-written codec that, unlike the generated {@code @Config} codecs, keeps
	 * a partial value (the fields that did parse) alongside the error, exercising the
	 * {@code resultOrPartial} path in {@code BasicConfigContainer}.
	 */
	private record PartialSection(String url, int interval) {
		static final PartialSection DEFAULT = new PartialSection("", 10);

		static final MapCodec<PartialSection> CODEC = new MapCodec<>() {
			@Override
			public <T> Stream<T> keys(DynamicOps<T> ops) {
				return Stream.of(ops.createString("url"), ops.createString("interval"));
			}

			@Override
			public <T> DataResult<PartialSection> decode(DynamicOps<T> ops, MapLike<T> input) {
				T urlRaw = input.get("url");
				String url = urlRaw != null ? ops.getStringValue(urlRaw).result().orElse(DEFAULT.url()) : DEFAULT.url();

				T intervalRaw = input.get("interval");
				if (intervalRaw == null) return DataResult.success(new PartialSection(url, DEFAULT.interval()));

				Optional<Number> parsed = ops.getNumberValue(intervalRaw).result();
				if (parsed.isEmpty()) {
					return DataResult.error(() -> "interval: not a number", new PartialSection(url, DEFAULT.interval()));
				}
				int interval = parsed.get().intValue();
				if (interval < 1 || interval > 60) {
					return DataResult.error(() -> "interval: out of range", new PartialSection(url, DEFAULT.interval()));
				}
				return DataResult.success(new PartialSection(url, interval));
			}

			@Override
			public <T> RecordBuilder<T> encode(PartialSection input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				prefix.add("url", ops.createString(input.url()));
				prefix.add("interval", ops.createNumeric(input.interval()));
				return prefix;
			}
		};
	}
}
