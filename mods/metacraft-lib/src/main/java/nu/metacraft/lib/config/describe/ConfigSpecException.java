package nu.metacraft.lib.config.describe;

/** A described config that cannot be used as described; thrown at registration, naming the component. */
public class ConfigSpecException extends RuntimeException {
	public ConfigSpecException(String message) {
		super(message);
	}
}
