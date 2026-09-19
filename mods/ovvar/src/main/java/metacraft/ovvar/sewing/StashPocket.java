package metacraft.ovvar.sewing;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.PatchItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.store.Stash;
import metacraft.ovvar.store.Wardrobe;
import metacraft.ovvar.store.Wardrobes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The wardrobe screen's pocket as a chest: a container of real slots the client picks from and
 * drops into like any other, mirroring one page of the stash — one slot per kind, holding as
 * many as the stash has of it (a stack at most), and the slots after them free to put patches in.
 * <p>
 * A stash is a versioned row in a store shared by every server, not an inventory, so the mirror is
 * optimistic: the moment a slot changes (a pick-up, a shift-click, a drop) the difference per kind
 * since the last fill becomes one compare-and-set write. When the store says yes the slots are
 * filled again from it (a kind with more than a stack of it tops up; a kind put in shows its whole
 * count). When it says no, what the player took is pulled back out of their hands — the cursor
 * first, then the inventory — and what they put in is handed back, and the slots show the stash as
 * the store holds it. Slots the player changes while a write is in flight are reconciled once it
 * lands, so quick clicks are never lost. The layout is fixed for the life of the screen: a kind
 * that runs out leaves its slot empty rather than shuffling the rest along under the pointer.
 */
public final class StashPocket extends SimpleContainer {
	private static final int STACK = 64;

	private final ServerPlayer player;
	private final UUID owner;
	/** The kind each slot holds, fixed at build; null until a patch is put into a free slot. */
	private final Patches.@Nullable Patch[] kinds;
	/** What each slot showed at the last fill or the last write: the baseline a change is measured from. */
	private final int[] shown;
	/** The wardrobe the slots were last filled from; what they fall back to while the cache refetches. */
	private Wardrobe base = Wardrobe.NONE;
	private boolean filling, writing, dirty;

	public StashPocket(ServerPlayer player, UUID owner, List<Patches.Patch> pageKinds, int size) {
		super(size);
		this.player = player;
		this.owner = owner;
		this.kinds = new Patches.Patch[size];
		this.shown = new int[size];
		for (int i = 0; i < Math.min(size, pageKinds.size()); i++) kinds[i] = pageKinds.get(i);
	}

	/** The real slot for the screen to put at its {@code i}th pocket position. */
	public Slot slot(int i) {
		return new PocketSlot(this, i);
	}

	// ---- mirroring the stash

	/** The slots as the wardrobe holds them; each kind in its first slot, the rest cleared. */
	public void fill(Wardrobe wardrobe) {
		base = wardrobe;
		filling = true;
		try {
			for (int i = 0; i < getContainerSize(); i++) {
				Patches.Patch kind = kinds[i];
				int count = kind == null || repeated(i) ? 0 : Math.min(wardrobe.count(kind), STACK);
				setItem(i, count > 0 ? new ItemStack(ModContent.patchItem(kind), count) : ItemStack.EMPTY);
				shown[i] = count;
			}
		} finally {
			filling = false;
		}
	}

	/**
	 * Every tick the screen is open: the stash changed under the slots (a refetch after a lost race,
	 * a patch earned or sewn elsewhere) and the player has no change of their own waiting, so the
	 * slots follow the store.
	 */
	public void tick() {
		if (writing || !Wardrobes.loaded(owner)) return;
		Wardrobe now = Wardrobes.current(owner);
		if (now == base || !delta().isEmpty()) return;
		fill(now);
	}

	/** Does an earlier slot already hold this slot's kind (a patch put into a free slot beside its own)? */
	private boolean repeated(int i) {
		for (int j = 0; j < i; j++) if (kinds[j] == kinds[i]) return true;
		return false;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (filling) return;
		if (writing) {
			dirty = true;
			return;
		}
		reconcile();
	}

	/** Per kind, how the slots differ from the baseline; a patch put into a free slot claims it. */
	private Map<Patches.Patch, Integer> delta() {
		Map<Patches.Patch, Integer> delta = new LinkedHashMap<>();
		for (int i = 0; i < getContainerSize(); i++) {
			ItemStack stack = getItem(i);
			if (kinds[i] == null && stack.getItem() instanceof PatchItem put) kinds[i] = put.patch;
			if (kinds[i] == null) continue;
			delta.merge(kinds[i], stack.getCount() - shown[i], Integer::sum);
		}
		delta.values().removeIf(d -> d == 0);
		return delta;
	}

	/** Writes what changed since the baseline, then fills again from the store or takes it back. */
	private void reconcile() {
		Map<Patches.Patch, Integer> delta = delta();
		if (delta.isEmpty()) return;
		for (int i = 0; i < getContainerSize(); i++) shown[i] = getItem(i).getCount();
		writing = true;
		Wardrobes.update(owner, w -> {
			for (Map.Entry<Patches.Patch, Integer> e : delta.entrySet()) {
				if (w.count(e.getKey()) + e.getValue() < 0) return null;
				w = w.add(e.getKey(), e.getValue());
			}
			return w;
		}, outcome -> {
			writing = false;
			// What the player did to the slots while the store was answering, to keep after the refill.
			Map<Patches.Patch, Integer> meanwhile = delta();
			if (outcome == Wardrobes.Outcome.OK) {
				player.sendOverlayMessage(Component.literal(describe(delta)));
			} else {
				takeBack(delta);
				player.sendOverlayMessage(Component.literal(switch (outcome) {
					case REJECTED -> "The stash does not hold that many";
					case CONFLICT -> "The stash changed on another server, try again";
					case NOT_LOADED -> "Your wardrobe is still loading, try again in a moment";
					default -> "The wardrobe store cannot be reached, try again later";
				}));
			}
			// A lost race empties the cache until the refetch lands: until then the slots keep what
			// they showed, and tick() fills them from the store once it is back.
			fill(Wardrobes.loaded(owner) ? Wardrobes.current(owner) : base);
			if (dirty) {
				dirty = false;
				replay(meanwhile);
				reconcile();
			}
		});
	}

	/** Puts a change made during a write back onto the refilled slots, so the next write carries it. */
	private void replay(Map<Patches.Patch, Integer> meanwhile) {
		filling = true;
		try {
			for (Map.Entry<Patches.Patch, Integer> e : meanwhile.entrySet()) {
				int i = slotOf(e.getKey());
				if (i < 0) continue;
				int count = Math.max(0, Math.min(STACK, shown[i] + e.getValue()));
				setItem(i, count > 0 ? new ItemStack(ModContent.patchItem(e.getKey()), count) : ItemStack.EMPTY);
			}
		} finally {
			filling = false;
		}
	}

	private int slotOf(Patches.Patch kind) {
		for (int i = 0; i < kinds.length; i++) if (kinds[i] == kind) return i;
		return -1;
	}

	/** "3 Nyckeln taken out of the stash", "1 ITK put in", or both. */
	private static String describe(Map<Patches.Patch, Integer> delta) {
		StringBuilder out = new StringBuilder();
		for (Map.Entry<Patches.Patch, Integer> e : delta.entrySet()) {
			if (!out.isEmpty()) out.append(", ");
			int n = Math.abs(e.getValue());
			out.append(n).append(' ').append(e.getKey().name()).append(e.getValue() < 0 ? " taken out of the stash" : " put in the stash");
		}
		return out.toString();
	}

	/** The store said no: what was taken comes back out of the player's hands, what was put in goes back to them. */
	private void takeBack(Map<Patches.Patch, Integer> delta) {
		for (Map.Entry<Patches.Patch, Integer> e : delta.entrySet()) {
			if (e.getValue() > 0) {
				Stash.give(player, e.getKey(), e.getValue());
				continue;
			}
			int left = -e.getValue();
			ItemStack carried = player.containerMenu.getCarried();
			left -= shrink(carried, e.getKey(), left);
			var inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) left -= shrink(inventory.getItem(i), e.getKey(), left);
			if (left > 0) Ovvar.LOGGER.warn("[ovvar] stash: {} of {} could not be taken back from {} after a refused write", left, e.getKey().id(), player.getName().getString());
		}
	}

	private static int shrink(ItemStack stack, Patches.Patch kind, int upTo) {
		if (stack.getItem() != ModContent.patchItem(kind) || stack.has(ModComponents.SESSION)) return 0;
		int n = Math.min(upTo, stack.getCount());
		stack.shrink(n);
		return n;
	}

	/** A pocket slot: only real patches go in, never a session's fake one. */
	private static final class PocketSlot extends Slot {
		PocketSlot(StashPocket pocket, int index) {
			super(pocket, index, 0, 0);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.getItem() instanceof PatchItem && !stack.has(ModComponents.SESSION);
		}
	}
}
