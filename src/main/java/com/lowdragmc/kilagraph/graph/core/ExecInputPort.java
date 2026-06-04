package com.lowdragmc.kilagraph.graph.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as an execution-flow input port (uses {@code TypeHandles.EXECUTION_FLOW}).
 *
 * <p>Phase-1 {@code GraphExecutor} does not consume execution edges; nodes carrying exec ports may
 * still be defined but their {@code execute(...)} hook is unused for now.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExecInputPort {
    String name() default "";

    String display() default "";
}
