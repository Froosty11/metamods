package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "Unannotated")
public record Unannotated(@Option(description = "x") int x, int y) {
	public static final Unannotated DEFAULT = new Unannotated(1, 2);
}
