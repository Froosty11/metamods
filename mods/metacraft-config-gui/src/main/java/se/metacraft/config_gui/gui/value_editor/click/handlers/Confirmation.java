package se.metacraft.config_gui.gui.value_editor.click.handlers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.ConfirmScreen;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerType;

public record Confirmation(boolean confirmed) implements ClickHandler {

	public static final MapCodec<Confirmation> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.BOOL.fieldOf("confirmed").forGetter(Confirmation::confirmed)
		).apply(instance, Confirmation::new)
	);

	@Override
	public DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui) {
		if (gui instanceof ConfirmScreen confirmScreen) {
			if (confirmed) {
				return confirmScreen.onConfirm(player);
			} else {
				return confirmScreen.parent().map(ClickHandlerGUI.ParentInfo::gui).orElse(null);
			}
		}
		return gui;
	}

	@Override
	public ClickHandlerType<Confirmation> type() {
		return ClickHandlerRegistry.CONFIRMATION;
	}

}
