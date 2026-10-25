package se.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ContainerComponent;
import net.minecraft.world.item.component.GrowableMutableContainer;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record PortableJukeboxInvData(Optional<ItemStackTemplate> item) implements ContainerComponent<PortableJukeboxInvData> {

	public static final PortableJukeboxInvData EMPTY = new PortableJukeboxInvData(Optional.empty());

	public static final Codec<PortableJukeboxInvData> CODEC = Codec.withAlternative(
		ItemStackTemplate.CODEC.optionalFieldOf("item").codec(), ItemStackTemplate.CODEC.xmap(Optional::of, Optional::get)
	).xmap(PortableJukeboxInvData::new, PortableJukeboxInvData::item);

	@Override
	public @NonNull Stream<ItemStack> itemCopies() {
		return item.stream().map(ItemStackTemplate::create);
	}

	@Override
	public int size() {
		return item.isPresent() ? 1 : 0;
	}

	@Override
	public @NonNull PortableJukeboxInvData copyWithContents(@NonNull Stream<ItemStack> newContents) {
		return new PortableJukeboxInvData(newContents.findAny().map(ItemStackTemplate::fromStack));
	}

	@Override
	public @NonNull MutableJukebox asMutable() {
		return new MutableJukebox(item.stream().map(ItemStackTemplate::create).toList());
	}

	public PortableJukeboxInvData withItem(ItemStack item) {
		return new PortableJukeboxInvData(item.isEmpty() ? Optional.empty() : Optional.of(ItemStackTemplate.fromNonEmptyStack(item)));
	}

	public ItemStack getItem() {
		return getNonEmptyItem().orElse(ItemStack.EMPTY);
	}

	public Optional<ItemStack> getNonEmptyItem() {
		return item.map(ItemStackTemplate::create);
	}

	public static class MutableJukebox extends GrowableMutableContainer<PortableJukeboxInvData> {

		public MutableJukebox(List<ItemStack> items) {
			super(items);
		}

		@Override
		public boolean canInsertNewSlots() {
			return items.isEmpty();
		}

		@Override
		protected boolean setItem(final int slot, final @NonNull ItemStack itemStack) {
			if (!itemStack.has(DataComponents.JUKEBOX_PLAYABLE) && !itemStack.isEmpty()) return false;
			return super.setItem(slot, itemStack);
		}

		@Override
		public @NonNull PortableJukeboxInvData toImmutable() {
			return new PortableJukeboxInvData(items.stream().filter(i -> !i.isEmpty()).map(s -> s.copyWithCount(1)).findAny().map(ItemStackTemplate::fromStack));
		}
	}
}
