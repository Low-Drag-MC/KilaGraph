package com.lowdragmc.kilagraph.rendertype.nodes.vertex;

import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.IShaderNodeDescription;
import com.lowdragmc.kilagraph.rendertype.compiler.NodeDisplayNames;
import com.lowdragmc.kilagraph.graph.util.NodeDescriptions;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.ContextNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NodeAttribute(name = "rt_vertex_stage", group = "rendertype_vertex", graphTypes = RenderTypeGraph.class)
public class VaryingStageNode extends ContextNode implements IShaderNodeDescription {
    /**
     * Vertex-stage blocks that are single-instance: the vertex position is driven by exactly one block
     * (the object-space {@link VertexModelPositionBlock} <em>or</em> the advanced clip-space
     * {@link VertexPositionBlock}), and the normal by at most one {@link VertexModelNormalBlock}. Each is
     * dropped from the Add-Block menu once one is present (see {@link #getSupportBlocks()}), so it can be
     * added only once. (The mutually-exclusive glPosition + Position case is a graph-validation error —
     * see {@code RenderTypeGraph.validateGraph}.)
     */
    private static final Set<Class<? extends BlockNode>> UNIQUE_BLOCKS = Set.of(
            VertexPositionBlock.class, VertexModelPositionBlock.class, VertexModelNormalBlock.class);

    @Override
    public void setImplementation(NodeModel nodeModel) {
        super.setImplementation(nodeModel);
        NodeTooltipHelper.apply(nodeModel, getNodeTooltip());
    }

    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_vertex_stage.tooltip");
    }

    /**
     * The vertex-stage blocks offered in the Add-Block menu. Discovery stays fully automatic — the base
     * {@link ContextNode#getSupportBlocks()} scans every block annotated {@code @UseWithContext(VaryingStageNode)}
     * — this override only <b>drops a single-instance block</b> ({@link #UNIQUE_BLOCKS}) once one is already
     * present in the stage, so glPosition / Position / Normal can each be added just once; the Custom
     * varyings are unlimited. A detached node (no backing model) can't inspect its blocks, so it returns
     * the discovered list unfiltered.
     */
    @Override
    public List<Class<? extends BlockNode>> getSupportBlocks() {
        var supported = super.getSupportBlocks();
        var model = getContextNodeModel();
        if (model == null) return supported;
        Set<Class<?>> present = new HashSet<>();
        for (var block : model.getBlocks()) {
            if (block instanceof ICustomNodeModel custom && custom.getNode() != null) {
                present.add(custom.getNode().getClass());
            }
        }
        return supported.stream()
                .filter(cls -> !UNIQUE_BLOCKS.contains(cls) || !present.contains(cls))
                .toList();
    }

    @Override
    public Component getDisplayName() {
        return NodeDisplayNames.fromAttribute(this);
    }

    @Override
    @Nullable
    public UIElement createDescriptionUI() {
        return NodeDescriptions.build(this);
    }
}
