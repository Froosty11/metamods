package se.metacraft.ovvar.content.enchantment;

import com.mojang.authlib.GameProfile;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ContainerComponent;
import net.minecraft.world.item.component.ItemLore;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;
import se.metacraft.bundles.util.BundleHelper;
import se.metacraft.lib.util.TaskScheduler;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.item.OvveItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Ender Pockets: an ovve whose pockets are the ender chest of whoever uses them. Clicking an item onto
 * it puts the item in the clicking player's ender chest; right-clicking it with an empty cursor takes a
 * whole stack out; using it opens the ender chest screen. Its own {@code bundle_contents} stay empty:
 * whatever was in them when it got the enchantment is thrown on the ground ({@link #spill}) the next
 * time it is touched or ticked, never moved into anybody's ender chest.
 *
 * The client is shown the viewer's own ender chest as the bundle's contents ({@link #view}), newest
 * first like a bundle, with a fill bar over the chest's 27 slots. A preview never contains itself: an
 * Ender Pockets ovve inside the preview (in the ender chest, or in a shulker box or bundle there) is
 * handed over marked {@link ModComponents#ENDER_NESTED} and shows empty pockets. A marker, not only a
 * flag for the duration of the call, because Polymer turns nested stacks into client stacks later,
 * while the packet is encoded on the network thread, after {@link #clientStack} has returned.
 *
 * The server's ovve does not change when the ender chest does, so the menus' slot sync (vanilla's, and
 * Polymer's, which compares server stacks once a slot has been sent) never resends it by itself. So
 * {@link #tick} keeps a copy of the ender chest of every player who has an Ender Pockets ovve in an open
 * menu or on the cursor, and when it differs, or a preview read tore, resends those slots ({@link #resend}).
 */
public final class EnderPockets {
	private EnderPockets() {}

	/** The fill bar's phase on {@link PolymerItemUtils#ITEM_MODIFICATION_EVENT}: after metacraft-bundles adds its own bar. */
	public static final Identifier FILL_BAR_PHASE = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "ender_pockets");
	/** An ender chest's slots: the fill bar's capacity, in bundles. */
	public static final int ENDER_SLOTS = 27;
	/** How deep {@link #forView} looks into containers in containers; anything deeper is shown empty. */
	private static final int MAX_DEPTH = 8;

	/** The view a client stack is being built with, while {@link #clientStack} runs on this thread; null otherwise. */
	private static final ThreadLocal<BundleContents> BUILDING = new ThreadLocal<>();

	/** Server thread only: the ender chest as last seen, for each player who has an Ender Pockets ovve to keep current. */
	private static final Map<UUID, List<ItemStack>> SEEN = new HashMap<>();
	/** Players whose preview read tore on the network thread ({@link #view}): resent at the end of the tick. */
	private static final Set<UUID> TORN = ConcurrentHashMap.newKeySet();

	public static void init() {
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, FILL_BAR_PHASE);
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register(FILL_BAR_PHASE, EnderPockets::fillBar);
		ServerTickEvents.END_SERVER_TICK.register(EnderPockets::tick);
	}

	public static boolean has(ItemStack ovve) {
		return OvveEnchantments.level(ovve, OvveEnchantments.ENDER_POCKETS) > 0;
	}

	// ---- the ovve's own pockets

	/**
	 * Throws out whatever is in an Ender Pockets ovve's own pockets (put there before it was enchanted):
	 * a player drops it, any other holder spawns it where it stands. The size factor stays (metacraft-bundles
	 * keeps it on {@code EMPTY}). Returns whether anything was thrown.
	 */
	public static boolean spill(ItemStack ovve, @Nullable Entity holder) {
		if (holder == null || !has(ovve)) return false;
		BundleContents contents = ovve.get(DataComponents.BUNDLE_CONTENTS);
		if (contents == null || contents.isEmpty() || !(holder.level() instanceof ServerLevel level)) return false;
		ovve.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
		contents.itemCopies().forEach(copy -> {
			if (copy.isEmpty()) return;
			if (holder instanceof Player player) player.drop(copy, false, Prediction.SERVER_ONLY);
			else holder.spawnAtLocation(level, copy);
		});
		return true;
	}

	// ---- the view

	/**
	 * What the viewer's client is shown as the pockets: their ender chest's non-empty slots from 26 down to
	 * 0, with {@code selected} applied the way the bundle applies its own selection. EMPTY without a viewer.
	 */
	public static BundleContents view(@Nullable ServerPlayer viewer, int selected) {
		if (viewer == null) return BundleContents.EMPTY;
		List<ItemStackTemplate> items = new ArrayList<>();
		try {
			Container ender = viewer.getEnderChestInventory();
			for (int slot = ender.getContainerSize() - 1; slot >= 0; slot--) {
				ItemStack stack = ender.getItem(slot);
				if (!stack.isEmpty()) items.add(ItemStackTemplate.fromNonEmptyStack(forView(stack, 0)));
			}
		} catch (RuntimeException e) {
			// The network thread builds client stacks while the server thread may be changing the chest: a torn
			// read shows nothing rather than failing the packet, and the slot is resent at the end of the tick.
			Ovvar.LOGGER.debug("[{}] ender pockets: reading {}'s ender chest: {}", Ovvar.MOD_ID, viewer.getUUID(), e.toString());
			TORN.add(viewer.getUUID());
			return BundleContents.EMPTY;
		}
		BundleContents view = new BundleContents(items);
		if (selected < 0) return view;
		BundleContents.Mutable mutable = view.asMutable();
		mutable.toggleSelectedItem(selected);
		return mutable.toImmutable();
	}

	/**
	 * A stack as the preview shows it: an Ender Pockets ovve marked so its own preview is empty, and the
	 * contents of a shulker box, bundle or any other item container walked for the same, up to
	 * {@link #MAX_DEPTH}. Returns the stack itself when nothing needs changing.
	 */
	private static ItemStack forView(ItemStack stack, int depth) {
		if (has(stack)) {
			ItemStack copy = stack.copy();
			copy.set(ModComponents.ENDER_NESTED, Unit.INSTANCE);
			return copy;
		}
		ItemStack copy = null;
		for (TypedDataComponent<?> component : stack.getComponents()) {
			if (!(component.value() instanceof ContainerComponent<?> container) || container.size() == 0) continue;
			List<ItemStack> shown = new ArrayList<>();
			boolean changed = depth + 1 >= MAX_DEPTH;
			if (!changed) {
				for (ItemStack inside : container.itemCopies().toList()) {
					ItemStack s = inside.isEmpty() ? inside : forView(inside, depth + 1);
					changed |= s != inside;
					shown.add(s);
				}
			}
			if (!changed) continue;
			if (copy == null) copy = stack.copy();
			setUnchecked(copy, component.type(), container.copyWithContents(shown.stream()));
		}
		return copy == null ? stack : copy;
	}

	@SuppressWarnings("unchecked")
	private static <T> void setUnchecked(ItemStack stack, DataComponentType<T> type, Object value) {
		stack.set(type, (T) value);
	}

	/** The ender slot behind view index {@code viewIndex} (see {@link #view}), or −1. */
	public static int slotOf(ServerPlayer player, int viewIndex) {
		return slotIn(player.getEnderChestInventory(), viewIndex);
	}

	private static int slotIn(Container ender, int viewIndex) {
		if (viewIndex < 0) return -1;
		int seen = 0;
		for (int slot = ender.getContainerSize() - 1; slot >= 0; slot--) {
			if (!ender.getItem(slot).isEmpty() && seen++ == viewIndex) return slot;
		}
		return -1;
	}

	/** The bundle's selection, kept on the ovve since its contents are not its own: the same index again (or −1) clears it. */
	public static void toggleSelected(ItemStack ovve, int index) {
		int current = ovve.getOrDefault(ModComponents.ENDER_SELECTED, -1);
		if (index < 0 || index == current) ovve.remove(ModComponents.ENDER_SELECTED);
		else ovve.set(ModComponents.ENDER_SELECTED, index);
	}

	// ---- clicks (BundleItem's rules and return values, with the ender chest as the contents)

	/** The ovve on the cursor, clicked onto {@code slot}. */
	public static boolean stackedOnOther(ItemStack ovve, Slot slot, ClickAction action, Player player) {
		spill(ovve, player);
		ItemStack other = slot.getItem();
		if (action == ClickAction.PRIMARY && !other.isEmpty()) {
			boolean moved = takeFromSlot(player, slot);
			if (moved) playInsert(player);
			else playInsertFail(player);
			afterClick(player, slot);
			return true;
		}
		if (action == ClickAction.SECONDARY && other.isEmpty()) {
			Container ender = player.getEnderChestInventory();
			int from = takeOutSlot(ender, ovve);
			// A slot that refuses the stack (an armour slot, a result slot) leaves it where it is in the chest.
			if (from >= 0 && slot.mayPlace(ender.getItem(from))) {
				ItemStack removed = takeOut(player, ovve);
				ItemStack rest = slot.safeInsert(removed);
				if (rest.getCount() < removed.getCount()) playRemoveOne(player);
				putBack(player, rest);
			} else {
				ovve.remove(ModComponents.ENDER_SELECTED);
				playInsertFail(player);
			}
			afterClick(player, slot);
			return true;
		}
		return false;
	}

	/** {@code other} (the cursor) clicked onto the ovve in {@code slot}, worn or not. */
	public static boolean otherStackedOnMe(ItemStack ovve, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		spill(ovve, player);
		if (action == ClickAction.PRIMARY && other.isEmpty()) {
			toggleSelected(ovve, -1);
			return false;
		}
		if (action == ClickAction.PRIMARY) {
			boolean moved = false;
			if (slot.allowModification(player)) {
				ItemStack rest = player.getEnderChestInventory().addItem(other.copy());
				moved = rest.getCount() < other.getCount();
				other.setCount(rest.getCount());
			}
			if (moved) playInsert(player);
			else playInsertFail(player);
			afterClick(player, slot);
			return true;
		}
		if (action == ClickAction.SECONDARY && other.isEmpty()) {
			if (slot.allowModification(player)) {
				ItemStack removed = takeOut(player, ovve);
				if (!removed.isEmpty()) {
					playRemoveOne(player);
					carried.set(removed);
				}
			}
			afterClick(player, slot);
			return true;
		}
		toggleSelected(ovve, -1);
		return false;
	}

	/** Moves as much of the slot's stack as the ender chest has room for into it. */
	private static boolean takeFromSlot(Player player, Slot slot) {
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ItemStack other = slot.getItem();
		int room = room(ender, other);
		if (room <= 0) return false;
		ItemStack taken = slot.safeTake(other.getCount(), room, player);
		if (taken.isEmpty()) return false;
		ItemStack rest = ender.addItem(taken);
		if (!rest.isEmpty()) putBack(player, slot.safeInsert(rest));
		return true;
	}

	private static int room(Container ender, ItemStack stack) {
		int max = ender.getMaxStackSize(stack), room = 0;
		for (int slot = 0; slot < ender.getContainerSize(); slot++) {
			ItemStack there = ender.getItem(slot);
			if (there.isEmpty()) room += max;
			else if (ItemStack.isSameItemSameComponents(there, stack)) room += Math.max(0, max - there.getCount());
		}
		return room;
	}

	/**
	 * The ender slot {@link #takeOut} would empty: the selected one, or, with nothing selected or a selection
	 * that no longer maps to a stack, the newest. Never the ovve itself (it may sit in the ender chest, and is
	 * not something to take out of its own pockets): then the newest other stack. −1 if there is none.
	 */
	private static int takeOutSlot(Container ender, ItemStack ovve) {
		int slot = slotIn(ender, ovve.getOrDefault(ModComponents.ENDER_SELECTED, -1));
		if (slot >= 0 && ender.getItem(slot) != ovve) return slot;
		for (int i = ender.getContainerSize() - 1; i >= 0; i--) {
			ItemStack there = ender.getItem(i);
			if (!there.isEmpty() && there != ovve) return i;
		}
		return -1;
	}

	/** Takes the whole selected stack (see {@link #takeOutSlot}) out of the ender chest; clears the selection like the bundle does. */
	private static ItemStack takeOut(Player player, ItemStack ovve) {
		Container ender = player.getEnderChestInventory();
		int slot = takeOutSlot(ender, ovve);
		ovve.remove(ModComponents.ENDER_SELECTED);
		if (slot < 0) return ItemStack.EMPTY;
		ItemStack taken = ender.getItem(slot);
		ender.setItem(slot, ItemStack.EMPTY);
		return taken;
	}

	/** What did not fit where it was going: back into the ender chest, else the inventory, else the ground. */
	private static void putBack(Player player, ItemStack rest) {
		if (rest.isEmpty()) return;
		ItemStack left = player.getEnderChestInventory().addItem(rest);
		if (!left.isEmpty()) player.getInventory().placeItemBackInInventory(left, Prediction.SERVER_ONLY);
	}

	// Sounds go to the clicker only, as metacraft-bundles' BundleItemMixin sends them: the vanilla
	// playSound from a player skips that player, whose client did not predict this.
	private static void playInsert(Player player) {
		sound(player, SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
	}

	private static void playRemoveOne(Player player) {
		sound(player, SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
	}

	private static void playInsertFail(Player player) {
		sound(player, SoundEvents.BUNDLE_INSERT_FAIL, 1.0f, 1.0f);
	}

	private static void sound(Player player, SoundEvent sound, float volume, float pitch) {
		if (player instanceof ServerPlayer p) {
			p.connection.send(new ClientboundSoundEntityPacket(Holder.direct(sound), player.getSoundSource(), player, volume, pitch,
					player.level().getRandom().nextLong()));
		}
	}

	/**
	 * After a click, as BundleItem and metacraft-bundles' {@code updateIfClicked} do: tell the menu the
	 * inventory changed, then, at the end of the tick, resend the clicked slot and the cursor (the client
	 * predicted a bundle, not an ender chest).
	 */
	private static void afterClick(Player player, Slot slot) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu != null) menu.slotsChanged(player.getInventory());
		if (!(player instanceof ServerPlayer p) || menu == null) return;
		TaskScheduler.scheduleImmediately(p.level().getServer(), () -> {
			if (p.containerMenu != menu) return;
			p.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), slot.index, slot.getItem().copy()));
			p.connection.send(new ClientboundSetCursorItemPacket(menu.getCarried().copy()));
		});
	}

	// ---- keeping the preview current

	/**
	 * END_SERVER_TICK: for every player with an Ender Pockets ovve in an open menu or on the cursor, compares the
	 * ender chest with its copy from the last tick and, if it changed (a block's screen, the screen {@link #open}
	 * opens, a second ovve, a command) or a preview read tore, resends those slots. Players without one cost a
	 * scan of their menus' slots; nothing here throws.
	 */
	private static void tick(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (SEEN.size() > players.size() || TORN.size() > players.size()) {
			Set<UUID> online = new HashSet<>();
			for (ServerPlayer player : players) online.add(player.getUUID());
			SEEN.keySet().retainAll(online);
			TORN.retainAll(online);
		}
		for (ServerPlayer player : players) {
			try {
				refresh(player);
			} catch (RuntimeException e) {
				Ovvar.LOGGER.debug("[{}] ender pockets: keeping {}'s preview current: {}", Ovvar.MOD_ID, player.getUUID(), e.toString());
			}
		}
	}

	private static void refresh(ServerPlayer player) {
		UUID id = player.getUUID();
		if (!carries(player)) {
			SEEN.remove(id);
			TORN.remove(id);
			return;
		}
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		List<ItemStack> seen = SEEN.get(id);
		boolean changed = seen != null && !same(seen, ender);
		if (seen == null || changed) SEEN.put(id, snapshot(ender));
		// The first time a player has one, its slot was just sent with the chest as it is.
		if (TORN.remove(id) || changed) resend(player);
	}

	private static boolean carries(ServerPlayer player) {
		AbstractContainerMenu open = player.containerMenu;
		if (anyIn(open)) return true;
		return open != player.inventoryMenu && anyIn(player.inventoryMenu);
	}

	private static boolean anyIn(AbstractContainerMenu menu) {
		if (isEnderOvve(menu.getCarried())) return true;
		for (Slot slot : menu.slots) if (isEnderOvve(slot.getItem())) return true;
		return false;
	}

	private static boolean isEnderOvve(ItemStack stack) {
		return stack.getItem() instanceof OvveItem && has(stack);
	}

	private static boolean same(List<ItemStack> seen, Container ender) {
		if (seen.size() != ender.getContainerSize()) return false;
		for (int i = 0; i < seen.size(); i++) if (!ItemStack.matches(seen.get(i), ender.getItem(i))) return false;
		return true;
	}

	private static List<ItemStack> snapshot(Container ender) {
		List<ItemStack> copy = new ArrayList<>(ender.getContainerSize());
		for (int i = 0; i < ender.getContainerSize(); i++) copy.add(ender.getItem(i).copy());
		return copy;
	}

	/**
	 * Makes the menus believe the client has nothing in each slot (and cursor) holding an Ender Pockets ovve,
	 * so their next {@code broadcastChanges} sends it with the chest as it is now, and sends the open menu's
	 * at once. The inventory menu behind an open screen goes when it is back in front (its armour and offhand
	 * slots; the rest the open menu has just sent). A selection made in the old view is cleared, since its
	 * index may now point at a different stack.
	 */
	private static void resend(ServerPlayer player) {
		forget(player.containerMenu);
		if (player.containerMenu != player.inventoryMenu) forget(player.inventoryMenu);
		player.containerMenu.broadcastChanges();
	}

	private static void forget(AbstractContainerMenu menu) {
		for (int i = 0; i < menu.slots.size(); i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (!isEnderOvve(stack)) continue;
			stack.remove(ModComponents.ENDER_SELECTED);
			menu.setRemoteSlot(i, ItemStack.EMPTY);
		}
		if (isEnderOvve(menu.getCarried())) {
			menu.getCarried().remove(ModComponents.ENDER_SELECTED);
			menu.setRemoteCarried(HashedStack.EMPTY);
		}
	}

	// ---- use

	/** Use: the ender chest screen, the same as the block's (EnderChestBlock). */
	public static InteractionResult open(ItemStack ovve, Player player) {
		spill(ovve, player);
		if (player instanceof ServerPlayer p) {
			p.getEnderChestInventory().setActiveChest(null);
			p.openMenu(new SimpleMenuProvider((id, inventory, opener) -> ChestMenu.threeRows(id, inventory, opener.getEnderChestInventory()),
					Component.translatable("container.enderchest")));
		}
		return InteractionResult.SUCCESS;
	}

	// ---- the client stack

	/**
	 * {@code OvveItem.getPolymerItemStack}'s part: builds the client stack with {@code build} and gives it the
	 * viewer's view as its pockets. While {@code build} runs, the fill bar ({@link #fillBar}) reads the view
	 * from {@link #BUILDING}, and an Ender Pockets ovve converted inside it gets an empty view.
	 */
	public static ItemStack clientStack(ItemStack ovve, @Nullable PacketContext context, Supplier<ItemStack> build) {
		BundleContents view = shownTo(ovve, context);
		BundleContents outer = BUILDING.get();
		BUILDING.set(view);
		ItemStack out;
		try {
			out = build.get();
		} finally {
			if (outer == null) BUILDING.remove();
			else BUILDING.set(outer);
		}
		out.set(DataComponents.BUNDLE_CONTENTS, view);
		return out;
	}

	private static BundleContents shownTo(ItemStack ovve, @Nullable PacketContext context) {
		if (ovve.has(ModComponents.ENDER_NESTED) || BUILDING.get() != null) return BundleContents.EMPTY;
		return view(viewer(context), ovve.getOrDefault(ModComponents.ENDER_SELECTED, -1));
	}

	/**
	 * The player a client stack is for, or null: by the login's profile, as OvveTop finds it, else by the
	 * connection's play listener (a connection that never logged in, like a game test's mock player, has no profile).
	 */
	private static @Nullable ServerPlayer viewer(@Nullable PacketContext context) {
		if (context == null) return null;
		MinecraftServer server = context.get(PacketContext.SERVER_INSTANCE);
		GameProfile profile = context.get(PacketContext.GAME_PROFILE);
		if (server != null && profile != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(profile.id());
			if (player != null) return player;
		}
		Connection connection = context.get(PacketContext.CONNECTION);
		return connection != null && connection.getPacketListener() instanceof ServerGamePacketListenerImpl listener ? listener.player : null;
	}

	/** Swaps metacraft-bundles' fill bar (the ovve's own, empty pockets) for the ender chest's. */
	private static ItemStack fillBar(ItemStack server, ItemStack client, PacketContext context) {
		if (!(server.getItem() instanceof OvveItem) || !has(server)) return client;
		Component own = BundleHelper.getOccupancyText(server);
		ItemLore lore = client.get(DataComponents.LORE);
		// No bar to swap: metacraft-bundles' rendering is off.
		if (own == null || lore == null || !lore.lines().contains(own)) return client;
		BundleContents view = BUILDING.get();
		if (view == null) view = shownTo(server, context);
		List<Component> lines = new ArrayList<>(lore.lines());
		lines.remove(own);
		lines.addFirst(BundleHelper.getOccupancyText(view.weight().map(EnderPockets::chestShare)));
		client.set(DataComponents.LORE, new ItemLore(lines));
		return client;
	}

	private static Fraction chestShare(Fraction weight) {
		Fraction share = weight.divideBy(Fraction.getFraction(ENDER_SLOTS, 1));
		return share.compareTo(Fraction.ONE) > 0 ? Fraction.ONE : share;
	}
}
