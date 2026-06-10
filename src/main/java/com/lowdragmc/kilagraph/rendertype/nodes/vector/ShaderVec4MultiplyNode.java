package com.lowdragmc.kilagraph.rendertype.nodes.vector;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "rt_vec4_multiply", group = "rendertype_vector", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ShaderVec4MultiplyNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("a", RenderTypeGraphTypes.VEC4);
        context.addInputPort("b", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr("(" + ctx.input("a").code() + ") * (" + ctx.input("b").code() + ")", GlslType.VEC4));
    }
}
