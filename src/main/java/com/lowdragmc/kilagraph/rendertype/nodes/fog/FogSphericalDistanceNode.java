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

@NodeAttribute(name = "rt_fog_spherical_distance", group = "rendertype_fog", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FogSphericalDistanceNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("pos", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.include("minecraft:fog.glsl");
        ctx.output("out", new ShaderExpr("fog_spherical_distance(" + ctx.input("pos").code() + ")", GlslType.FLOAT));
    }
}
