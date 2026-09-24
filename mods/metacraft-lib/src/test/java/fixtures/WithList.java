package fixtures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

import java.util.Map;

@Config(name = "With map")
public record WithList(@Option(description = "A map.") Map<String, Integer> weights) {
	public static final WithList DEFAULT = new WithList(Map.of());

	public static final MapCodec<WithList> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("weights").forGetter(WithList::weights)
	).apply(i, WithList::new));
}
