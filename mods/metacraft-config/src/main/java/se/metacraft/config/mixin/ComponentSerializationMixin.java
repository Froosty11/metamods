package se.metacraft.config.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config.util.CapturedCodecs;

@Mixin(ComponentSerialization.class)
public class ComponentSerializationMixin {

	@ModifyExpressionValue(
		method = "createCodec", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/chat/ComponentSerialization;createLegacyComponentMatcher(Lnet/minecraft/util/ExtraCodecs$LateBoundIdMapper;Ljava/util/function/Function;Ljava/lang/String;)Lcom/mojang/serialization/MapCodec;"
		)
	)
	private static MapCodec<?> createCodec(MapCodec<?> original) {
		var parsed = CodecParser.parse(original, HolderLookup.Provider.create(BuiltInRegistries.REGISTRY.stream().map(e -> e)));
		CapturedCodecs.CAPTURED_CODECS.put(
			CapturedCodecs.COMPONENT_TYPE, parsed.getUnderlying(
				e -> e.underlying().isEmpty()
			).orElseThrow().components().getFirst().mapCodec().orElseThrow()
		);
		return original;
	}

}
