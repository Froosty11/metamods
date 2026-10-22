package se.metacraft.config.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.throwables.MixinError;

import java.util.List;

@Mixin(TranslatableContents.class)
public interface TranslatableContentsAccessor {

	@Accessor("PRIMITIVE_ARG_CODEC")
	static Codec<Object> getPrimitiveArgCodec() {
		throw new MixinError("");
	}

	@Accessor
	void setDecomposedWith(@Nullable Language language);

	@Accessor
	List<FormattedText> getDecomposedParts();

	@Invoker
	void callDecompose();

}
