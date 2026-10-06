package se.metacraft.config.mixin.codec;

import com.mojang.serialization.Codec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {"com.mojang.serialization.Codec$1", "com.mojang.serialization.Codec$4"})
public interface MappedCodecAccessor {

	@Accessor("this$0")
	Codec<?> getParent();

}
