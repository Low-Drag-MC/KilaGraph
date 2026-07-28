package com.lowdragmc.kilagraph.graph.util;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The per-node hooks {@link NodeDescriptions} reads when it assembles a node's entry in the item
 * library's description panel. Implemented by every KilaGraph node hierarchy — the shader graph's
 * {@code ShaderNode}/{@code ShaderBlockNode}/stage contexts and the blueprint's {@code AnnotatedNode}.
 *
 * <p>Everything else about a description lives in the lang files under {@code kg.node.<name>.*}; these
 * hooks only cover what cannot be translated (source code) or cannot be discovered from the node model
 * (the value list behind a dropdown option).</p>
 */
public interface INodeDescription {

    /**
     * Source code this node stands for, shown as a syntax-highlighted card. Keep lines short (~48 chars);
     * the panel is narrow. A node whose output is a single expression should derive this from the same
     * method that produces the real code, so the example can never drift.
     *
     * @return the snippet, or {@code null} for no code section
     */
    @Nullable
    default String codeExample() {
        return null;
    }

    /**
     * The selectable values of a dropdown option, in menu order, so the description can list what each
     * choice does from {@code kg.node.<name>.option.<optionId>.tooltip.<value>}. Nodes that build a
     * choice configurator already hold this list — return it here.
     *
     * @param optionId the option being described
     * @return the value ids, or an empty list to document the option as a single line
     */
    default List<String> optionChoices(String optionId) {
        return List.of();
    }

    /** Appends custom UI after the generated sections — a diagram, a swatch, anything the panel needs. */
    default void appendDescription(NodeDescriptionUI ui) {}
}
