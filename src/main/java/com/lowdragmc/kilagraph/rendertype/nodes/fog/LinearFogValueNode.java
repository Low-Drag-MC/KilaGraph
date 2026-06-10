package com.lowdragmc.kilagraph.rendertype.nodes.fog;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "rt_linear_fog_value", group = "rendertype_fog", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class LinearFogValueNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("vertexDistance", TypeHandles.FLOAT);
        context.addInputPort("fogStart", TypeHandles.FLOAT);
        context.addInputPort("fogEnd", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.include("minecraft:fog.glsl");
        String code = "linear_fog_value(" + ctx.input("vertexDistance").code() + ", "
                + ctx.input("fogStart").code() + ", " + ctx.input("fogEnd").code() + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }
}
