package se.metacraft.config.parser.metadata;

import net.minecraft.network.chat.Component;
import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;

import java.util.Map;

public record Comments(Map<Object, Component> comments) implements Metadata {
	@Override
	public MetadataKey<Comments> key() {
		return MetadataKey.COMMENTS;
	}
}
