package com.lowdragmc.kilagraph.rendertype.nodes.input.basic;

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

@NodeAttribute(name = "rt_vec4", group = "rendertype_input/basic", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class Vec4Node extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("x", TypeHandles.FLOAT);
        context.addInputPort("y", TypeHandles.FLOAT);
        context.addInputPort("z", TypeHandles.FLOAT);
        context.addInputPort("w", TypeHandles.FLOAT);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String code = "vec4(" + ctx.input("x").code() + ", " + ctx.input("y").code() + ", "
                + ctx.input("z").code() + ", " + ctx.input("w").code() + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC4));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
