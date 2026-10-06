package se.metacraft.config.mixin.codec;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.codecs.KeyDispatchCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

@Mixin(KeyDispatchCodec.class)
public interface KeyDispatchCodecAccessor<K, V> {

	@Accessor
	MapCodec<K> getKeyCodec();

	@Accessor
	Function<? super V, ? extends DataResult<? extends K>> getType();

	@Accessor
	Function<? super K, ? extends DataResult<? extends MapDecoder<? extends V>>> getDecoder();

	@Accessor
	Function<? super V, ? extends DataResult<? extends MapEncoder<V>>> getEncoder();

}
