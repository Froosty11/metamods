import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import fixtures.*;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestDescribedCodec {
	private static final Codec<Sample> CODEC = ConfigSpec.of(Sample.class).codec().codec();

	private static JsonObject encode(Sample value) {
		return CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject();
	}

	@Test
	public void writesSnakeCaseKeysAndHelp() {
		JsonObject json = encode(Sample.DEFAULT);
		assertTrue(json.get("enabled").getAsBoolean());
		assertEquals(6, json.get("count").getAsInt());
		assertEquals("fast", json.get("mode").getAsString());
		assertEquals("minecraft:stone", json.get("block").getAsString());
		assertEquals(10, json.getAsJsonObject("store").get("interval").getAsInt());
		assertEquals(3, json.get("old-name").getAsInt());
		assertFalse(json.has("limit"));   // an empty Optional is left out
		assertEquals("How many.", json.getAsJsonObject("_help").get("count").getAsString());
	}

	@Test
	public void roundTrips() {
		Sample changed = new Sample(false, 2, 12.5, 0.25, Optional.of(3.0), "yo", Sample.DEFAULT.block(),
				Sample.Mode.SLOW, List.of("x", "y"), new SampleSection("db://x", 30), 9);
		assertEquals(changed, CODEC.parse(JsonOps.INSTANCE, encode(changed)).getOrThrow());
	}

	@Test
	public void missingKeysAreDefaults() {
		Sample read = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"count\": 4}")).getOrThrow();
		assertEquals(4, read.count());
		assertEquals(Sample.DEFAULT.title(), read.title());
		assertEquals(Sample.DEFAULT.store(), read.store());
	}

	@Test
	public void refusesOutOfRangeAndNamesTheKey() {
		var result = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"count\": 40}"));
		assertTrue(result.error().isPresent());
		assertTrue(result.error().get().message().contains("count"), result.error().get().message());
	}

	@Test
	public void aBadKeyFallsBackToItsDefaultAndKeepsTheRest() {
		var result = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"count\": 40, \"title\": \"kept\", \"speed\": 7}"));
		assertEquals("count: 40 is not in (1 – 16)", result.error().orElseThrow().message());
		Sample partial = result.resultOrPartial().orElseThrow();
		assertEquals(Sample.DEFAULT.count(), partial.count());
		assertEquals("kept", partial.title());
		assertEquals(7, partial.speed());
	}

	@Test
	public void everyBadKeyIsReportedInOrder() {
		var result = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"{\"count\": 40, \"mode\": \"warp\", \"store\": {\"url\": \"db://kept\", \"interval\": 999}}"));
		String message = result.error().orElseThrow().message();
		assertTrue(message.startsWith("count: 40 is not in (1 – 16); mode: "), message);
		assertTrue(message.contains("; store: interval: 999 is not in"), message);
		Sample partial = result.resultOrPartial().orElseThrow();
		assertEquals(Sample.Mode.FAST, partial.mode());
		assertEquals(new SampleSection("db://kept", 10), partial.store());   // a section keeps its good keys
	}

	@Test
	public void refusesUnknownChoice() {
		assertTrue(CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"mode\": \"warp\"}")).error().isPresent());
	}

	@Test
	public void cannotGenerateForUnsupportedTypes() {
		assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(WithList.class).codec());
	}

	@Test
	public void checksHandWrittenCodecsForMissingKeys() {
		MapCodec<SampleSection> handWritten = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
				Codec.INT.fieldOf("seconds").forGetter(SampleSection::interval)   // not "interval"
		).apply(i, SampleSection::new));
		var error = assertThrows(ConfigSpecException.class, () -> ConfigSpec.of(SampleSection.class).checkWrittenBy(handWritten.codec()));
		assertTrue(error.getMessage().contains("interval"));
	}
}
