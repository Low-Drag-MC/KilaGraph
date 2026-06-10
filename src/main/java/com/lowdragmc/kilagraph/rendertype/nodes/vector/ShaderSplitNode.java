package com.lowdragmc.kilagraph.rendertype.nodes.vector;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "rt_split", group = "rendertype_vector", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ShaderSplitNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("r", TypeHandles.FLOAT);
        context.addOutputPort("g", TypeHandles.FLOAT);
        context.addOutputPort("b", TypeHandles.FLOAT);
        context.addOutputPort("a", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String in = ctx.input("in").code();
        ctx.output("r", new ShaderExpr("(" + in + ").x", GlslType.FLOAT));
        ctx.output("g", new ShaderExpr("(" + in + ").y", GlslType.FLOAT));
        ctx.output("b", new ShaderExpr("(" + in + ").z", GlslType.FLOAT));
        ctx.output("a", new ShaderExpr("(" + in + ").w", GlslType.FLOAT));
    }
}
