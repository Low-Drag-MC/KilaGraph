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

@NodeAttribute(name = "rt_total_fog_value", group = "rendertype_fog", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TotalFogValueNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("sphericalVertexDistance", TypeHandles.FLOAT);
        context.addInputPort("cylindricalVertexDistance", TypeHandles.FLOAT);
        context.addInputPort("environmentalStart", TypeHandles.FLOAT);
        context.addInputPort("environmentalEnd", TypeHandles.FLOAT);
        context.addInputPort("renderDistanceStart", TypeHandles.FLOAT);
        context.addInputPort("renderDistanceEnd", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.include("minecraft:fog.glsl");
        String code = "total_fog_value("
                + ctx.input("sphericalVertexDistance").code() + ", "
                + ctx.input("cylindricalVertexDistance").code() + ", "
                + ctx.input("environmentalStart").code() + ", "
                + ctx.input("environmentalEnd").code() + ", "
                + ctx.input("renderDistanceStart").code() + ", "
                + ctx.input("renderDistanceEnd").code() + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }
}
