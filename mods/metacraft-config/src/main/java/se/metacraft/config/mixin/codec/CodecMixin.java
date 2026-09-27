package se.metacraft.config.mixin.codec;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import se.metacraft.config.comments.CodecExtension;
import se.metacraft.config.comments.codecs.CodecWithComment;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Codec.class)
public interface CodecMixin<T> extends CodecExtension<T> {

	default Codec<T> metacraft$comment(Component comment) {
		//noinspection unchecked
		return new CodecWithComment<>((Codec<T>) this, comment);
	}

}
