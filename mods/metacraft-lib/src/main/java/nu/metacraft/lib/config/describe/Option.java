package nu.metacraft.lib.config.describe;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** One option of a {@link Config} record: every component of a described record has one. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Option {
	/** Shown as the option's label. */
	String description();
	/** The range an edit must stay in; the config's codec enforces its own ranges on load. */
	double min() default Double.NEGATIVE_INFINITY;
	double max() default Double.POSITIVE_INFINITY;
	/** A slider's step. 0: 1 for whole numbers; a decimal without a step is always typed. */
	double step() default 0;
	/** The option only takes effect after a restart; the screen marks it and lists pending changes. */
	boolean restart() default false;
	/** The key in the file; empty means the component's name in snake_case. */
	String key() default "";
}
