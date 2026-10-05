
package se.metacraft.config_gui.gui.value_editor.click.handlers;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerType;
import se.metacraft.config_gui.gui.value_editor.trait.RemovableByKey;

public record RemoveObject(Object key) implements ClickHandler {

	public static final MapCodec<RemoveObject> CODEC = ExtraCodecs.JAVA.fieldOf("remove$key").xmap(
		RemoveObject::new, RemoveObject::key
	);

	@Override
	public DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui) {
		if (gui instanceof RemovableByKey removable) {
			return removable.remove(player, key);
		} else {
			return gui;
		}
	}

	@Override
	public ClickHandlerType<RemoveObject> type() {
		return ClickHandlerRegistry.REMOVE_OBJECT;
	}
}
