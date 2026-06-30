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

@NodeAttribute(name = "rt_apply_fog", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ApplyFogNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_apply_fog.tooltip");
    }


    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("inColor", RenderTypeGraphTypes.VEC4);
        // The remaining params have meaningful engine defaults (the fog-distance varyings + the Fog UBO),
        // so they need no editor: leave them unconnected to fog with the current scene settings.
        context.addInputPort("sphericalVertexDistance", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("cylindricalVertexDistance", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("environmentalStart", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("environmentalEnd", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("renderDistanceStart", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("renderDistanceEnd", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("fogColor", RenderTypeGraphTypes.VEC4).withoutConfigurator();
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // Under an Iris shaderpack we run inside the pack's gbuffers program, which applies its OWN fog in a
        // later composite pass — and can't satisfy Minecraft's fog.glsl/Fog UBO anyway. So fog is a no-op
        // here: pass the colour straight through (this also keeps the graph injection-compatible, since we
        // never pull the fog include / Fog UBO fields). Same intent as the vanilla deferred path.
        if (ctx.isInjection()) {
            ctx.output("out", ctx.input("inColor"));
            return;
        }
        ctx.include("minecraft:fog.glsl");
        String code = "apply_fog("
                + ctx.input("inColor").code() + ", "
                + fog(ctx, "sphericalVertexDistance", ctx.sphericalVertexDistance()) + ", "
                + fog(ctx, "cylindricalVertexDistance", ctx.cylindricalVertexDistance()) + ", "
                + fog(ctx, "environmentalStart", ctx.fogField("FogEnvironmentalStart", GlslType.FLOAT)) + ", "
                + fog(ctx, "environmentalEnd", ctx.fogField("FogEnvironmentalEnd", GlslType.FLOAT)) + ", "
                + fog(ctx, "renderDistanceStart", ctx.fogField("FogRenderDistanceStart", GlslType.FLOAT)) + ", "
                + fog(ctx, "renderDistanceEnd", ctx.fogField("FogRenderDistanceEnd", GlslType.FLOAT)) + ", "
                + fog(ctx, "fogColor", ctx.fogField("FogColor", GlslType.VEC4)) + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC4));
    }

    /** The connected input, else the supplied engine default. */
    private static String fog(ShaderCompileContext ctx, String id, ShaderExpr def) {
        return (ctx.isConnected(id) ? ctx.input(id) : def).code();
    }
}
