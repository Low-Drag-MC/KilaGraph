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
        // KG_Fog is a slice-view of Minecraft's own Fog buffer (identical values, no #moj_import) — keeps
        // graphs reading fog parameters injectable under an Iris shaderpack. The values are VANILLA fog
        // parameters; a shaderpack still applies its own fog in composite regardless.
        ctx.output("FogColor", ctx.fogField("FogColor", GlslType.VEC4));
        ctx.output("FogEnvironmentalStart", ctx.fogField("FogEnvironmentalStart", GlslType.FLOAT));
        ctx.output("FogEnvironmentalEnd", ctx.fogField("FogEnvironmentalEnd", GlslType.FLOAT));
        ctx.output("FogRenderDistanceStart", ctx.fogField("FogRenderDistanceStart", GlslType.FLOAT));
        ctx.output("FogRenderDistanceEnd", ctx.fogField("FogRenderDistanceEnd", GlslType.FLOAT));
        ctx.output("FogSkyEnd", ctx.fogField("FogSkyEnd", GlslType.FLOAT));
        ctx.output("FogCloudsEnd", ctx.fogField("FogCloudsEnd", GlslType.FLOAT));
    }
}
