package se.metacraft.config_gui.mixin;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import se.metacraft.config_gui.DebugWarnings;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.DialogGUIExtension;

import java.util.Optional;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements DialogGUIExtension {

	@Shadow
	public abstract void openDialog(Holder<Dialog> dialog);

	@Shadow
	public ServerGamePacketListenerImpl connection;
	@Unique
	@Nullable
	private DialogGUI gui;

	@Override
	public void metacraft$openGUI(DialogGUI gui) {
		this.gui = gui;
		if (gui == null) {
			metacraft$closeGUI();
		} else {
			var dialog = gui.createDialog((ServerPlayer) (Object) this);
			DebugWarnings.validateDialog(dialog);
			openDialog(dialog);
		}
	}

	@Override
	public void metacraft$closeGUI() {
		boolean forceClose = gui != null && gui.forceClose();
		this.gui = null;
		// Unfortunately, the ClientboundClearDialogPacket does not close the WaitingForResponse screen, so we must send a stronger packet.
		if (forceClose) {
			connection.send(new ClientboundContainerClosePacket(-1));
		} else {
			connection.send(ClientboundClearDialogPacket.INSTANCE);
		}
	}

	@Override
	public Optional<DialogGUI> metacraft$getCurrentGUI() {
		return Optional.ofNullable(gui);
	}
}
