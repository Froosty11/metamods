package nu.metacraft.config.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import nu.metacraft.config.source.*;

import java.util.*;

/** The screen's dialogs: the list of configs, a config's page, and "reset this page?". */
public final class Pages {
	public static final Identifier OPEN = Identifier.fromNamespaceAndPath("metacraft-config", "open");
	public static final Identifier SAVE = Identifier.fromNamespaceAndPath("metacraft-config", "save");
	public static final Identifier RESET = Identifier.fromNamespaceAndPath("metacraft-config", "reset");
	private static final int BODY_WIDTH = 300, BUTTON_WIDTH = 150;

	private Pages() {}

	public static Dialog main(List<ConfigSource> sources) {
		List<DialogBody> body = new ArrayList<>();
		List<String> pending = new ArrayList<>();
		for (ConfigSource source : sources) {
			for (String option : source.pendingRestart()) pending.add(source.name() + ": " + option);
		}
		if (!pending.isEmpty()) {
			body.add(message(Component.literal("Restart needed for: " + String.join("; ", pending)).withStyle(ChatFormatting.GOLD)));
		}
		if (sources.isEmpty()) body.add(message(Component.literal("No configs are described on this server.")));
		List<ActionButton> buttons = new ArrayList<>();
		for (ConfigSource source : sources) {
			// Vanilla's font has no ⟳; spell the restart marker out instead of drawing a tofu box.
			String marks = (source.loadError().isPresent() ? " ⚠" : "") + (source.pendingRestart().isEmpty() ? "" : " (restart pending)");
			buttons.add(button(Component.literal(source.name() + marks), Optional.of(Component.literal(source.description())),
					OPEN, target(source.id(), List.of())));
		}
		if (buttons.isEmpty()) buttons.add(closeButton());   // MultiActionDialog's actions must not be empty
		return new MultiActionDialog(common(Component.literal("Server config"), body, List.of()), buttons,
				Optional.of(closeButton()), 2);
	}

	public static Dialog config(ConfigSource source, List<String> path, Optional<Component> message, Map<String, String> typed) {
		Page page = source.page(path);
		List<DialogBody> body = new ArrayList<>();
		message.ifPresent(m -> body.add(message(m)));
		source.loadError().ifPresent(error -> body.add(message(Component.literal(
				"The file does not load cleanly: " + error + ". The server keeps the values it could read; the rest are "
						+ "defaults or the last good values. Those are shown below; saving writes them over the file.").withStyle(ChatFormatting.RED))));
		List<Input> inputs = new ArrayList<>();
		for (int i = 0; i < page.fields().size(); i++) {
			Field field = page.fields().get(i);
			if (!field.editable()) {
				body.add(message(Component.literal(field.label() + ": " + field.value() + " (edit in the file)").withStyle(ChatFormatting.GRAY)));
				continue;
			}
			if (!Inputs.fits(field)) {
				body.add(message(Component.literal(field.label() + ": too long to edit here; edit in the file").withStyle(ChatFormatting.GRAY)));
				continue;
			}
			inputs.add(new Input(Inputs.key(i), Inputs.forField(field, typed.getOrDefault(field.key(), field.value()))));
		}
		CompoundTag here = target(source.id(), path);
		here.putInt("hash", source.hash());
		List<ActionButton> buttons = new ArrayList<>();
		buttons.add(button(Component.literal("Save").withStyle(ChatFormatting.GREEN), Optional.empty(), SAVE, here.copy()));
		for (Link link : page.sections()) {
			List<String> sub = new ArrayList<>(path);
			sub.add(link.key());
			buttons.add(button(Component.literal(link.label()), Optional.of(Component.literal("Unsaved changes on this page are lost.")),
					OPEN, target(source.id(), sub)));
		}
		CompoundTag reset = here.copy();
		reset.putBoolean("confirm", false);
		buttons.add(button(Component.literal("Reset to defaults"), Optional.empty(), RESET, reset));
		buttons.add(button(Component.literal("Back"), Optional.empty(), OPEN,
				path.isEmpty() ? target("", List.of()) : target(source.id(), path.subList(0, path.size() - 1))));
		Component title = Component.literal(path.isEmpty() ? source.name() : source.name() + " › " + page.title());
		return new MultiActionDialog(common(title, body, inputs), buttons, Optional.of(closeButton()), 2);
	}

	public static Dialog confirmReset(ConfigSource source, List<String> path) {
		CompoundTag yes = target(source.id(), path);
		yes.putInt("hash", source.hash());
		yes.putBoolean("confirm", true);
		Component what = Component.literal(path.isEmpty() ? source.name() : source.page(path).title());
		return new MultiActionDialog(common(Component.literal("Reset " + what.getString() + "?"),
				List.of(message(Component.literal("Every option on this page goes back to its default. This is saved at once."))), List.of()),
				List.of(button(Component.literal("Reset").withStyle(ChatFormatting.RED), Optional.empty(), RESET, yes),
						button(Component.literal("Keep"), Optional.empty(), OPEN, target(source.id(), path))),
				Optional.empty(), 2);
	}

	static CompoundTag target(String source, List<String> path) {
		CompoundTag tag = new CompoundTag();
		tag.putString("source", source);
		tag.putString("page", String.join("/", path));
		return tag;
	}

	private static CommonDialogData common(Component title, List<DialogBody> body, List<Input> inputs) {
		return new CommonDialogData(title, Optional.empty(), true, false, DialogAction.WAIT_FOR_RESPONSE, body, inputs);
	}

	private static PlainMessage message(Component text) {
		return new PlainMessage(text, BODY_WIDTH);
	}

	private static ActionButton button(Component label, Optional<Component> tooltip, Identifier action, CompoundTag payload) {
		return new ActionButton(new CommonButtonData(label, tooltip, BUTTON_WIDTH), Optional.of(new CustomAll(action, Optional.of(payload))));
	}

	private static ActionButton closeButton() {
		return new ActionButton(new CommonButtonData(Component.literal("Close"), BUTTON_WIDTH), Optional.empty());
	}
}
