package com.lowdragmc.kilagraph.graph.core;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortCapacity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a data input port on a plain Java field. The field's type maps to a
 * {@code TypeHandle}; the field's initialiser is the embedded-constant default value.
 *
 * <p>Combine with LDLib2's {@code @Configurable} + {@code @ConfigNumber}/etc. or with
 * {@link CustomConfigurable} / {@link PortType} for advanced cases.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InputPort {
    /** Port id. Defaults to the annotated field's name. */
    String name() default "";

    String display() default "";

    PortCapacity capacity() default PortCapacity.SINGLE;
}
