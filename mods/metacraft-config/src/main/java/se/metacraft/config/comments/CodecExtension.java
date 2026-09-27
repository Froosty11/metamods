package se.metacraft.config.comments;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

public interface CodecExtension<T> {

	Codec<T> metacraft$comment(Component comment);

}
