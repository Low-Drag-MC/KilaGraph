package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;

import java.util.List;
import java.util.function.Function;

/**
 * A generic editor dropdown for a node's STRING option constrained to a fixed list of choices — a
 * {@link SelectorConfigurator} over the given values. Client-only and referenced lazily from a node's
 * option {@code withConfigurable} lambda so the headless compiler never loads it (mirrors
 * {@link VertexAttributeConfigurator}). Used e.g. by the Transform node's space/type pickers and the
 * Exponential/Log base pickers.
 */
public final class ChoiceConfigurator {
    private ChoiceConfigurator() {}

    /** Dropdown showing each choice value verbatim. */
    public static IConfigurable build(IFieldValueConfigurable vc, List<String> choices) {
        return build(vc, choices, s -> s);
    }

    /** Dropdown whose displayed text comes from {@code label} (the stored value stays the raw choice). */
    public static IConfigurable build(IFieldValueConfigurable vc, List<String> choices, Function<String, String> label) {
        String def = choices.isEmpty() ? "" : choices.getFirst();
        return IConfigurable.create(group -> group.addConfigurator(new SelectorConfigurator<>(
                "",
                () -> vc.getValue() instanceof String s ? s : def,
                vc::setValue,
                def, vc.forceUpdate(),
                choices,
                label)));
    }
}
