package se.metacraft.config.mixin.codec;

import com.mojang.serialization.MapDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {"com.mojang.serialization.MapCodec$2", "com/mojang/serialization/Codec$3"})
public interface SimpleMapCodecAccessor {

	@Accessor("val$decoder")
	MapDecoder<?> getDecoder();

}
