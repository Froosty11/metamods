package se.metacraft.config.mixin.codec;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.codec.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryFileCodec.class)
public interface RegistryFileCodecAccessor<T> {

	@Accessor
	ResourceKey<? extends Registry<T>> getRegistryKey();

	@Accessor
	Codec<T> getElementCodec();


	@Accessor
	boolean getAllowInline();

}
