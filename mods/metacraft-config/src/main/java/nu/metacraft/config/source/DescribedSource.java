package nu.metacraft.config.source;

import com.mojang.serialization.DataResult;
import nu.metacraft.lib.config.describe.*;

import java.util.*;

/** A config described in metacraft-lib, as the screen sees it. */
public record DescribedSource(DescribedConfig<?> config) implements ConfigSource {

	@Override public String id() { return config.id(); }
	@Override public String name() { return config.spec().name(); }
	@Override public String description() { return config.spec().description(); }
	@Override public Optional<String> loadError() { return config.loadError(); }
	@Override public List<String> pendingRestart() { return config.pendingRestart(); }
	@Override public int hash() { return config.hash(); }

	@Override
	public Page page(List<String> path) {
		ConfigSpec<?> spec = ConfigEdits.pageSpec(config.spec(), path);
		Record value = config.valueOn(path);
		List<Field> fields = new ArrayList<>();
		List<Link> sections = new ArrayList<>();
		for (OptionSpec option : spec.options()) {
			if (option.kind() == OptionKind.SECTION) {
				sections.add(new Link(option.key(), option.description()));
				continue;
			}
			String range = option.rangeText();
			String label = range.isEmpty() ? option.description() : option.description() + " " + range;
			fields.add(new Field(option.key(), label, option.kind(), ConfigEdits.text(option, option.read(value)),
					option.min(), option.max(), option.effectiveStep(), option.slider(), option.choices(),
					option.restart(), option.editable()));
		}
		return new Page(List.copyOf(path), spec.name(), fields, sections);
	}

	@Override
	public EditOutcome apply(List<String> path, Map<String, String> values, int expectedHash) {
		return outcome(config.apply(path, values, expectedHash));
	}

	@Override
	public EditOutcome reset(List<String> path, int expectedHash) {
		return outcome(config.reset(path, expectedHash));
	}

	private static EditOutcome outcome(DataResult<?> result) {
		if (result.error().isEmpty()) return new EditOutcome.Saved();
		String message = result.error().get().message();
		return message.equals(DescribedConfig.STALE) ? new EditOutcome.Stale() : new EditOutcome.Refused(message);
	}
}
