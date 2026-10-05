package se.metacraft.config.mixin.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.OptionalFieldCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionalFieldCodec.class)
public interface OptionalFieldCodecAccessor {

	@Accessor
	String getName();

	@Accessor
	Codec<?> getElementCodec();

}
