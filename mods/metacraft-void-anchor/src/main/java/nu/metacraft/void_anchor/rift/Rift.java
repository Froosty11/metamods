package nu.metacraft.void_anchor.rift;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.void_anchor.VoidAnchor;
import org.joml.Vector3f;

/**
 * A flat portal lying in the air: one item display showing the rift model, which grows open and
 * shrinks shut. What it looks like comes from the pack (the rift sprite and the item shader).
 */
public final class Rift extends ElementHolder {

	public static final int OPEN_TICKS = 8;
	public static final int CLOSE_TICKS = 6;

	private static final float CLOSED = 0.01f;

	private final ItemDisplayElement display;
	private final float size;
	private int age = 0;
	private int closingSince = -1;
	private int autoCloseAt = -1;

	private Rift(float size) {
		this.size = size;
		var stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, VoidAnchor.getID("rift"));
		display = new ItemDisplayElement(stack);
		display.setItemDisplayContext(ItemDisplayContext.NONE);
		display.setBrightness(new Brightness(15, 15));
		display.setScale(new Vector3f(CLOSED, 1f, CLOSED));
		display.setViewRange(4f);
		addElement(display);
	}

	public static Rift open(ServerLevel level, Vec3 centre, float size) {
		var rift = new Rift(size);
		ChunkAttachment.ofTicking(rift, level, centre);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5f, 1.6f);
		return rift;
	}

	/** Shuts the rift by itself after this many more ticks. */
	public Rift closeAfter(int ticks) {
		autoCloseAt = age + ticks;
		return this;
	}

	public void close() {
		if (closingSince >= 0) {
			return;
		}
		closingSince = age;
		scaleTo(CLOSED, CLOSE_TICKS);
	}

	public boolean isClosing() {
		return closingSince >= 0;
	}

	@Override
	protected void onTick() {
		age++;
		if (age == 1) {
			// The spawn packet carried the closed scale; grow from it.
			scaleTo(size, OPEN_TICKS);
		}
		if (age == autoCloseAt) {
			close();
		}
		var attachment = getAttachment();
		if (attachment == null) {
			return;
		}
		if (closingSince < 0 && age % 2 == 0) {
			var c = attachment.getPos();
			attachment.getWorld().sendParticles(
					ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.2, c.z, 6, size * 0.35, 0.1, size * 0.35, 0.02
			);
		}
		if (closingSince >= 0 && age - closingSince >= CLOSE_TICKS) {
			destroy();
		}
	}

	private void scaleTo(float scale, int ticks) {
		display.setInterpolationDuration(ticks);
		display.setStartInterpolation(0);
		display.setScale(new Vector3f(scale, 1f, scale));
	}

}
