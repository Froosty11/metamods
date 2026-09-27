package se.metacraft.config_gui.gui.value_editor.click;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.message.DialogGUIHandler;

import java.util.Optional;

public interface ClickHandler {

	String TYPE_ID = "click$type";

	Codec<ClickHandler> CODEC = Codec.withAlternative(
		ClickHandlerType.CODEC.dispatch(
			TYPE_ID, ClickHandler::type, ClickHandlerType::codec
		),
		ClickHandlerType.CODEC.comapFlatMap(
			type -> type.singleton().map(
				DataResult::success
			).orElseGet(() -> DataResult.error(() -> type + " is not a singleton")),
			ClickHandler::type
		)
	);

	DialogGUI onClick(ServerPlayer player, ClickHandlerGUI gui);

	ClickHandlerType<? extends ClickHandler> type();

	static ClickEvent clickEvent(HolderLookup.Provider provider, ClickHandler handler) {
		Tag tag = ClickHandler.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), handler).getOrThrow();
		if (tag instanceof CompoundTag c && c.size() == 1 && c.get(TYPE_ID) instanceof StringTag s) {
			return DialogGUIHandler.clickEvent(Optional.of(s));
		}
		return DialogGUIHandler.clickEvent(Optional.of(tag));
	}

	static Action click(HolderLookup.Provider provider, ClickHandler handler, boolean submitFields) {
		if (submitFields) {
			CompoundTag tag = (CompoundTag) ClickHandler.CODEC.encodeStart(
				provider.createSerializationContext(NbtOps.INSTANCE), handler
			).getOrThrow();
			return DialogGUIHandler.withInputsAction(Optional.of(tag));
		}
		return new StaticAction(clickEvent(provider, handler));
	}
}
