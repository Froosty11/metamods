package se.metacraft.config.mixin.codec;

import com.mojang.serialization.MapCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net/minecraft/util/ExtraCodecs$3")
public interface OrCompressedMapCodecAccessor {

	@Accessor("val$normal")
	MapCodec<?> getNormal();

	@Accessor("val$compressed")
	MapCodec<?> getCompressed();

}
