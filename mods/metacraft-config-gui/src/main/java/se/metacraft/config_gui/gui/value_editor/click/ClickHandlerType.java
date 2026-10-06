package se.metacraft.config_gui.gui.value_editor.click;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

public class ClickHandlerType<T extends ClickHandler> {

	public static final Codec<ClickHandlerType<?>> CODEC = ClickHandlerRegistry.REGISTRY.byNameCodec();

	private final Either<MapCodec<T>, T> codec;

	public ClickHandlerType(MapCodec<T> codec) {
		this.codec = Either.left(codec);
	}

	public ClickHandlerType(T instance) {
		this.codec = Either.right(instance);
	}

	public Optional<T> singleton() {
		return codec.right();
	}

	public MapCodec<? extends ClickHandler> codec() {
		return codec.map(l -> l, MapCodec::unit);
	}

}
