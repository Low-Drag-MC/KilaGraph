package com.lowdragmc.kilagraph.rendertype.nodes.artistic.utility;

import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticGlsl;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Unity's Colorspace Conversion: converts {@code in} between RGB (display/sRGB), Linear, and HSV. Routed
 * through display-RGB as the hub ({@code from → rgb → to}); the sRGB&harr;Linear leg uses the common
 * gamma-2.2 approximation, HSV uses the shared {@link ArtisticGlsl} helpers.
 */
@NodeAttribute(name = "rt_colorspace_conversion", group = "rendertype_artistic/utility", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ColorspaceConversionNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_colorspace_conversion.tooltip");
    }


    private static final List<String> SPACES = List.of("rgb", "linear", "hsv");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("from", TypeHandles.STRING).withDefaultValue("rgb")
                .withTooltips(Tooltips.of("kg.node.rt_colorspace_conversion.option.from.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES)).build();
        context.addOption("to", TypeHandles.STRING).withDefaultValue("linear")
                .withTooltips(Tooltips.of("kg.node.rt_colorspace_conversion.option.to.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, SPACES)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String from = choice("from", "rgb");
        String to = choice("to", "linear");
        ShaderExpr in = ctx.temp(GlslType.VEC3, ctx.input("in").code());
        if (from.equals(to)) {
            ctx.output("out", new ShaderExpr(in.code(), GlslType.VEC3));
            return;
        }
        // from -> display RGB
        String rgb = switch (from) {
            case "linear" -> "pow(" + in.code() + ", vec3(0.45454545))"; // linear -> sRGB (1/2.2)
            case "hsv" -> {
                ctx.function(ArtisticGlsl.HSV2RGB_NAME, ArtisticGlsl.HSV2RGB);
                yield "kg_hsv2rgb(" + in.code() + ")";
            }
            default -> in.code(); // rgb
        };
        ShaderExpr rgbTmp = ctx.temp(GlslType.VEC3, rgb);
        // display RGB -> to
        String out = switch (to) {
            case "linear" -> "pow(" + rgbTmp.code() + ", vec3(2.2))"; // sRGB -> linear
            case "hsv" -> {
                ctx.function(ArtisticGlsl.RGB2HSV_NAME, ArtisticGlsl.RGB2HSV);
                yield "kg_rgb2hsv(" + rgbTmp.code() + ")";
            }
            default -> rgbTmp.code(); // rgb
        };
        ctx.output("out", new ShaderExpr(out, GlslType.VEC3));
    }

    private String choice(String id, String def) {
        INodeOption opt = getNodeOptionById(id);
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && SPACES.contains(s) ? s : def;
    }
}
