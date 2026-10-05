package se.metacraft.config.mixin.codec;

import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecordCodecBuilder.class)
public interface RecordCodecBuilderAccessor {

	@Accessor
	MapDecoder<?> getDecoder();

	@Mixin(targets = "com.mojang.serialization.codecs.RecordCodecBuilder$2")
	interface MapCodec {
		@Accessor("val$builder")
		RecordCodecBuilder<?, ?> getBuilder();
	}

}
