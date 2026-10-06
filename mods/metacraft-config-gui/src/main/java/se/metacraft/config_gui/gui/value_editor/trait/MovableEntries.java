package se.metacraft.config_gui.gui.value_editor.trait;

import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.handlers.MoveEntry;

public interface MovableEntries {

	DialogGUI moveEntry(int index, MoveEntry.MoveDirection direction);

}
