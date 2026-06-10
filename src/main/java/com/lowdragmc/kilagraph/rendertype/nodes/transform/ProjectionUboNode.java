package com.lowdragmc.kilagraph.rendertype.nodes.transform;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "rt_projection_ubo", group = "rendertype_transform", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ProjectionUboNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("ProjMat", RenderTypeGraphTypes.MAT4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.useMinecraftUniform("Projection", "minecraft:projection.glsl");
        ctx.output("ProjMat", new ShaderExpr("ProjMat", GlslType.MAT4));
    }
}
