package se.metacraft.config.mixin.codec;

import com.mojang.serialization.MapDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {
	"com.mojang.serialization.MapDecoder$3",
	"com.mojang.serialization.MapDecoder$4",
	"com.mojang.serialization.MapDecoder$5",
	"com.mojang.serialization.MapDecoder$6"
})
public interface MappedMapDecoderAccessor {

	@Accessor("this$0")
	MapDecoder<?> getParent();

}
