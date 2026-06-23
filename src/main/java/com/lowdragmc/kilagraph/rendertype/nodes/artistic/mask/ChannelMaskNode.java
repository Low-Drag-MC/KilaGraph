package com.lowdragmc.kilagraph.rendertype.nodes.artistic.mask;

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
 * Unity's Channel Mask: keeps only the channels selected by {@code mask} (per-channel 1 = keep, 0 = drop),
 * zeroing the rest. Unity's channel checkboxes become a {@code mask} vec3 here.
 */
@NodeAttribute(name = "rt_channel_mask", group = "rendertype_artistic/mask", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ChannelMaskNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_channel_mask.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("mask", RenderTypeGraphTypes.VEC3).withDefaultValue(new Vector3f(1f, 1f, 1f));
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr(
                "(" + ctx.input("in").code() + " * " + ctx.input("mask").code() + ")", GlslType.VEC3));
    }
}
