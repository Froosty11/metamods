package se.metacraft.config.mixin.codec;

import com.mojang.serialization.Decoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.serialization.Codec$2")
public interface SimpleCodecAccessor {

	@Accessor("val$decoder")
	Decoder<?> getDecoder();

}
