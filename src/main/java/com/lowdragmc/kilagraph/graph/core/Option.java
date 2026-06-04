package com.lowdragmc.kilagraph.graph.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a node option on a plain Java field. The field's type maps to a {@code TypeHandle}
 * via {@link com.lowdragmc.kilagraph.graph.type.KGTypeHandles#handleFor}; the field's
 * initialiser is the option's default value.
 *
 * <p>UI is delegated to LDLib2: combine with {@code @Configurable} +
 * {@code @ConfigNumber}/{@code @ConfigColor}/{@code @ConfigSelector}/... or with
 * {@link CustomConfigurable} to supply an arbitrary {@code ITypeConfigurable}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option {
    /** Option id. Defaults to the annotated field's name. */
    String name() default "";

    String display() default "";
}
