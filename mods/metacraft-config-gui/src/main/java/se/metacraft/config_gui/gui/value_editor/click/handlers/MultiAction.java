package se.metacraft.config_gui.gui.value_editor.click.handlers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerType;

import java.util.List;

public record MultiAction(List<ClickHandler> toRun) implements ClickHandler {

	public static final MapCodec<MultiAction> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			ExtraCodecs.nonEmptyList(Codec.lazyInitialized(() -> ClickHandler.CODEC).listOf()).fieldOf("run").forGetter(MultiAction::toRun)
		).apply(instance, MultiAction::new)
	);

	@Override
	public DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui) {
		DialogGUI result = gui;
		for (var action : toRun) {
			if (!(result instanceof ClickHandlerGUI clickable)) return result;
			result = action.onClick(player, clickable);
		}
		return result;
	}

	@Override
	public ClickHandlerType<MultiAction> type() {
		return ClickHandlerRegistry.MULTI_ACTION;
	}

}
