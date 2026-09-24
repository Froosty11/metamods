import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import fixtures.Demo;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.*;
import nu.metacraft.config.screen.*;
import nu.metacraft.config.source.*;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
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
		ConfigContainer.Builder.create(Demo.CODEC, () -> Demo.DEFAULT)
				.describedBy(Demo.class).build(dir.resolve("demo.json"));
		return Sources.find("demo").orElseThrow();
	}

	private static void assertEncodes(Dialog dialog) {
		HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
		DataResult<?> result = Dialog.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, lookup), dialog);
		assertTrue(result.error().isEmpty(), () -> result.error().get().message());
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

	private static String written(Demo value) {
		return Demo.CODEC.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow().toString();
	}

	@Test
	public void aFileThatDoesNotLoadShowsWhatTheServerUsesAndTheError(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("demo.json");
		String content = written(new Demo(true, 6, 9, Optional.empty(), false, Demo.Inner.DEFAULT)).replace("\"stitches\":6", "\"stitches\":40");
		Files.writeString(file, content);
		MultiActionDialog page = (MultiActionDialog) Pages.config(demo(dir), List.of(), Optional.empty(), Map.of());
		// RecordCodecBuilder gives nothing back when one field fails, so every value is the default.
		assertEquals("2.5", ((TextInput) page.common().inputs().get(2).control()).initial());
		NumberRangeInput stitches = (NumberRangeInput) page.common().inputs().get(1).control();
		assertEquals(Optional.of(6f), stitches.rangeInfo().initial());
		String body = page.common().body().stream().map(b -> ((PlainMessage) b).contents().getString()).reduce("", String::concat);
		assertTrue(body.contains("Value 40 outside of range [1:16]"), body);
		assertTrue(body.contains("keeps the values it could read; the rest are defaults or the last good values"), body);
		assertEquals(content, Files.readString(file));
	}

	@Test
	public void aValueTooLongForItsInputIsReadOnlyAndNotSent(@TempDir Path dir) throws Exception {
		String name = "n".repeat(2000);
		Files.writeString(dir.resolve("demo.json"), written(new Demo(true, 6, 2.5, Optional.empty(), false, new Demo.Inner(name))));
		ConfigSource source = demo(dir);
		MultiActionDialog page = (MultiActionDialog) Pages.config(source, List.of("inner"), Optional.empty(), Map.of());
		assertTrue(page.common().inputs().isEmpty());
		String body = page.common().body().stream().map(b -> ((PlainMessage) b).contents().getString()).reduce("", String::concat);
		assertTrue(body.contains("Name.: too long to edit here; edit in the file"), body);
		assertEncodes(page);

		CompoundTag payload = new CompoundTag();
		payload.putString(Inputs.key(0), "short");   // a modified client sends it anyway
		assertEquals(Map.of(), Payloads.values(source.page(List.of("inner")), payload));
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

	@Test
	public void emptyMainPageStillHasAButtonAndEncodes() {
		MultiActionDialog main = (MultiActionDialog) Pages.main(List.of());
		assertFalse(main.actions().isEmpty());
		assertEncodes(main);
	}

	@Test
	public void sliderClampsOutOfRangeInitial() {
		Field field = new Field("stitches", "Stitches.", OptionKind.WHOLE, "999", 1, 16, 1, true, List.of(), false, true);
		NumberRangeInput slider = assertInstanceOf(NumberRangeInput.class, Inputs.forField(field, "999"));
		assertEquals(Optional.of(16f), slider.rangeInfo().initial());

		Field belowRange = new Field("stitches", "Stitches.", OptionKind.WHOLE, "-5", 1, 16, 1, true, List.of(), false, true);
		NumberRangeInput lowSlider = assertInstanceOf(NumberRangeInput.class, Inputs.forField(belowRange, "-5"));
		assertEquals(Optional.of(1f), lowSlider.rangeInfo().initial());
	}

	@Test
	public void choiceInputWidensForALongLabelAndStillEncodes() {
		Field field = new Field("mode", "Use vanilla's experimental minecart physics.", OptionKind.CHOICE, "experimental",
				0, 0, 0, false, List.of("legacy", "experimental"), true, true);
		SingleOptionInput input = assertInstanceOf(SingleOptionInput.class, Inputs.forField(field, "experimental"));
		assertTrue(input.width() >= 400, "width was " + input.width());

		Dialog dialog = new MultiActionDialog(
				new CommonDialogData(Component.literal("t"), Optional.empty(), true, false, DialogAction.WAIT_FOR_RESPONSE,
						List.of(), List.of(new Input("o0", input))),
				List.of(new ActionButton(new CommonButtonData(Component.literal("Close"), 150), Optional.empty())),
				Optional.empty(), 1);
		assertEncodes(dialog);
	}

	@Test
	public void configPageEncodesWithVanillaCodec(@TempDir Path dir) {
		assertEncodes(Pages.config(demo(dir), List.of(), Optional.empty(), Map.of()));
	}

	@Test
	public void overlongTypedValueIsTruncatedSoThePageStillEncodes(@TempDir Path dir) {
		assertEncodes(Pages.config(demo(dir), List.of(), Optional.empty(), Map.of("speed", "x".repeat(5000))));
	}
}
