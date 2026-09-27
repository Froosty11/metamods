package se.metacraft.config_gui;

import java.util.Optional;

public interface DialogGUIExtension {

	void metacraft$openGUI(DialogGUI gui);
	void metacraft$closeGUI();

	Optional<DialogGUI> metacraft$getCurrentGUI();

}
