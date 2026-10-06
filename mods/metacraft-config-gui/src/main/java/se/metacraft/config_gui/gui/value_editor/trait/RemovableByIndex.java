package se.metacraft.config_gui.gui.value_editor.trait;

import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.DialogGUI;

public interface RemovableByIndex {

	DialogGUI remove(ServerPlayer player, int index);

}
