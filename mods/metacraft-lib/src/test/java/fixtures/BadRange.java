package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "Bad range")
public record BadRange(@Option(description = "x", min = 5, max = 1) int x) {
	public static final BadRange DEFAULT = new BadRange(3);
}
