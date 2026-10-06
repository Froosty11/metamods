package se.metacraft.config.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.throwables.MixinError;

@Mixin(TranslatableContents.class)
public interface TranslatableContentsAccessor {

	@Accessor("PRIMITIVE_ARG_CODEC")
	static Codec<Object> getPrimitiveArgCodec() {
		throw new MixinError("");
	}

}
