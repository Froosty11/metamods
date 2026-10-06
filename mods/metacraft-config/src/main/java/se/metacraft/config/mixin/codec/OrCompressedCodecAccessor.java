package se.metacraft.config.mixin.codec;

import com.mojang.serialization.Codec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net/minecraft/util/ExtraCodecs$2")
public interface OrCompressedCodecAccessor {

	@Accessor("val$compressed")
	Codec<?> getCompressed();

}
