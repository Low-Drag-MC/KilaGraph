package com.lowdragmc.kilagraph.rendertype.nodes.artistic.adjustment;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import org.joml.Vector3f;

/**
 * Unity's Channel Mixer: each output channel is a weighted mix of the input's R/G/B. Unity's per-output
 * sliders become three weight vec3s ({@code red}/{@code green}/{@code blue}) here, defaulting to the
 * identity so an unwired node is a pass-through.
 */
@NodeAttribute(name = "rt_channel_mixer", group = "rendertype_artistic/adjustment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ChannelMixerNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_channel_mixer.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("red", RenderTypeGraphTypes.VEC3).withDefaultValue(new Vector3f(1f, 0f, 0f));
        context.addInputPort("green", RenderTypeGraphTypes.VEC3).withDefaultValue(new Vector3f(0f, 1f, 0f));
        context.addInputPort("blue", RenderTypeGraphTypes.VEC3).withDefaultValue(new Vector3f(0f, 0f, 1f));
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr in = ctx.temp(GlslType.VEC3, ctx.input("in").code());
        String c = in.code();
        ctx.output("out", new ShaderExpr("vec3(dot(" + c + ", " + ctx.input("red").code() + "), dot("
                + c + ", " + ctx.input("green").code() + "), dot(" + c + ", " + ctx.input("blue").code() + "))",
                GlslType.VEC3));
    }

    @Override
    public String glslExample() {
        return """
                out = vec3(dot(in, red), dot(in, green),
                           dot(in, blue));""";
    }
}
