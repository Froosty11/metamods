package nu.metacraft.lib.config.describe;

/** What an option is edited as. */
public enum OptionKind {
	BOOLEAN, WHOLE, DECIMAL, TEXT, IDENTIFIER, CHOICE, TEXT_LIST, IDENTIFIER_LIST,
	/** A nested {@link Config} record: its own page. */
	SECTION,
	/** A type the screen cannot edit; shown with "edit in the file". */
	READ_ONLY
}
