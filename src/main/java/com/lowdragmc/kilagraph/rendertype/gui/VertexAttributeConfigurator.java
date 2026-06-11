package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.format.KGVertexElement;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElements;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;

import java.util.List;

/**
 * The editor dropdown for the {@code VertexAttributeInputNode}'s element option: a {@link SelectorConfigurator}
 * over every registered {@link KGVertexElement} (labelled by its attribute name). Client-only and referenced
 * lazily from the node's option {@code withConfigurable} lambda, so the headless compiler never loads it
 * (mirrors {@link Sampler2DConfigurator}).
 */
public final class VertexAttributeConfigurator {
    private VertexAttributeConfigurator() {}

    public static IConfigurable build(IFieldValueConfigurable vc) {
        List<String> keys = KGVertexElements.all().stream().map(KGVertexElement::key).toList();
        String def = keys.isEmpty() ? "position" : keys.getFirst();
        return IConfigurable.create(group -> group.addConfigurator(new SelectorConfigurator<>(
                "",
                () -> vc.getValue() instanceof String s ? s : def,
                vc::setValue,
                def, vc.forceUpdate(),
                keys,
                VertexAttributeConfigurator::label)));
    }

    private static String label(String key) {
        KGVertexElement element = KGVertexElements.get(key);
        return element == null ? key : element.attribName();
    }
}
