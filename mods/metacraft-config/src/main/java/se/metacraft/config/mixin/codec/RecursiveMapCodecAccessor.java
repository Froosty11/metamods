package se.metacraft.config.mixin.codec;

import com.mojang.serialization.MapCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(targets = "com.mojang.serialization.MapCodec$RecursiveMapCodec")
public interface RecursiveMapCodecAccessor<T> {
	@Accessor
	Supplier<MapCodec<T>> getWrapped();
}
