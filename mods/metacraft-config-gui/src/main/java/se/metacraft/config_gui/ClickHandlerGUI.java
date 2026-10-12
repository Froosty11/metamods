package se.metacraft.config_gui;

import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.gui.value_editor.PopupScreen;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface ClickHandlerGUI extends DialogGUI {

	Optional<ParentInfo> parent();

	record MessageUpdate(ClickHandlerGUI resultGUI, boolean click) {
		public static MessageUpdate simple(ClickHandlerGUI resultGUI) {
			return new MessageUpdate(resultGUI, true);
		}

		public static <T> MessageUpdate dataResult(DataResult<T> result, Function<T, ClickHandlerGUI> onSuccess, ClickHandlerGUI fallback) {
			return result.mapOrElse(
				onSuccess.andThen(MessageUpdate::simple),
				err -> new MessageUpdate(
					new PopupScreen(
						PopupScreen.FAILED, List.of(new PlainMessage(Component.literal(err.message()), PlainMessage.DEFAULT_WIDTH)),
						new SimpleSubMenuParentInfo(err.partialValue().map(onSuccess).orElse(fallback))
					),
					false
				)
			);
		}
	}

	default MessageUpdate updateFromMessage(ServerPlayer player, CompoundTag tag) {
		return MessageUpdate.simple(this);
	}
	default ClickHandlerGUI updateFromChild(HolderLookup.Provider lookup, ClickHandlerGUI child, SubMenu.SubMenuKey position) {
		return this;
	}

	@FunctionalInterface
	interface ParentInfo {
		ClickHandlerGUI withUpdatedGUI(ClickHandlerGUI childGUI, ServerPlayer player);
	}

	record SimpleSubMenuParentInfo(ClickHandlerGUI gui) implements ParentInfo {
		public ClickHandlerGUI withUpdatedGUI(ClickHandlerGUI childGUI, ServerPlayer player) {
			return gui;
		}
	}

	record SubMenuParentInfo(ClickHandlerGUI gui, SubMenu.SubMenuKey position) implements ParentInfo {
		public ClickHandlerGUI withUpdatedGUI(ClickHandlerGUI childGUI, ServerPlayer player) {
			return gui.updateFromChild(player.registryAccess(), childGUI, position);
		}
	}

	@Override
	default boolean forceClose() {
		return parent().isEmpty();
	}

	@Override
	default DialogGUI onClick(ServerPlayer player, Optional<Tag> message) {
		if (message.isEmpty()) return this;
		var tag = message.get();
		var updated = tag instanceof CompoundTag c ? updateFromMessage(player, c) : null;
		if (updated != null && !updated.click()) return updated.resultGUI();
		return ClickHandler.CODEC.parse(
			player.registryAccess().createSerializationContext(NbtOps.INSTANCE),
			tag
		).resultOrPartial(ConfigGUI.LOGGER::error).map(
			clickHandler -> clickHandler.onClick(player, updated != null ? updated.resultGUI() : this)
		).orElse(null);
	}
}
