package se.metacraft.config_gui.gui.value_editor.click.handlers;

import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerType;

public class Back implements ClickHandler {

	public static final Back INSTANCE = new Back();

	private Back() {}

	@Override
	public DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui) {
		return gui.parent().map(
			parent -> parent.withUpdatedGUI(gui, player.registryAccess())
		).orElse(null);
	}

	@Override
	public ClickHandlerType<Back> type() {
		return ClickHandlerRegistry.BACK;
	}
}
