package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

/**
 * Shared display-name resolution for RenderType nodes: a node's {@link NodeAttribute#name()} used as a
 * translate key (so the i18n in {@code lang/} drives the label), falling back to the class simple name
 * if a node somehow has no {@code @NodeAttribute}. Used by {@code ShaderNode}, {@code ShaderBlockNode},
 * and the stage context nodes — three unrelated hierarchies that all want the same behavior.
 */
public final class NodeDisplayNames {
    private NodeDisplayNames() {}

    public static Component fromAttribute(Object node) {
        var attribute = node.getClass().getAnnotation(NodeAttribute.class);
        return attribute != null
                ? Component.translatable(attribute.name())
                : Component.literal(node.getClass().getSimpleName());
    }
}
