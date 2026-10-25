package se.metacraft.portable_jukebox.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.item.components.PortableJukeboxInvData;

public class PortableJukeboxInventory implements Container {

	private final ItemStack portableJukebox;
	private final Runnable onUpdate;

	public PortableJukeboxInventory(ItemStack portableJukebox, Runnable onUpdate) {
		this.portableJukebox = portableJukebox;
		this.onUpdate = onUpdate;
	}

	private ItemStack getStack() {
		return portableJukebox.getOrDefault(Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY).getItem();
	}

	private ItemStack remove() {
		var prev = portableJukebox.update(
			Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY, jukebox -> jukebox.withItem(ItemStack.EMPTY)
		);
		return prev != null ? prev.getItem() : ItemStack.EMPTY;
	}

	@Override
	public int getContainerSize() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return getStack().isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return getStack();
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		var stack = getStack().split(amount);
		if (getStack().isEmpty()) {
			remove();
		}
		return stack.isEmpty() ? ItemStack.EMPTY : stack;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return remove();
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (!stack.isEmpty()) {
			portableJukebox.update(Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY, jukebox -> jukebox.withItem(stack));
		} else {
			remove();
		}
	}

	@Override
	public void setChanged() {
		onUpdate.run();
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		remove();
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	public ItemStack getJukebox() {
		return portableJukebox;
	}
}
