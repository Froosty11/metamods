package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.CodecDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record SingleValueEditor<T>(
	Object value, CodecDialog.Type type, Codec<T> codec, Optional<ParentInfo> parent, Optional<Component> tooltip
) implements ValueEditor<T> {

	public static final String VALUE = "value";

	@Override
	public Holder<Dialog> createDialog(ServerPlayer player) {
		List<ActionButton> buttons = new ArrayList<>();
		List<Input> inputs = new ArrayList<>();
		List<DialogBody> body = new ArrayList<>();
		CodecDialog.addInput(
			VALUE, Component.translatable("selectWorld.edit"), type, value, player.registryAccess(), body, buttons, inputs, tooltip
		);
		return CodecDialog.template(
			body, inputs, buttons, player.registryAccess(), 1
		);
	}

	@Override
	public ValueEditor<T> updateFromMessage(ServerPlayer player, CompoundTag tag) {
		var result = type.parse(player.registryAccess(), tag.get(VALUE)).resultOrPartial(
			err -> player.sendSystemMessage(Component.literal(err))
		);
		return result.map(
			o -> new SingleValueEditor<>(o, type, codec, parent, tooltip)
		).orElse(this);
	}

	@Override
	public Optional<T> getValue(HolderLookup.Provider lookup) {
		return codec.parse(
			lookup.createSerializationContext(JavaOps.INSTANCE), value
		).resultOrPartial();
	}
}
