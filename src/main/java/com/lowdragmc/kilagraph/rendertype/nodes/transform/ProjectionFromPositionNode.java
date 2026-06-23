package com.lowdragmc.kilagraph.rendertype.nodes.transform;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Projects a position into clip space ({@code ProjMat * pos}). {@link StageAffinity#VERTEX_ONLY}: clip
 * space is a per-vertex concept (it feeds {@code gl_Position}). An unconnected {@code position} falls back
 * to the model-space vertex position {@code vec4(Position + ModelOffset, 1.0)}.
 */
@NodeAttribute(name = "rt_projection_from_position", group = "rendertype_transform", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ProjectionFromPositionNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_projection_from_position.tooltip");
    }

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.VERTEX_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("position", RenderTypeGraphTypes.VEC4).withoutConfigurator();
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.include("minecraft:projection.glsl");
        String pos = ctx.isConnected("position") ? ctx.input("position").code()
                : "vec4(" + ctx.modelPosition().code() + ", 1.0)";
        ctx.output("out", new ShaderExpr("projection_from_position(" + pos + ")", GlslType.VEC4));
    }
}
