package com.lowdragmc.kilagraph.rendertype.nodes.logic;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/** Unity's And node: {@code out = a && b} (boolean). */
@NodeAttribute(name = "rt_and", group = "rendertype_logic", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class AndNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", TypeHandles.BOOL);
        context.addInputPort("b", TypeHandles.BOOL);
        context.addOutputPort("out", TypeHandles.BOOL);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr("(" + ctx.input("a").code() + " && " + ctx.input("b").code() + ")", GlslType.BOOL));
    }
}
