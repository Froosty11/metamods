package fixtures;

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

	public Optional<String> validate() {
		return title.equals("forbidden") ? Optional.of("That title is not allowed.") : Optional.empty();
	}

	public enum Mode implements StringRepresentable {
		FAST("fast"), SLOW("slow");
		private final String name;
		Mode(String name) { this.name = name; }
		@Override public String getSerializedName() { return name; }
	}
}
