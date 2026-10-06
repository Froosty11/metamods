package se.metacraft.config_gui.mixin;

import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.throwables.MixinError;

@Mixin(TagParser.class)
public interface TagParserAccessor {

	@Accessor("NBT_OPS_PARSER")
	static TagParser<Tag> getNBTOpsParser() {
		throw new MixinError("Error");
	}

}
