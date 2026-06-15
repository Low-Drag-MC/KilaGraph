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

@NodeAttribute(name = "rt_fog_ubo", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FogUboNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("FogColor", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("FogEnvironmentalStart", TypeHandles.FLOAT);
        context.addOutputPort("FogEnvironmentalEnd", TypeHandles.FLOAT);
        context.addOutputPort("FogRenderDistanceStart", TypeHandles.FLOAT);
        context.addOutputPort("FogRenderDistanceEnd", TypeHandles.FLOAT);
        context.addOutputPort("FogSkyEnd", TypeHandles.FLOAT);
        context.addOutputPort("FogCloudsEnd", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.useMinecraftUniform("Fog", "minecraft:fog.glsl");
        ctx.output("FogColor", new ShaderExpr("FogColor", GlslType.VEC4));
        ctx.output("FogEnvironmentalStart", new ShaderExpr("FogEnvironmentalStart", GlslType.FLOAT));
        ctx.output("FogEnvironmentalEnd", new ShaderExpr("FogEnvironmentalEnd", GlslType.FLOAT));
        ctx.output("FogRenderDistanceStart", new ShaderExpr("FogRenderDistanceStart", GlslType.FLOAT));
        ctx.output("FogRenderDistanceEnd", new ShaderExpr("FogRenderDistanceEnd", GlslType.FLOAT));
        ctx.output("FogSkyEnd", new ShaderExpr("FogSkyEnd", GlslType.FLOAT));
        ctx.output("FogCloudsEnd", new ShaderExpr("FogCloudsEnd", GlslType.FLOAT));
    }
}
