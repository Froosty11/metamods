package nu.metacraft.nether_rifts.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A rift ghast's fireball still blasts (vanilla's weak blast) but sets nothing alight. */
@Mixin(LargeFireball.class)
public abstract class LargeFireballMixin {

	@ModifyArg(
			method = "onHit",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)V"),
			index = 5
	)
	private boolean netherRifts$noRiftFire(boolean fire) {
		return fire && !RiftMobs.isRiftMob(((Projectile) (Object) this).getOwner());
	}
}
