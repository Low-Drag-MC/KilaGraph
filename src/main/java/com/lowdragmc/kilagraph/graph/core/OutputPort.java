package com.lowdragmc.kilagraph.graph.core;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortCapacity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an output port on a plain Java field. The field's type determines the port's
 * {@code TypeHandle}; the field's initialiser is ignored at runtime — outputs have no
 * embedded constant.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OutputPort {
    String name() default "";

    String display() default "";

    PortCapacity capacity() default PortCapacity.MULTIPLE;
}
