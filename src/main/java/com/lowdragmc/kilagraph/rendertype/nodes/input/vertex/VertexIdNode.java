package com.lowdragmc.kilagraph.rendertype.nodes.input.vertex;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * The GLSL built-in {@code gl_VertexID} — the index of the current vertex within the draw call (an
 * {@code int}). Stage-agnostic (default {@link com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity#ANY}):
 * the built-in itself only exists in the vsh, so the vertex stage reads it directly while the fragment stage
 * reads it through an auto-forwarded {@code flat int} varying ({@code kg_vertexId}).
 *
 * <p>The per-node preview has no real vertex stage (the thumbnail draws a flat quad through the preview vsh),
 * so it substitutes a constant {@code 0} instead of emitting the undefined built-in.</p>
 */
@NodeAttribute(name = "rt_vertex_id", group = "rendertype_input/vertex", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class VertexIdNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_vertex_id.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", TypeHandles.INT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // vsh: gl_VertexID directly. fsh: a `flat in int kg_vertexId` fed by the vsh. preview: constant 0.
        ctx.output("out", ctx.varyingInput("kg_vertexId", GlslType.INT,
                () -> new ShaderExpr("gl_VertexID", GlslType.INT),
                new ShaderExpr("0", GlslType.INT)));
    }
}
