package se.metacraft.config.mixin.codec;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(targets = {"com/mojang/serialization/MapCodec$10", "com/mojang/serialization/MapCodec$11"})
public interface UnitCodecAccessor {

	@Accessor("val$value")
	Supplier<?> getValue();

}
