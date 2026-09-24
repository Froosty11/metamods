import com.google.gson.JsonParser;
import nu.metacraft.config.source.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestPolyDecorationsSource {
	private static final String FORK_FILE = """
			{
				"features": {
					"canvas": true,
					"bench": false,
					"statues": false
				},
				"something_else": 1
			}
			""";

	@Test
	public void onlyTheForksFormatIsPickedUp(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		assertTrue(PolyDecorationsSource.detect(file).isEmpty());   // no file
		Files.writeString(file, "{\"canvas\": true}");
		assertTrue(PolyDecorationsSource.detect(file).isEmpty());   // not the fork's format
		Files.writeString(file, FORK_FILE);
		assertTrue(PolyDecorationsSource.detect(file).isPresent());
	}

	@Test
	public void everyFeatureIsARestartToggle(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		Page page = PolyDecorationsSource.detect(file).orElseThrow().page(List.of());
		assertEquals(List.of("canvas", "bench", "statues"), page.fields().stream().map(Field::key).toList());
		assertTrue(page.fields().stream().allMatch(f -> f.restart() && f.kind() == nu.metacraft.lib.config.describe.OptionKind.BOOLEAN));
		assertEquals("false", page.fields().get(1).value());
	}

	@Test
	public void anEmptyFeatureKeyIsSkipped(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, "{\"features\": {\"\": true, \"canvas\": false}}");
		ConfigSource source = PolyDecorationsSource.detect(file).orElseThrow();
		assertEquals(List.of("canvas"), source.page(List.of()).fields().stream().map(Field::key).toList());
		Files.writeString(file, "{\"features\": {\"\": false, \"canvas\": true}}");
		assertEquals(List.of("Canvas on"), source.pendingRestart());
	}

	@Test
	public void writesBackKeepingOtherKeys(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		ConfigSource source = PolyDecorationsSource.detect(file).orElseThrow();
		assertEquals(new EditOutcome.Saved(), source.apply(List.of(), Map.of("bench", "true"), source.hash()));
		var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertTrue(json.getAsJsonObject("features").get("bench").getAsBoolean());
		assertEquals(1, json.get("something_else").getAsInt());
		assertEquals(List.of("bench"), source.pendingRestart().stream().map(s -> s.split(" ")[0].toLowerCase()).toList());
	}

	@Test
	public void refusesStaleAndBadValues(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		ConfigSource source = PolyDecorationsSource.detect(file).orElseThrow();
		assertEquals(new EditOutcome.Stale(), source.apply(List.of(), Map.of("bench", "true"), source.hash() + 1));
		assertInstanceOf(EditOutcome.Refused.class, source.apply(List.of(), Map.of("bench", "maybe"), source.hash()));
		assertInstanceOf(EditOutcome.Refused.class, source.apply(List.of(), Map.of("warp_drive", "true"), source.hash()));
	}

	@Test
	public void aBrokenFileIsReportedNotOverwrittenUntilSaved(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		ConfigSource source = PolyDecorationsSource.detect(file).orElseThrow();
		Files.writeString(file, "{ broken");
		assertTrue(source.loadError().isPresent());
		assertEquals("{ broken", Files.readString(file));
	}

	@Test
	public void applyAndResetRefuseAnUnknownPageInsteadOfThrowing(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		ConfigSource source = PolyDecorationsSource.detect(file).orElseThrow();
		assertInstanceOf(EditOutcome.Refused.class, source.apply(List.of("x"), Map.of("bench", "true"), source.hash()));
		assertInstanceOf(EditOutcome.Refused.class, source.reset(List.of("x"), source.hash()));
	}

	@Test
	public void resetTurnsEveryFeatureOnAndHonoursTheStaleHash(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		ConfigSource source = PolyDecorationsSource.detect(file).orElseThrow();
		assertEquals(new EditOutcome.Stale(), source.reset(List.of(), source.hash() + 1));
		assertEquals(new EditOutcome.Saved(), source.reset(List.of(), source.hash()));
		var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		for (var entry : json.getAsJsonObject("features").entrySet()) {
			assertTrue(entry.getValue().getAsBoolean());
		}
	}

	@Test
	public void aReadFailureRefusesApplyRatherThanOverwriting(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("polydecorations.json");
		Files.writeString(file, FORK_FILE);
		ConfigSource source = PolyDecorationsSource.detect(file).orElseThrow();
		Files.writeString(file, "{ broken");
		assertInstanceOf(EditOutcome.Refused.class, source.apply(List.of(), Map.of("bench", "true"), source.hash()));
		assertEquals("{ broken", Files.readString(file));
	}
}
