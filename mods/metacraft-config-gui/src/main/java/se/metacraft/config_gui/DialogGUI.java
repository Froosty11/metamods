package se.metacraft.config_gui;

import net.minecraft.core.Holder;
import net.minecraft.nbt.Tag;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public interface DialogGUI {

	DialogGUI onClick(
		ServerPlayer player,
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Tag> message
	);

	// Workaround for https://bugs.mojang.com/browse/MC/issues/MC-306380
	// Will be removed in the future.
	@Deprecated
	default boolean forceClose() {
		return false;
	}

	Holder<Dialog> createDialog(ServerPlayer player);

}
