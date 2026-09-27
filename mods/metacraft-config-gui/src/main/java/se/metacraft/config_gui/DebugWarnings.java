package se.metacraft.config_gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import nu.metacraft.lib.custom_message.CustomMessageRegistry;

import java.util.Optional;

public class DebugWarnings {

	private static void err() {
		throw new IllegalStateException("Dialog can be closed without notifying server!");
	}

	private static void errIfClosable(CommonDialogData data) {
		if (data.afterAction() != DialogAction.NONE) {
			err();
		}
		if (data.canCloseWithEscape()) {
			err();
		}
	}

	private static void warnCustomMessageID(Identifier id) {
		if (!CustomMessageRegistry.REGISTRY.containsKey(id)) {
			ConfigGUI.LOGGER.warn("The message id {} has not been registered.", id);
		}
	}

	private static void validateClickEvent(ClickEvent event, CommonDialogData data) {
		switch (event) {
			case ClickEvent.Custom custom -> {
				warnCustomMessageID(custom.id());
			}
			case ClickEvent.ShowDialog dialog -> {
				validateDialog(dialog.dialog());
			}
			case ClickEvent.OpenUrl url -> {
				errIfClosable(data);
			}
			case ClickEvent.CopyToClipboard c -> {
				errIfClosable(data);
			}
			case ClickEvent.ChangePage c -> {
				errIfClosable(data);
			}
			case ClickEvent.OpenFile c -> {
				ConfigGUI.LOGGER.warn("The open file click event cannot be sent to the client!");
				errIfClosable(data);
			}
			case ClickEvent.SuggestCommand c -> {
				ConfigGUI.LOGGER.warn("https://bugs.mojang.com/browse/MC/issues/MC-300896");
				errIfClosable(data);
			}
			default -> {}
		}
	}

	private static void validateAction(
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Action> action,
		CommonDialogData data
	) {
		if (action.isEmpty()) {
			errIfClosable(data);
		}

		if (action.isPresent()) {
			switch (action.get()) {
				case CustomAll custom -> {
					warnCustomMessageID(custom.id());
				}
				case StaticAction(ClickEvent value) -> {
					validateClickEvent(value, data);
				}
				default -> {}
			}
		}

	}

	private static void validateText(Component text, CommonDialogData data) {
		var click = text.getStyle().getClickEvent();
		if (click != null) {
			validateClickEvent(click, data);
		}
		for (var sibling : text.getSiblings()) {
			validateText(sibling, data);
		}
	}

	public static void validateDialog(Holder<Dialog> dialog) {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			var d = dialog.value();
			if (d instanceof ServerLinksDialog) {
				err();
			}
			var common = d.common();
			for (var body : common.body()) {
				switch (body) {
					case PlainMessage(Component contents, _) -> {
						validateText(contents, dialog.value().common());
					}
					case ItemBody item -> {
						if (item.description().isPresent()) {
							validateText(item.description().get().contents(), dialog.value().common());
						}
					}
					default -> {}
				}
				if (body instanceof PlainMessage(Component contents, _)) {
					validateText(contents, dialog.value().common());
				}
			}
			if (d.common().canCloseWithEscape()) {
				validateAction(d.onCancel(), common);
			}
			if (d instanceof SimpleDialog simple) {
				for (var button : simple.mainActions()) {
					validateAction(button.action(), common);
				}
			}
			if (d instanceof MultiActionDialog multi) {
				for (var button : multi.actions()) {
					validateAction(button.action(), common);
				}
			}
		}

	}

}
