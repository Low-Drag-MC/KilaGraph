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
 * The GLSL built-in {@code gl_InstanceID} — the index of the current instance within an instanced draw
 * (an {@code int}; {@code 0} for non-instanced draws). Stage-agnostic (default
 * {@link com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity#ANY}): the built-in itself only exists in
 * the vsh, so the vertex stage reads it directly while the fragment stage reads it through an auto-forwarded
 * {@code flat int} varying ({@code kg_instanceId}).
 *
 * <p>The per-node preview has no real vertex stage, so it substitutes a constant {@code 0} instead of
 * emitting the undefined built-in.</p>
 */
@NodeAttribute(name = "rt_instance_id", group = "rendertype_input/vertex", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class InstanceIdNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_instance_id.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", TypeHandles.INT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // vsh: gl_InstanceID directly. fsh: a `flat in int kg_instanceId` fed by the vsh. preview: constant 0.
        ctx.output("out", ctx.varyingInput("kg_instanceId", GlslType.INT,
                () -> new ShaderExpr("gl_InstanceID", GlslType.INT),
                new ShaderExpr("0", GlslType.INT)));
    }

    @Override
    public String glslExample() {
        return """
                // vertex shader
                out = gl_InstanceID;

                // fragment shader (auto-forwarded)
                flat in int kg_instanceId;""";
    }
}
