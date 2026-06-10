package com.lowdragmc.kilagraph.rendertype.nodes.lighting;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Minecraft's vanilla per-vertex diffuse lighting: {@code minecraft_mix_light(Light0_Direction,
 * Light1_Direction, normal, color)} from {@code minecraft:light.glsl}. Exposes the lighting that used
 * to be a hidden default of the Color vertex block as an explicit node — the default entity shader
 * now wires this into the vertex Color block, and the user can edit or remove it.
 *
 * <p>{@link StageAffinity#VERTEX_ONLY}: an unconnected {@code normal}/{@code color} falls back to the
 * {@code Normal}/{@code Color} vertex attributes, which only exist in the vertex shader.</p>
 */
@NodeAttribute(name = "rt_mix_light", group = "rendertype_lighting", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class MixLightNode extends ShaderNode {
    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.VERTEX_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("normal", RenderTypeGraphTypes.VEC3).withoutConfigurator();
        context.addInputPort("color", RenderTypeGraphTypes.VEC4).withoutConfigurator();
        context.addOutputPort("litColor", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.useMinecraftUniform("Lighting", "minecraft:light.glsl");
        ShaderExpr normal = ctx.isConnected("normal") ? ctx.input("normal") : new ShaderExpr("Normal", GlslType.VEC3);
        ShaderExpr color = ctx.isConnected("color") ? ctx.input("color") : new ShaderExpr("Color", GlslType.VEC4);
        String code = "minecraft_mix_light(Light0_Direction, Light1_Direction, "
                + normal.code() + ", " + color.code() + ")";
        ctx.output("litColor", new ShaderExpr(code, GlslType.VEC4));
    }
}
