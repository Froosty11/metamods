package se.metacraft.config.mixin.codec;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.PairMapCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PairMapCodec.class)
public interface PairMapCodecAccessor {

	@Accessor
	MapCodec<?> getFirst();

	@Accessor
	MapCodec<?> getSecond();

}
