package com.lowdragmc.kilagraph.graph.util;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.TextAreaConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.ITypeConfigurable;

/**
 * {@link ITypeConfigurable} factories for KilaGraph options whose value is text but whose editor
 * shouldn't be the default one-line {@code TextField}.
 */
public final class KGTextConfigurators {

    private KGTextConfigurators() {}

    /**
     * A multi-line {@code TextArea} editor over a plain {@code STRING} option: lines are joined with
     * {@code \n} on the way in and split back out on the way to the widget, so the stored value stays
     * an ordinary string that every other string node can consume.
     *
     * <p>Referenced from a {@code withConfigurable} lambda, which is what keeps
     * {@link TextAreaConfigurator} (a client-side UI class) off the headless load path.</p>
     *
     * @param defaultValue the option's default, shown when its value is missing
     */
    public static ITypeConfigurable multiLineText(String defaultValue) {
        return (vc, typeHandle) -> IConfigurable.create(group -> group.addConfigurator(
                new TextAreaConfigurator("",
                        () -> lines(readString(vc, defaultValue)),
                        edited -> vc.setValue(String.join("\n", edited)),
                        lines(defaultValue),
                        vc.forceUpdate())));
    }

    /**
     * Reads the option through {@code Object} + {@code toString()} rather than the generic
     * {@code getValue()} overload, which infers whatever the call site asks for and would
     * {@code ClassCastException} on a value that isn't already a String.
     */
    private static String readString(IFieldValueConfigurable vc, String fallback) {
        Object value = vc.getValue();
        return value == null ? fallback : value.toString();
    }

    /** {@code -1} keeps trailing empties, so a text ending in a newline round-trips its blank last line. */
    private static String[] lines(String text) {
        return text.split("\n", -1);
    }
}
