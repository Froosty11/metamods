package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "No default")
public record NoDefault(@Option(description = "x") int x) {}
