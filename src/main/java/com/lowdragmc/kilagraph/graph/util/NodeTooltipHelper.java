package com.lowdragmc.kilagraph.graph.util;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.network.chat.Component;

public final class NodeTooltipHelper {
    private NodeTooltipHelper() {}

    public static void apply(NodeModel nodeModel, Component tooltip) {
        if (tooltip != null) {
            nodeModel.setTooltip(tooltip);
        }
    }

}
