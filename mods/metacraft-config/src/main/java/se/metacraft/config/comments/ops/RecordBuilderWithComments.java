package se.metacraft.config.comments.ops;

import com.mojang.serialization.RecordBuilder;
import net.minecraft.network.chat.Component;

import java.util.Map;

public interface RecordBuilderWithComments<T> extends RecordBuilder<T> {

	RecordBuilderWithComments<T> metacraft$addComments(Map<T, Component> comments);

}
