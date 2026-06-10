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

@NodeAttribute(name = "rt_projection_from_position", group = "rendertype_transform", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ProjectionFromPositionNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("position", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.include("minecraft:projection.glsl");
        ctx.output("out", new ShaderExpr("projection_from_position(" + ctx.input("position").code() + ")", GlslType.VEC4));
    }
}
