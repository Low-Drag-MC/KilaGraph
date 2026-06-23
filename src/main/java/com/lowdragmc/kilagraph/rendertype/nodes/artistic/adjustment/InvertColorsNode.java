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
 * Unity's Invert Colors: inverts ({@code 1 - x}) the channels selected by {@code mask} (per-channel,
 * 1 = invert, 0 = keep). Unity's per-channel checkboxes become a {@code mask} vec3 input here.
 */
@NodeAttribute(name = "rt_invert_colors", group = "rendertype_artistic/adjustment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class InvertColorsNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_invert_colors.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("mask", RenderTypeGraphTypes.VEC3).withDefaultValue(new Vector3f(1f, 1f, 1f));
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String in = ctx.input("in").code();
        String mask = ctx.input("mask").code();
        // per channel: mask=1 -> 1-in, mask=0 -> in
        ctx.output("out", new ShaderExpr("mix(" + in + ", 1.0 - " + in + ", " + mask + ")", GlslType.VEC3));
    }
}
