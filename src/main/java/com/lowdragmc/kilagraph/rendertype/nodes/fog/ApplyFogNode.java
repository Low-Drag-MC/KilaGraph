package com.lowdragmc.kilagraph.rendertype.nodes.fog;

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

@NodeAttribute(name = "rt_apply_fog", group = "rendertype_fog", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ApplyFogNode extends ShaderNode {

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("inColor", RenderTypeGraphTypes.VEC4);
        context.addInputPort("sphericalVertexDistance", TypeHandles.FLOAT);
        context.addInputPort("cylindricalVertexDistance", TypeHandles.FLOAT);
        context.addInputPort("environmentalStart", TypeHandles.FLOAT);
        context.addInputPort("environmentalEnd", TypeHandles.FLOAT);
        context.addInputPort("renderDistanceStart", TypeHandles.FLOAT);
        context.addInputPort("renderDistanceEnd", TypeHandles.FLOAT);
        context.addInputPort("fogColor", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.include("minecraft:fog.glsl");
        String code = "apply_fog("
                + ctx.input("inColor").code() + ", "
                + ctx.input("sphericalVertexDistance").code() + ", "
                + ctx.input("cylindricalVertexDistance").code() + ", "
                + ctx.input("environmentalStart").code() + ", "
                + ctx.input("environmentalEnd").code() + ", "
                + ctx.input("renderDistanceStart").code() + ", "
                + ctx.input("renderDistanceEnd").code() + ", "
                + ctx.input("fogColor").code() + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC4));
    }
}
