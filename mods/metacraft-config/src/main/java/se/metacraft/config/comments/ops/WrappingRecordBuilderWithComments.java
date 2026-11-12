package se.metacraft.config.comments.ops;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Unique;
import se.metacraft.config.mixin.TranslatableContentsAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public class WrappingRecordBuilderWithComments<T> implements RecordBuilderWithComments<T> {

	private final Map<T, Component> comments = new HashMap<>();
	private RecordBuilder<T> builder;

	public WrappingRecordBuilderWithComments(RecordBuilder<T> builder) {
		this.builder = builder;
	}

	private RecordBuilder<T> update(RecordBuilder<T> builder) {
		if (builder != this.builder) {
			this.builder = builder;
		}
		return builder;
	}

	@Override
	public RecordBuilderWithComments<T> metacraft$addComments(Map<T, Component> comments) {
		this.comments.putAll(comments);
		return this;
	}

	@Override
	public DynamicOps<T> ops() {
		return builder.ops();
	}

	@Override
	public RecordBuilder<T> add(T key, T value) {
		return update(builder.add(key, value));
	}

	@Override
	public RecordBuilder<T> add(T key, DataResult<T> value) {
		return update(builder.add(key, value));
	}

	@Override
	public RecordBuilder<T> add(DataResult<T> key, DataResult<T> value) {
		return update(builder.add(key, value));
	}

	@Override
	public RecordBuilder<T> withErrorsFrom(DataResult<?> result) {
		return update(builder.withErrorsFrom(result));
	}

	@Override
	public RecordBuilder<T> setLifecycle(Lifecycle lifecycle) {
		return update(builder.setLifecycle(lifecycle));
	}

	@Override
	public RecordBuilder<T> mapError(UnaryOperator<String> onError) {
		return update(builder.mapError(onError));
	}

	@Unique
	private static void fix(Component message) {
		if (message.getContents() instanceof TranslatableContents translatable) {
			var translatableAccessor = (TranslatableContentsAccessor) translatable;
			if (translatableAccessor.getDecomposedParts().size() == 1) {
				var string = translatableAccessor.getDecomposedParts().getFirst().getString();
				if (string.equals(translatable.getFallback()) || string.equals(translatable.getKey())) {
					translatableAccessor.setDecomposedWith(null);
					translatableAccessor.callDecompose();
				}
			}
			for (Object arg : translatable.getArgs()) {
				if (arg instanceof Component c) {
					fix(c);
				}
			}
		}
		message.getSiblings().forEach(WrappingRecordBuilderWithComments::fix);
	}

	@Override
	public DataResult<T> build(T prefix) {
		var result = builder.build(prefix);
		if (ops() instanceof OpsWithComments<T> c) {
			comments.values().forEach(WrappingRecordBuilderWithComments::fix);
			return result.flatMap(v -> c.metacraft$withComments(v, this.comments));
		}
		return result;
	}
}
