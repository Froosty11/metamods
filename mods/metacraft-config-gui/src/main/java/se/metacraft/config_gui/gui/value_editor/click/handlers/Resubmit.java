package se.metacraft.config_gui.gui.value_editor.click.handlers;

import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerType;

public class Resubmit implements ClickHandler {

	public static final Resubmit INSTANCE = new Resubmit();

	private Resubmit() {}

	@Override
	public DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui) {
		var dialog = gui.createDialog(player);
		if (dialog.value().common().afterAction() == DialogAction.NONE) {
			player.openDialog(dialog);
		}
		return gui;
	}

	@Override
	public ClickHandlerType<Resubmit> type() {
		return ClickHandlerRegistry.RESUBMIT;
	}
}
