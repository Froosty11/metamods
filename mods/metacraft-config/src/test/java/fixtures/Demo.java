package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

import java.util.Optional;

@Config(name = "Demo", description = "For tests.")
public record Demo(
		@Option(description = "On.") boolean on,
		@Option(description = "Stitches.", min = 1, max = 16) int stitches,
		@Option(description = "Speed.", min = 0) double speed,
		@Option(description = "Cap.", min = 0) Optional<Double> cap,
		@Option(description = "Needs a restart.", restart = true) boolean heavy,
		@Option(description = "Inner.") Inner inner
) {
	public static final Demo DEFAULT = new Demo(true, 6, 2.5, Optional.empty(), false, Inner.DEFAULT);

	@Config(name = "Inner")
	public record Inner(@Option(description = "Name.") String name) {
		public static final Inner DEFAULT = new Inner("x");
	}
}
