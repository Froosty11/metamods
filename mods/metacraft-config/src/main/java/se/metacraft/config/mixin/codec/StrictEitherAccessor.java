package se.metacraft.config.mixin.codec;

import com.mojang.serialization.MapCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.network.chat.ComponentSerialization$StrictEither")
public interface StrictEitherAccessor {

	@Accessor
	MapCodec<?> getTyped();

	@Accessor
	MapCodec<?> getFuzzy();

}
