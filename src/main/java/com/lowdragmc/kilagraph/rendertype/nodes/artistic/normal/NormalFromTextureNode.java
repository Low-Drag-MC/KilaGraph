package com.lowdragmc.kilagraph.rendertype.nodes.artistic.normal;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Normal From Texture: derives a tangent-space normal by sampling a (height) texture at the uv
 * and one {@code offset} step along u and v, then crossing the two slope vectors scaled by {@code strength}.
 * Samples a texture, so {@link StageAffinity#FRAGMENT_ONLY}. Unconnected {@code texture}/{@code uv} fall
 * back to the missing-texture sampler and the mesh uv.
 */
@NodeAttribute(name = "rt_normal_from_texture", group = "rendertype_artistic/normal", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class NormalFromTextureNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_normal_from_texture.tooltip");
    }

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY; // texture() auto-lod needs derivatives
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("texture", RenderTypeGraphTypes.SAMPLER2D).withoutConfigurator();
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("offset", TypeHandles.FLOAT).withDefaultValue(0.005f);
        context.addInputPort("strength", TypeHandles.FLOAT).withDefaultValue(8f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr sampler = ctx.isConnected("texture") ? ctx.input("texture") : ctx.missingSampler();
        ShaderExpr uvExpr = ctx.input("uv");
        ShaderExpr uv = ctx.temp(GlslType.VEC2, uvExpr.code());
        String off = ctx.input("offset").code();
        String s = ctx.input("strength").code();
        String tex = sampler.code();
        ShaderExpr n = ctx.temp(GlslType.FLOAT, "texture(" + tex + ", " + uv.code() + ").r");
        ShaderExpr du = ctx.temp(GlslType.FLOAT,
                "texture(" + tex + ", " + uv.code() + " + vec2(" + off + ", 0.0)).r");
        ShaderExpr dv = ctx.temp(GlslType.FLOAT,
                "texture(" + tex + ", " + uv.code() + " + vec2(0.0, " + off + ")).r");
        String va = "vec3(1.0, 0.0, (" + du.code() + " - " + n.code() + ") * " + s + ")";
        String vb = "vec3(0.0, 1.0, (" + dv.code() + " - " + n.code() + ") * " + s + ")";
        ctx.output("out", new ShaderExpr("normalize(cross(" + va + ", " + vb + "))", GlslType.VEC3));
    }
}
