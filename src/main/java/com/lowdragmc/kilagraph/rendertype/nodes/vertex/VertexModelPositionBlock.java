package com.lowdragmc.kilagraph.rendertype.nodes.vertex;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.IVertexOutputBlock;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderBlockNode;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.VertexOutputs;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity-like vertex <b>Position</b> block: the connected value becomes the full model-space vertex
 * position, and the standard Minecraft transform ({@code ProjMat * ModelViewMat}) is applied after —
 * so wiring the Position input node straight in is the identity, exactly like Unity's master node.
 * Unconnected it contributes nothing: the emitted GLSL is byte-identical to a block-less graph.
 *
 * <p>Because the transform chain stays standard, a driven Position is injectable into an Iris
 * shaderpack's own vertex shader (vertex displacement works under shaderpacks) — unlike the advanced
 * glPosition block. Note the mesh position is whatever space the vertices were written in: the true
 * object space in previews, Minecraft's pose-baked (camera-relative world) space for in-world
 * geometry — offsets and normal-based displacement are safe in both; absolute rotations/scales about
 * the origin are not meaningful in-world.</p>
 */
@UseWithContext(VaryingStageNode.class)
@NodeAttribute(name = "rt_vertex_model_position", group = "rendertype_vertex", graphTypes = RenderTypeGraph.class)
public class VertexModelPositionBlock extends ShaderBlockNode implements IVertexOutputBlock {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_vertex_model_position.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("position", RenderTypeGraphTypes.VEC3).withoutConfigurator();
    }

    @Override
    public void emitVertex(ShaderCompileContext ctx, VertexOutputs out) {
        if (ctx.isConnected("position")) {
            out.position = ctx.input("position");
        }
        // Unconnected: contribute nothing — the mesh position flows through untouched (identity).
    }
}
