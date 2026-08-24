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
 * <p>Combine with LDLib2's {@code @Configurable} + {@code @ConfigNumber}/etc. for field-level UI
 * hints. Anything an annotation cannot express — a port whose {@code TypeHandle} follows an option, a
 * search picker, a port with no inspector row — is not another annotation: declare it imperatively in
 * {@code AnnotatedNode.onDefineDynamicPorts} instead, which is where {@code withConfigurable} and a
 * computed handle are reachable. See {@code docs/CONVENTIONS.md} §1.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InputPort {
    /** Port id. Defaults to the annotated field's name. */
    String name() default "";

    String display() default "";

    PortCapacity capacity() default PortCapacity.SINGLE;
}
