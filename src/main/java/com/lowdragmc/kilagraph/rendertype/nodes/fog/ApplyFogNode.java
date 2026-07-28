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
        ctx.include("minecraft:fog.glsl");
        // 1.21.1 has a single linear fog band: linear_fog(inColor, vertexDistance, fogStart, fogEnd, fogColor).
        // The 1.21.5 environmental band maps to FogStart/FogEnd; the extra 1.21.5 inputs (cylindrical /
        // renderDistance) remain as ports for graph compatibility but linear_fog uses one band + FogColor.
        String code = "linear_fog("
                + ctx.input("inColor").code() + ", "
                + fog(ctx, "sphericalVertexDistance", ctx.sphericalVertexDistance()) + ", "
                + fog(ctx, "environmentalStart", ctx.fogField("FogStart", GlslType.FLOAT)) + ", "
                + fog(ctx, "environmentalEnd", ctx.fogField("FogEnd", GlslType.FLOAT)) + ", "
                + fog(ctx, "fogColor", ctx.fogField("FogColor", GlslType.VEC4)) + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC4));
    }

    /** The connected input, else the supplied engine default. */
    private static String fog(ShaderCompileContext ctx, String id, ShaderExpr def) {
        return (ctx.isConnected(id) ? ctx.input(id) : def).code();
    }

    @Override
    public String glslExample() {
        return """
                out = linear_fog(inColor,
                    sphericalVertexDistance,
                    environmentalStart, environmentalEnd,
                    fogColor);""";
    }
}
