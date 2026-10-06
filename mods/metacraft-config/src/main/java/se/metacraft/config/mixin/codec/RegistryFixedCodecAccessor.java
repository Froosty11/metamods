package se.metacraft.config.mixin.codec;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.codec.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryFixedCodec.class)
public interface RegistryFixedCodecAccessor {

	@Accessor
	ResourceKey<? extends Registry<?>> getRegistryKey();

}
