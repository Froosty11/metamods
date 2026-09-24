package nu.metacraft.lib.config.extensions;

/**
 * @deprecated Use records and {@link nu.metacraft.lib.config.container.ConfigContainer#update}.
 */
@Deprecated
public interface Modifiable {

	void setModified(boolean modified);
	boolean isModified();

}
