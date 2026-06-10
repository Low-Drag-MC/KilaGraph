package com.lowdragmc.kilagraph.rendertype.nodes.vertex;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.NodeDisplayNames;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.ContextNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

import java.util.List;

@NodeAttribute(name = "rt_vertex_stage", group = "rendertype_vertex", graphTypes = RenderTypeGraph.class)
public class VaryingStageNode extends ContextNode {
    @Override
    public Component getDisplayName() {
        return NodeDisplayNames.fromAttribute(this);
    }

    @Override
    public List<Class<? extends BlockNode>> getSupportBlocks() {
        return List.of(
                VertexPositionBlock.class,
                VaryingVertexColorBlock.class,
                VaryingSphericalVertexDistanceBlock.class,
                VaryingCylindricalVertexDistanceBlock.class,
                VaryingTexCoordBlock.class,
                VaryingCustomFloatBlock.class,
                VaryingCustomVec2Block.class,
                VaryingCustomVec3Block.class,
                VaryingCustomVec4Block.class
        );
    }
}
