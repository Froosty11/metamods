package nu.metacraft.config.screen;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.config.MetacraftConfig;
import nu.metacraft.config.source.*;
import nu.metacraft.lib.custom_message.CustomMessageHandler;
import nu.metacraft.lib.custom_message.CustomMessageRegistry;

import java.util.*;

/** The screen's buttons, handled on the server; every press checks the permission again. */
public final class Actions {
	public static final String PERMISSION = "metacraft.config";
	public static final int LEVEL = 3;

	private Actions() {}

	public static void register() {
		Registry.register(CustomMessageRegistry.REGISTRY, Pages.OPEN, handler(Actions::open));
		Registry.register(CustomMessageRegistry.REGISTRY, Pages.SAVE, handler(Actions::save));
		Registry.register(CustomMessageRegistry.REGISTRY, Pages.RESET, handler(Actions::reset));
	}

	public static boolean allowed(ServerPlayer player) {
		return Permissions.check(player.createCommandSourceStack(), PERMISSION, LEVEL);
	}

	public static void openMain(ServerPlayer player) {
		player.openDialog(Holder.direct(Pages.main(Sources.all())));
	}

	public static void openConfig(ServerPlayer player, ConfigSource source, List<String> path) {
		show(player, source, path, Optional.empty(), Map.of());
	}

	private interface Press {
		void handle(ServerPlayer player, CompoundTag payload);
	}

	private static CustomMessageHandler handler(Press press) {
		return (payload, server, potential) -> potential.getPlayer(server).ifPresent(player -> {
			if (!allowed(player)) return;
			CompoundTag tag = payload.flatMap(Tag::asCompound).orElseGet(CompoundTag::new);
			press.handle(player, tag);
		});
	}

	private static void open(ServerPlayer player, CompoundTag payload) {
		String id = payload.getStringOr("source", "");
		if (id.isEmpty()) {
			openMain(player);
			return;
		}
		withSource(player, id, source -> openConfig(player, source, Payloads.path(payload)));
	}

	private static void save(ServerPlayer player, CompoundTag payload) {
		withSource(player, payload.getStringOr("source", ""), source -> {
			List<String> path = Payloads.path(payload);
			Page page;
			try {
				page = source.page(path);
			} catch (IllegalArgumentException e) {
				openMain(player);
				return;
			}
			Map<String, String> values = Payloads.values(page, payload);
			EditOutcome outcome;
			try {
				outcome = source.apply(path, values, payload.getIntOr("hash", 0));
			} catch (RuntimeException e) {
				// Adapters are third-party-shaped code; a bug there should not take the packet handler
				// down with it.
				MetacraftConfig.LOGGER.error("{} failed to save", source.id(), e);
				show(player, source, path, Optional.of(Component.literal("Could not save: " + e.getMessage()).withStyle(ChatFormatting.RED)), values);
				return;
			}
			switch (outcome) {
				case EditOutcome.Saved saved -> show(player, source, path,
						Optional.of(Component.literal("Saved.").withStyle(ChatFormatting.GREEN)), Map.of());
				case EditOutcome.Refused refused -> show(player, source, path,
						Optional.of(Component.literal(refused.message()).withStyle(ChatFormatting.RED)), values);
				case EditOutcome.Stale stale -> show(player, source, path,
						Optional.of(Component.literal("Someone changed this config since you opened it; here it is as it is now.")
								.withStyle(ChatFormatting.GOLD)), Map.of());
			}
		});
	}

	private static void reset(ServerPlayer player, CompoundTag payload) {
		withSource(player, payload.getStringOr("source", ""), source -> {
			List<String> path = Payloads.path(payload);
			if (!payload.getBooleanOr("confirm", false)) {
				player.openDialog(Holder.direct(resetConfirmDialog(source, path)));
				return;
			}
			EditOutcome outcome;
			try {
				outcome = source.reset(path, payload.getIntOr("hash", 0));
			} catch (RuntimeException e) {
				MetacraftConfig.LOGGER.error("{} failed to reset", source.id(), e);
				show(player, source, path, Optional.of(Component.literal("Could not reset: " + e.getMessage()).withStyle(ChatFormatting.RED)), Map.of());
				return;
			}
			Component message = switch (outcome) {
				case EditOutcome.Saved saved -> Component.literal("Reset to defaults.").withStyle(ChatFormatting.GREEN);
				case EditOutcome.Refused refused -> Component.literal(refused.message()).withStyle(ChatFormatting.RED);
				case EditOutcome.Stale stale -> Component.literal("Someone changed this config meanwhile; nothing was reset.").withStyle(ChatFormatting.GOLD);
			};
			show(player, source, path, Optional.of(message), Map.of());
		});
	}

	private static void show(ServerPlayer player, ConfigSource source, List<String> path, Optional<Component> message, Map<String, String> typed) {
		player.openDialog(Holder.direct(configDialog(source, path, message, typed)));
	}

	/**
	 * {@link Pages#config}, falling back to the main page for a path the source does not have — a
	 * modified client can send any string as the page. Exposed (not called through a
	 * {@link ServerPlayer}) so a unit test can reach the guard directly.
	 */
	public static Dialog configDialog(ConfigSource source, List<String> path, Optional<Component> message, Map<String, String> typed) {
		try {
			return Pages.config(source, path, message, typed);
		} catch (IllegalArgumentException e) {
			return Pages.main(Sources.all());
		}
	}

	/** {@link Pages#confirmReset}, with the same fallback as {@link #configDialog}. */
	public static Dialog resetConfirmDialog(ConfigSource source, List<String> path) {
		try {
			return Pages.confirmReset(source, path);
		} catch (IllegalArgumentException e) {
			return Pages.main(Sources.all());
		}
	}

	private static void withSource(ServerPlayer player, String id, java.util.function.Consumer<ConfigSource> then) {
		Sources.find(id).ifPresentOrElse(then, () -> openMain(player));
	}
}
