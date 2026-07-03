package com.lowdragmc.kilagraph.rendertype.nodes.fog;

import net.minecraft.network.chat.Component;
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
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_fog_ubo.tooltip");
    }

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
        ctx.include("minecraft:fog.glsl");
        // 1.21.1 fog is a single band: FogStart/FogEnd/FogColor (individual uniforms, set by setDefaultUniforms).
        // The 1.21.5 multi-band outputs are kept as ports (graph compat) but all map onto that single band.
        ShaderExpr fogStart = ctx.fogField("FogStart", GlslType.FLOAT);
        ShaderExpr fogEnd = ctx.fogField("FogEnd", GlslType.FLOAT);
        ctx.output("FogColor", ctx.fogField("FogColor", GlslType.VEC4));
        ctx.output("FogEnvironmentalStart", fogStart);
        ctx.output("FogEnvironmentalEnd", fogEnd);
        ctx.output("FogRenderDistanceStart", fogStart);
        ctx.output("FogRenderDistanceEnd", fogEnd);
        ctx.output("FogSkyEnd", fogEnd);
        ctx.output("FogCloudsEnd", fogEnd);
    }
}
