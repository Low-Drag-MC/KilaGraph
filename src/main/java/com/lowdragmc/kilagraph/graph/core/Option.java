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
 * {@code @ConfigNumber}/{@code @ConfigColor}/{@code @ConfigSelector}/... To supply an arbitrary
 * {@code ITypeConfigurable} — a type-handle picker, a member-key search box — declare the option in
 * {@code AnnotatedNode.onDefineExtraOptions} and call {@code withConfigurable} on the builder; see
 * {@link com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators} and {@code docs/CONVENTIONS.md} §1.</p>
 *
 * <p>An enum field renders as a dropdown over <em>all</em> of its constants automatically, via
 * LDLib2's {@code ITypeConfigurable.DEFAULT}. Note that path ignores the field, so
 * {@code @ConfigSelector(candidate = …)} does not narrow an option's choices.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option {
    /** Option id. Defaults to the annotated field's name. */
    String name() default "";

    String display() default "";
}
