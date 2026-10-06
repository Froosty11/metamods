package se.metacraft.config.mixin.codec;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import se.metacraft.config.comments.MapCodecExtension;
import se.metacraft.config.comments.codecs.MapCodecWithComments;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;
import java.util.Optional;

@Mixin(MapCodec.class)
public class MapCodecMixin<T> implements MapCodecExtension<T> {

	@Override
	public MapCodec<T> metacraft$comment(Component comment) {
		//noinspection unchecked
		return new MapCodecWithComments<>((MapCodec<T>) (Object) this, Map.of(), Optional.of(comment));
	}

}
