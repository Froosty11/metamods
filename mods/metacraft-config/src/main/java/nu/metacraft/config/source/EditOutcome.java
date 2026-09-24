package nu.metacraft.config.source;

/** What a save or reset did. */
public sealed interface EditOutcome {
	record Saved() implements EditOutcome {
	}

	record Refused(String message) implements EditOutcome {
	}

	record Stale() implements EditOutcome {
	}
}
