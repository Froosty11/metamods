package se.metacraft.config_gui.gui.value_editor.click.handlers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerType;
import se.metacraft.config_gui.gui.value_editor.trait.Selectable;

public record Select(String key) implements ClickHandler {

	public static final MapCodec<Select> CODEC = Codec.STRING.fieldOf("submenu$key").xmap(
		Select::new, Select::key
	);

	@Override
	public DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui) {
		if (gui instanceof Selectable selectable) {
			return selectable.onSelect(key, player);
		}
		return gui;
	}

	@Override
	public ClickHandlerType<Select> type() {
		return ClickHandlerRegistry.SELECT;
	}
}
