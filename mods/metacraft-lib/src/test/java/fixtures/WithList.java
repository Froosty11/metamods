package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

import java.util.List;
import java.util.Map;

@Config(name = "With map")
public record WithList(@Option(description = "A map.") Map<String, Integer> weights) {
	public static final WithList DEFAULT = new WithList(Map.of());
}
