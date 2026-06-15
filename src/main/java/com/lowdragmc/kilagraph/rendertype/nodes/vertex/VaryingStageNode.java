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
        // Only Position + the generic Custom varying blocks. The old specialized blocks (uv0 / vertex
        // colour / fog distances) are gone — their values come from compiler defaults (meshUv /
        // litVertexColor / sphericalVertexDistance / cylindricalVertexDistance), and a user who wants to
        // override a varying adds a Custom block.
        return List.of(
                VertexPositionBlock.class,
                VaryingCustomFloatBlock.class,
                VaryingCustomVec2Block.class,
                VaryingCustomVec3Block.class,
                VaryingCustomVec4Block.class
        );
    }
}
