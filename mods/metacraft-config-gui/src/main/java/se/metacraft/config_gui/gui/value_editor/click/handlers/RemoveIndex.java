package se.metacraft.config_gui.gui.value_editor.click.handlers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerType;
import se.metacraft.config_gui.gui.value_editor.trait.RemovableByIndex;

public record RemoveIndex(int index) implements ClickHandler {

	public static final MapCodec<RemoveIndex> CODEC = Codec.INT.fieldOf("remove$index").xmap(
		RemoveIndex::new, RemoveIndex::index
	);

	@Override
	public DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui) {
		if (gui instanceof RemovableByIndex removable) {
			return removable.remove(player, index);
		} else {
			return gui;
		}
	}

	@Override
	public ClickHandlerType<RemoveIndex> type() {
		return ClickHandlerRegistry.REMOVE_INDEX;
	}
}
