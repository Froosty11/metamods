package se.metacraft.config.mixin.codec;

import com.mojang.serialization.MapCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {"com.mojang.serialization.MapCodec$3", "com.mojang.serialization.MapCodec$4"})
public interface MappedMapCodecAccessor {

	@Accessor("this$0")
	MapCodec<?> getParent();

}
