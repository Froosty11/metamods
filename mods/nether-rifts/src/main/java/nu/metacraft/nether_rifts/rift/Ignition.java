package nu.metacraft.nether_rifts.rift;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Flint and steel or a fire charge on a rift (its frame, or a rift block behind the clicked face) lights it further. */
public final class Ignition {

	private Ignition() {}

	public static void init() {
		UseBlockCallback.EVENT.register(Ignition::onUse);
	}

	static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel serverLevel) || hit.getType() == HitResult.Type.MISS) {
			return InteractionResult.PASS;
		}
		var stack = player.getItemInHand(hand);
		if (!isIgniter(stack)) {
			return InteractionResult.PASS;
		}
		var data = RiftData.get(serverLevel);
		var rift = data.at(hit.getBlockPos()).or(() -> data.at(hit.getBlockPos().relative(hit.getDirection())));
		if (rift.isPresent()) {
			if (rift.get().light(serverLevel)) {
				use(player, hand, stack);
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		return CryingIgnition.tryOpen(serverLevel, player, hand, stack, hit);
	}

	public static boolean isIgniter(ItemStack stack) {
		return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
	}

	/** Wears flint and steel by one, or uses up one fire charge (as simple-custom-features' portals do). */
	public static void use(Player player, InteractionHand hand, ItemStack stack) {
		if (stack.isDamageableItem()) {
			stack.hurtAndBreak(1, player, hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
		} else {
			int count = stack.getCount();
			stack.consume(1, player);
			var remainder = stack.get(DataComponents.USE_REMAINDER);
			if (remainder != null) {
				player.setItemInHand(hand, remainder.convertIntoRemainder(stack, count, player.hasInfiniteMaterials(), player::handleExtraItemsCreatedOnUse));
			}
		}
	}
}
