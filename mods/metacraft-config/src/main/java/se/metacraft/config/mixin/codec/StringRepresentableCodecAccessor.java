package se.metacraft.config.mixin.codec;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StringRepresentable.StringRepresentableCodec.class)
public interface StringRepresentableCodecAccessor {

	@Accessor
	Codec<?> getCodec();

}
