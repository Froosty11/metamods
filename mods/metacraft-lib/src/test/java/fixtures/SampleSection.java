package fixtures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "Store", description = "Where things are kept.")
public record SampleSection(
		@Option(description = "Address of the store.") String url,
		@Option(description = "Seconds between syncs.", min = 1, max = 60) int interval
) {
	public static final SampleSection DEFAULT = new SampleSection("", 10);

	public static final MapCodec<SampleSection> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.fieldOf("url").forGetter(SampleSection::url),
			Codec.intRange(1, 60).fieldOf("interval").forGetter(SampleSection::interval)
	).apply(i, SampleSection::new));
}
