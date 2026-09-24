import fixtures.Demo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.input.*;
import nu.metacraft.config.screen.*;
import nu.metacraft.config.source.*;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestInit.class)
public class TestPages {
	@BeforeEach
	public void clear() {
		ConfigRegistry.clearForTests();
		Sources.clearAdaptersForTests();
	}

	private static ConfigSource demo(Path dir) {
		ConfigContainer.Builder.create(ConfigSpec.of(Demo.class).codec(), () -> Demo.DEFAULT)
				.describedBy(Demo.class).build(dir.resolve("demo.json"));
		return Sources.find("demo").orElseThrow();
	}

	@Test
	public void eachKindGetsItsInput(@TempDir Path dir) {
		MultiActionDialog page = (MultiActionDialog) Pages.config(demo(dir), List.of(), Optional.empty(), Map.of());
		List<Input> inputs = page.common().inputs();
		assertEquals(List.of("o0", "o1", "o2", "o3", "o4"), inputs.stream().map(Input::key).toList());
		assertInstanceOf(BooleanInput.class, inputs.get(0).control());
		NumberRangeInput slider = assertInstanceOf(NumberRangeInput.class, inputs.get(1).control());
		assertEquals(1f, slider.rangeInfo().start());
		assertEquals(16f, slider.rangeInfo().end());
		assertEquals(Optional.of(6f), slider.rangeInfo().initial());
		TextInput speed = assertInstanceOf(TextInput.class, inputs.get(2).control());
		assertEquals("2.5", speed.initial());
		assertInstanceOf(TextInput.class, inputs.get(3).control());   // optional: typed, blank = none
	}

	@Test
	public void saveCarriesSourcePageAndHash(@TempDir Path dir) {
		ConfigSource source = demo(dir);
		MultiActionDialog page = (MultiActionDialog) Pages.config(source, List.of(), Optional.empty(), Map.of());
		ActionButton save = page.actions().getFirst();
		CustomAll action = assertInstanceOf(CustomAll.class, save.action().orElseThrow());
		assertEquals(Pages.SAVE, action.id());
		var additions = action.additions().orElseThrow();
		assertEquals("demo", additions.getStringOr("source", "?"));
		assertEquals("", additions.getStringOr("page", "?"));
		assertEquals(source.hash(), additions.getIntOr("hash", 0));
	}

	@Test
	public void sectionsAreButtons(@TempDir Path dir) {
		MultiActionDialog page = (MultiActionDialog) Pages.config(demo(dir), List.of(), Optional.empty(), Map.of());
		assertTrue(page.actions().stream().anyMatch(b -> b.button().label().getString().equals("Inner.")));
	}

	@Test
	public void refusedValuesArePreFilled(@TempDir Path dir) {
		MultiActionDialog page = (MultiActionDialog) Pages.config(demo(dir), List.of(), Optional.of(Component.literal("bad")),
				Map.of("speed", "fast"));
		assertEquals("fast", ((TextInput) page.common().inputs().get(2).control()).initial());
	}

	@Test
	public void mainPageListsSourcesAndMarksProblems(@TempDir Path dir) {
		ConfigSource source = demo(dir);
		MultiActionDialog main = (MultiActionDialog) Pages.main(List.of(source));
		assertEquals("Demo", main.actions().getFirst().button().label().getString());
	}
}
