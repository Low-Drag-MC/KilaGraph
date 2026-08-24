package com.lowdragmc.kilagraph.blueprint.nodes.ui.element;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.InfoContextNode;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

/**
 * Holds one {@link UIElement} for the property blocks in {@link UIElementInfoBlocks} to read.
 *
 * <p>A {@code UIElement} is exactly the kind of thing this shape exists for: a live object with a
 * dozen properties a graph might want, where wiring the same element into a dozen separate nodes
 * would be the bulk of the graph. Drop one of these, wire the element once, and stack whichever
 * property blocks you need inside it.</p>
 *
 * <p>The geometry blocks are the reason it is worth having at all. Layout numbers only exist once a
 * client {@code ModularUI} has laid the tree out, so they are read at the moment the graph asks —
 * during a hover handler, during a tick — and every one of them wants the same element.</p>
 */
@NodeAttribute(name = "ldlib2_ui_element_info", group = "ui/element", graphTypes = BlueprintGraph.class)
public class UIElementInfoNode extends InfoContextNode<UIElement> {

    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.ldlib2_ui_element_info.tooltip");
    }

    @Override
    protected Class<UIElement> targetClass() {
        return UIElement.class;
    }
}
