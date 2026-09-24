package fixtures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

import java.util.List;
import java.util.Optional;

@Config(name = "Sample", description = "A config for tests.")
public record Sample(
		@Option(description = "Turned on.") boolean enabled,
		@Option(description = "How many.", min = 1, max = 16) int count,
		@Option(description = "How fast.", min = 0) double speed,
		@Option(description = "Share, in steps of 0.05.", min = 0, max = 1, step = 0.05) double share,
		@Option(description = "Optional limit.", min = 0) Optional<Double> limit,
		@Option(description = "A name.") String title,
		@Option(description = "A block.") Identifier block,
		@Option(description = "Mode.", restart = true) Mode mode,
		@Option(description = "Words, one per line.") List<String> words,
		@Option(description = "The store.") SampleSection store,
		@Option(description = "Renamed in the file.", key = "old-name") int renamed
) {
	public static final Sample DEFAULT = new Sample(true, 6, 60, 0.5, Optional.empty(), "hi",
			Identifier.withDefaultNamespace("stone"), Mode.FAST, List.of("a"), SampleSection.DEFAULT, 3);

	public static final MapCodec<Sample> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.BOOL.fieldOf("enabled").forGetter(Sample::enabled),
			Codec.intRange(1, 16).fieldOf("count").forGetter(Sample::count),
			Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("speed").forGetter(Sample::speed),
			Codec.doubleRange(0, 1).fieldOf("share").forGetter(Sample::share),
			Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("limit").forGetter(Sample::limit),
			Codec.STRING.fieldOf("title").forGetter(Sample::title),
			Identifier.CODEC.fieldOf("block").forGetter(Sample::block),
			Mode.CODEC.fieldOf("mode").forGetter(Sample::mode),
			Codec.STRING.listOf().fieldOf("words").forGetter(Sample::words),
			SampleSection.CODEC.codec().fieldOf("store").forGetter(Sample::store),
			Codec.INT.fieldOf("old-name").forGetter(Sample::renamed)
	).apply(i, Sample::new));

	public Optional<String> validate() {
		return title.equals("forbidden") ? Optional.of("That title is not allowed.") : Optional.empty();
	}

	public enum Mode implements StringRepresentable {
		FAST("fast"), SLOW("slow");
		public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
		private final String name;
		Mode(String name) { this.name = name; }
		@Override public String getSerializedName() { return name; }
	}
}
