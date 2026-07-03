package com.lowdragmc.kilagraph.rendertype.nodes.fog;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "rt_total_fog_value", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TotalFogValueNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_total_fog_value.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        // All params have meaningful engine defaults (the fog-distance varyings + the Fog UBO), so no editor.
        context.addInputPort("sphericalVertexDistance", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("cylindricalVertexDistance", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("environmentalStart", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("environmentalEnd", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("renderDistanceStart", TypeHandles.FLOAT).withoutConfigurator();
        context.addInputPort("renderDistanceEnd", TypeHandles.FLOAT).withoutConfigurator();
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.include("minecraft:fog.glsl");
        // 1.21.1: single linear fog band; total fog value = 1 - fade(dist, FogStart, FogEnd). The 1.21.5
        // cylindrical/renderDistance inputs stay as ports for graph compat; the single band uses environmental.
        String code = "(1.0 - linear_fog_fade("
                + fog(ctx, "sphericalVertexDistance", ctx.sphericalVertexDistance()) + ", "
                + fog(ctx, "environmentalStart", ctx.fogField("FogStart", GlslType.FLOAT)) + ", "
                + fog(ctx, "environmentalEnd", ctx.fogField("FogEnd", GlslType.FLOAT)) + "))";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }

    /** The connected input, else the supplied engine default. */
    private static String fog(ShaderCompileContext ctx, String id, ShaderExpr def) {
        return (ctx.isConnected(id) ? ctx.input(id) : def).code();
    }
}
