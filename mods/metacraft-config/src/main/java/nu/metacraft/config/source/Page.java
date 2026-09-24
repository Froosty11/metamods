package nu.metacraft.config.source;

import java.util.List;

/** One page of a source: its editable fields and the buttons to its sections. {@code path} is the section keys from the top. */
public record Page(List<String> path, String title, List<Field> fields, List<Link> sections) {
}
