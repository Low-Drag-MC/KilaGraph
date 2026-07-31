package com.lowdragmc.kilagraph.rendertype.nodes.artistic.adjustment;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticGlsl;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Hue: rotates the colour's hue by {@code offset} degrees (wrapping), leaving saturation and
 * value untouched. Converts to HSV, shifts H, converts back via the shared RGB&harr;HSV helpers.
 */
@NodeAttribute(name = "rt_hue", group = "rendertype_artistic/adjustment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class HueNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_hue.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("offset", TypeHandles.FLOAT); // degrees
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.function(ArtisticGlsl.RGB2HSV_NAME, ArtisticGlsl.RGB2HSV);
        ctx.function(ArtisticGlsl.HSV2RGB_NAME, ArtisticGlsl.HSV2RGB);
        String in = ctx.input("in").code();
        String offset = ctx.input("offset").code();
        ShaderExpr hsv = ctx.temp(GlslType.VEC3, "kg_rgb2hsv(" + in + ")");
        // shift hue and wrap into [0,1) (fract handles either sign)
        String h = "fract(" + hsv.code() + ".x + " + offset + " / 360.0)";
        ctx.output("out", new ShaderExpr(
                "kg_hsv2rgb(vec3(" + h + ", " + hsv.code() + ".yz))", GlslType.VEC3));
    }

    @Override
    public String glslExample() {
        return """
                vec3 hsv = kg_rgb2hsv(in);
                hsv.x = fract(hsv.x + offset / 360.0);
                out = kg_hsv2rgb(hsv);""";
    }
}
