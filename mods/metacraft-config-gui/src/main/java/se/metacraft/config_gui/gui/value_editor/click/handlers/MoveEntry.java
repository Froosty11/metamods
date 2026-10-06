package se.metacraft.config_gui.gui.value_editor.click.handlers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerRegistry;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandlerType;
import se.metacraft.config_gui.gui.value_editor.trait.MovableEntries;

public record MoveEntry(int index, MoveDirection moveDirection) implements ClickHandler {

	public static final MapCodec<MoveEntry> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.INT.fieldOf("index").forGetter(MoveEntry::index),
			MoveDirection.CODEC.fieldOf("direction").forGetter(MoveEntry::moveDirection)
		).apply(instance, MoveEntry::new)
	);

	@Override
	public DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui) {
		if (gui instanceof MovableEntries movable) {
			return movable.moveEntry(index, moveDirection);
		}
		return gui;
	}

	@Override
	public ClickHandlerType<MoveEntry> type() {
		return ClickHandlerRegistry.MOVE_INDEX;
	}

	public enum MoveDirection implements StringRepresentable {
		UP("up"),
		DOWN("down");

		private final String id;

		MoveDirection(String id) {
			this.id = id;
		}

		public static final Codec<MoveDirection> CODEC = StringRepresentable.fromEnum(MoveDirection::values);

		@Override
		public @NonNull String getSerializedName() {
			return id;
		}
	}

}
