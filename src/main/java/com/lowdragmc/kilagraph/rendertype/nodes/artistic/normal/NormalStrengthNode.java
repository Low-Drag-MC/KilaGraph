package com.lowdragmc.kilagraph.rendertype.nodes.artistic.normal;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Normal Strength: scales a tangent-space normal's {@code xy} by {@code strength} and eases its
 * {@code z} toward 1 — {@code vec3(in.xy * strength, mix(1, in.z, saturate(strength)))}. 1 = unchanged,
 * 0 = flat.
 */
@NodeAttribute(name = "rt_normal_strength", group = "rendertype_artistic/normal", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class NormalStrengthNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_normal_strength.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("strength", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr in = ctx.temp(GlslType.VEC3, ctx.input("in").code());
        String s = ctx.input("strength").code();
        ctx.output("out", new ShaderExpr("vec3(" + in.code() + ".xy * " + s + ", mix(1.0, " + in.code()
                + ".z, clamp(" + s + ", 0.0, 1.0)))", GlslType.VEC3));
    }
}
