package se.metacraft.config_gui;

import se.metacraft.config_gui.util.EditorGUIPreferences;

import java.util.function.UnaryOperator;

public interface ServerPlayerExtension {

	EditorGUIPreferences metacraft$getEditorPreferences();

	void metacraft$updateEditorPreferences(UnaryOperator<EditorGUIPreferences> modifier);

}
