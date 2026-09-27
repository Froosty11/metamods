package se.metacraft.config.mixin.codec;

import com.mojang.serialization.Codec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(Codec.RecursiveCodec.class)
public interface RecursiveCodecAccessor<T> {

	@Accessor
	Supplier<Codec<T>> getWrapped();

}
