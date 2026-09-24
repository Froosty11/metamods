package fixtures;

import nu.metacraft.lib.config.describe.Config;
import nu.metacraft.lib.config.describe.Option;

@Config(name = "Store", description = "Where things are kept.")
public record SampleSection(
		@Option(description = "Address of the store.") String url,
		@Option(description = "Seconds between syncs.", min = 1, max = 60) int interval
) {
	public static final SampleSection DEFAULT = new SampleSection("", 10);
}
