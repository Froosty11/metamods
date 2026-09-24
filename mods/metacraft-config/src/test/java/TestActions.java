import fixtures.Demo;
import net.minecraft.server.dialog.Dialog;
import nu.metacraft.config.screen.Actions;
import nu.metacraft.config.screen.Pages;
import nu.metacraft.config.source.ConfigSource;
import nu.metacraft.config.source.Sources;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.describe.ConfigRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actions.configDialog/resetConfirmDialog are the guard a crafted-payload page path goes
 * through before a dialog is sent; these test the guard directly, without a ServerPlayer.
 */
@ExtendWith(TestInit.class)
public class TestActions {
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

	@Test
	public void configDialogFallsBackToMainForAPageThatDoesNotExist(@TempDir Path dir) {
		ConfigSource source = demo(dir);
		Dialog dialog = Actions.configDialog(source, List.of("nope"), Optional.empty(), Map.of());
		assertEquals(Pages.main(Sources.all()), dialog);
	}

	@Test
	public void resetConfirmDialogFallsBackToMainForAPageThatDoesNotExist(@TempDir Path dir) {
		ConfigSource source = demo(dir);
		Dialog dialog = Actions.resetConfirmDialog(source, List.of("nope"));
		assertEquals(Pages.main(Sources.all()), dialog);
	}

	@Test
	public void configDialogStillShowsARealPage(@TempDir Path dir) {
		ConfigSource source = demo(dir);
		Dialog dialog = Actions.configDialog(source, List.of(), Optional.empty(), Map.of());
		assertEquals(Pages.config(source, List.of(), Optional.empty(), Map.of()), dialog);
	}

	@Test
	public void anErrorWithoutAMessageIsNamedByItsClass() {
		assertEquals("java.lang.NullPointerException", Actions.describe(new NullPointerException()));
		assertEquals("boom", Actions.describe(new IllegalStateException("boom")));
	}
}
