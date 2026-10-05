package se.metacraft.playertrading.shop;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.inventory.SlotRange;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record SlotMap<T>(
	PMap<SlotRange, T> slots, Int2ObjectMap<PVector<SlotRange>> slotLookup
) {

	public static <T> Codec<SlotMap<T>> codec(Codec<PMap<SlotRange, T>> mapCodec) {
		return mapCodec.xmap(SlotMap::new, SlotMap::slots);
	}

	public static <T> SlotMap<T> of(PMap<SlotRange, T> empty) {
		return new SlotMap<>(empty);
	}

	public SlotMap(PMap<SlotRange, T> slots) {
		this(slots, from(slots.keySet()));
	}

	public Stream<T> get(int slot) {
		return getSlotRanges(slot).stream().map(slots::get).filter(Objects::nonNull);
	}

	public List<SlotRange> getSlotRanges(int slot) {
		return slotLookup.getOrDefault(slot, TreePVector.empty());
	}

	private static Int2ObjectMap<PVector<SlotRange>> from(Collection<SlotRange> slots) {
		Int2ObjectMap<PVector<SlotRange>> slotLookup = new Int2ObjectOpenHashMap<>();
		slots.forEach(range -> range.slots().forEach(slot -> {
			slotLookup.compute(slot, (s, existing) -> {
				if (existing == null) {
					return TreePVector.singleton(range);
				} else {
					return existing.plus(range);
				}
			});
		}));
		return slotLookup;
	}

}
