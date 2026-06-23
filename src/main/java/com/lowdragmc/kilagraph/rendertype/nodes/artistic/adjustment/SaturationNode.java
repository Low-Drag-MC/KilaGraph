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
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Saturation: pushes each channel toward ({@code <1}) or away from ({@code >1}) the colour's
 * luminance (Rec.709 weights). 1 = unchanged, 0 = greyscale.
 */
@NodeAttribute(name = "rt_saturation", group = "rendertype_artistic/adjustment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SaturationNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_saturation.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("saturation", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String in = ctx.input("in").code();
        String sat = ctx.input("saturation").code();
        ShaderExpr luma = ctx.temp(GlslType.FLOAT, "dot(" + in + ", vec3(0.2126729, 0.7151522, 0.0721750))");
        ctx.output("out", new ShaderExpr(
                "(" + luma.code() + " + " + sat + " * (" + in + " - " + luma.code() + "))", GlslType.VEC3));
    }
}
