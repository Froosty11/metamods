package metacraft.moredyes.banner;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.color.ModColor;
import net.minecraft.util.Prediction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BannerPatternTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The "dye loom": right-click with one of our dyes while holding a banner (or a shield with a base
 * colour) in the other hand. Lists every derived pattern for that colour as a banner already
 * carrying the layer, so the vanilla client renders the preview itself; clicking applies the layer
 * (with WHITE as the carrier colour) and consumes the dye.
 *
 * <p>A pattern that needs a pattern item at a loom (vanilla's flower or globe, kultur's Pirkko — anything
 * outside {@code #minecraft:no_item_required}) needs it here too: an item in the player's inventory
 * that provides it. As at a loom, the item is not used up.
 *
 * <p>The vanilla loom is deliberately not used: its client-side preview runs the same handler logic
 * on the client's view of the items and would list the wrong patterns for a Polymer-mapped dye.
 */
public final class DyeLoomGui extends SimpleGui {
	private static final int PAGE = 45;
	private static final int MAX_LAYERS = 6;

	private final ModColor color;
	private final InteractionHand dyeHand;
	private final List<Holder.Reference<BannerPattern>> patterns;
	private int page;

	public static boolean canOpen(ItemStack target) {
		if (target.getItem() instanceof BannerItem) return true;
		return target.is(Items.SHIELD) && target.has(DataComponents.BASE_COLOR);
	}

	public DyeLoomGui(ServerPlayer player, ModColor color, InteractionHand dyeHand) {
		super(MenuType.GENERIC_9x6, player, false);
		this.color = color;
		this.dyeHand = dyeHand;
		Registry<BannerPattern> registry = player.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		this.patterns = new ArrayList<>();
		String prefix = color.id() + "/";
		registry.listElements().forEach(ref -> {
			var id = ref.key().identifier();
			if (id.getNamespace().equals(MoreDyes.MOD_ID) && id.getPath().startsWith(prefix) && offered(player, registry, ref)) {
				patterns.add(ref);
			}
		});
		patterns.sort(Comparator.comparing(ref -> ref.key().identifier().getPath()));
		setTitle(Component.literal(color.name() + " patterns"));
		render();
	}

	/**
	 * Whether the player may apply this derived pattern: its source is free in any loom, or something in
	 * their inventory provides the source the way a pattern item does in the loom's slot.
	 */
	public static boolean offered(ServerPlayer player, Registry<BannerPattern> registry, Holder<BannerPattern> derived) {
		BannerPatterns.Derived d = derived.unwrapKey().map(key -> BannerPatterns.derived(key.identifier())).orElse(null);
		if (d == null) return false;
		var source = registry.get(d.source());
		if (source.isEmpty()) return false;
		if (source.get().is(BannerPatternTags.NO_ITEM_REQUIRED)) return true;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			HolderSet<BannerPattern> provided = inventory.getItem(i).get(DataComponents.PROVIDES_BANNER_PATTERNS);
			if (provided != null && provided.contains(source.get())) return true;
		}
		return false;
	}

	private ItemStack target() {
		return player.getItemInHand(dyeHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
	}

	private void render() {
		ItemStack base = target();
		int pages = Math.max(1, (patterns.size() + PAGE - 1) / PAGE);
		page = Math.floorMod(page, pages);
		for (int i = 0; i < PAGE; i++) {
			int index = page * PAGE + i;
			if (index >= patterns.size()) {
				clearSlot(i);
				continue;
			}
			Holder.Reference<BannerPattern> pattern = patterns.get(index);
			ItemStack preview = withLayer(base, pattern);
			setSlot(i, GuiElementBuilder.from(preview)
					.setName(Component.translatable(pattern.value().translationKey() + ".white"))
					.setCallback(() -> apply(pattern)));
		}
		setSlot(45, new GuiElementBuilder(Items.ARROW).setName(Component.literal("Previous page"))
				.setCallback(() -> { page--; render(); }));
		setSlot(49, new GuiElementBuilder(Items.DYE.white()).setName(Component.literal("Page " + (page + 1) + " / " + pages))
				.addLoreLine(Component.literal("Click a pattern to add it in " + color.name())));
		setSlot(53, new GuiElementBuilder(Items.ARROW).setName(Component.literal("Next page"))
				.setCallback(() -> { page++; render(); }));
	}

	private static ItemStack withLayer(ItemStack base, Holder<BannerPattern> pattern) {
		ItemStack stack = base.copyWithCount(1);
		BannerPatternLayers layers = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		List<BannerPatternLayers.Layer> list = new ArrayList<>(layers.layers());
		list.add(new BannerPatternLayers.Layer(pattern, DyeColor.WHITE));
		stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(list));
		return stack;
	}

	private void apply(Holder.Reference<BannerPattern> pattern) {
		ItemStack target = target();
		ItemStack dye = player.getItemInHand(dyeHand);
		Registry<BannerPattern> registry = player.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		// The pattern item may have left the inventory since the list was drawn.
		if (!canOpen(target) || dye.isEmpty() || !offered(player, registry, pattern)) {
			close();
			return;
		}
		BannerPatternLayers layers = target.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		if (layers.layers().size() >= MAX_LAYERS) {
			player.sendOverlayMessage(Component.literal("This banner already has " + MAX_LAYERS + " patterns"));
			close();
			return;
		}
		ItemStack result = withLayer(target, pattern);
		result.setCount(1);
		if (target.getCount() > 1) {
			target.shrink(1);
			if (!player.getInventory().add(result)) player.drop(result, false, Prediction.SERVER_ONLY);
		} else {
			player.setItemInHand(dyeHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, result);
		}
		if (!player.getAbilities().instabuild) dye.shrink(1);
		player.level().playSound(null, player.blockPosition(), SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
		close();
	}
}
