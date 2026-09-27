package se.metacraft.config.comments;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;

public interface MapCodecExtension<T> {

	MapCodec<T> metacraft$comment(Component comment);

}
