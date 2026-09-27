package se.metacraft.config.mixin.codec;

import com.mojang.serialization.Decoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
	targets = {
		"com.mojang.serialization.Decoder$1",
		"com.mojang.serialization.Decoder$2",
		"com.mojang.serialization.Decoder$3",
		"com.mojang.serialization.Decoder$4"
	}
)
public interface MappedDecoderAccessor {

	@Accessor("this$0")
	Decoder<?> getParent();

}
