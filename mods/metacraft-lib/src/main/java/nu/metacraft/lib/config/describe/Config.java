package nu.metacraft.lib.config.describe;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a config record as described: its components carry {@link Option}, and it declares
 * {@code public static final <Type> DEFAULT}. See {@link ConfigSpec}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config {
	/** Shown as the config's title. */
	String name();
	String description() default "";
}
