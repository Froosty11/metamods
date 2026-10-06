package se.metacraft.config_gui;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;

import java.util.Optional;

public interface ClickHandlerGUI extends DialogGUI {

	Optional<ParentInfo> parent();
	default ClickHandlerGUI updateFromMessage(ServerPlayer player, CompoundTag tag) {
		return this;
	}
	default ClickHandlerGUI updateFromChild(HolderLookup.Provider lookup, ClickHandlerGUI child, SubMenu.SubMenuKey position) {
		return this;
	}

	record ParentInfo(ClickHandlerGUI gui, SubMenu.SubMenuKey position) {
		public ClickHandlerGUI withUpdatedGUI(ClickHandlerGUI childGUI, HolderLookup.Provider lookup) {
			return gui.updateFromChild(lookup, childGUI, position);
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
		var updated = tag instanceof CompoundTag c ? updateFromMessage(player, c) : this;
		return ClickHandler.CODEC.parse(
			player.registryAccess().createSerializationContext(NbtOps.INSTANCE),
			tag
		).resultOrPartial(ConfigGUI.LOGGER::error).map(
			clickHandler -> clickHandler.onClick(player, updated)
		).orElse(null);
	}
}
