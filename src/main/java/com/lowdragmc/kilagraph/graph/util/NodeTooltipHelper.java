package com.lowdragmc.kilagraph.graph.util;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class NodeTooltipHelper {
    private NodeTooltipHelper() {}

    public static void apply(NodeModel nodeModel, Component tooltip) {
        if (tooltip != null) {
            nodeModel.setTooltip(tooltip);
        }
    }

    /**
     * The conventional hover tooltip for a node: {@code kg.node.<name>.tooltip}, the same key the
     * description panel uses as its summary line. Returns {@code null} when the lang file has no entry, so
     * an undocumented node simply has no tooltip.
     *
     * @param node any node carrying a {@link NodeAttribute}
     */
    @Nullable
    public static Component defaultTooltip(Object node) {
        var attribute = node.getClass().getAnnotation(NodeAttribute.class);
        if (attribute == null) return null;
        String key = "kg.node." + attribute.name() + ".tooltip";
        return LocalizationUtils.exist(key) ? Component.translatable(key) : null;
    }
}
