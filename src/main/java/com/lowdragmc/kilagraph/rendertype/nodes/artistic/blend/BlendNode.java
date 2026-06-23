package com.lowdragmc.kilagraph.rendertype.nodes.artistic.blend;

import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Unity's Blend: combines {@code base} and {@code blend} (vec3) by the chosen Photoshop-style {@code mode}
 * and lerps from base toward the result by {@code opacity}. The mode is a compile-time dropdown, so only
 * the selected formula is emitted (no runtime branch). Results aren't clamped (matches Unity).
 */
@NodeAttribute(name = "rt_blend", group = "rendertype_artistic/blend", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class BlendNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_blend.tooltip");
    }


    private static final List<String> MODES = List.of(
            "burn", "darken", "difference", "dodge", "divide", "exclusion", "hardlight", "hardmix",
            "lighten", "linearburn", "lineardodge", "linearlight", "linearlightaddsub", "multiply",
            "negation", "overlay", "pinlight", "screen", "softlight", "subtract", "vividlight", "overwrite");

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("mode", TypeHandles.STRING).withDefaultValue("overwrite")
                .withTooltips(Tooltips.of("kg.node.rt_blend.option.mode.tooltip"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, MODES)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("base", RenderTypeGraphTypes.VEC3);
        context.addInputPort("blend", RenderTypeGraphTypes.VEC3);
        context.addInputPort("opacity", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String mode = choice();
        ShaderExpr base = ctx.temp(GlslType.VEC3, ctx.input("base").code());
        ShaderExpr blend = ctx.temp(GlslType.VEC3, ctx.input("blend").code());
        String opacity = ctx.input("opacity").code();
        ShaderExpr blended = ctx.temp(GlslType.VEC3, blendExpr(mode, base.code(), blend.code()));
        ctx.output("out", new ShaderExpr(
                "mix(" + base.code() + ", " + blended.code() + ", " + opacity + ")", GlslType.VEC3));
    }

    private String choice() {
        INodeOption opt = getNodeOptionById("mode");
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && MODES.contains(s) ? s : "overwrite";
    }

    /** The GLSL for {@code mode}'s blend result over vec3 operands {@code b} (base) and {@code o} (blend). */
    private static String blendExpr(String mode, String b, String o) {
        return switch (mode) {
            case "burn" -> "(1.0 - (1.0 - " + b + ") / " + o + ")";
            case "darken" -> "min(" + b + ", " + o + ")";
            case "difference" -> "abs(" + b + " - " + o + ")";
            case "dodge" -> "(" + b + " / (1.0 - " + o + "))";
            case "divide" -> "(" + b + " / (" + o + " + 1e-12))";
            case "exclusion" -> "(" + b + " + " + o + " - 2.0 * " + b + " * " + o + ")";
            case "hardlight" -> "mix(2.0 * " + b + " * " + o + ", 1.0 - 2.0 * (1.0 - " + b + ") * (1.0 - "
                    + o + "), step(0.5, " + o + "))";
            case "hardmix" -> "step(1.0 - " + b + ", " + o + ")";
            case "lighten" -> "max(" + b + ", " + o + ")";
            case "linearburn" -> "(" + b + " + " + o + " - 1.0)";
            case "lineardodge" -> "(" + b + " + " + o + ")";
            case "linearlight" -> "(" + b + " + 2.0 * " + o + " - 1.0)";
            case "linearlightaddsub" -> "(" + o + " + 2.0 * " + b + " - 1.0)";
            case "multiply" -> "(" + b + " * " + o + ")";
            case "negation" -> "(1.0 - abs(1.0 - " + b + " - " + o + "))";
            case "overlay" -> "mix(2.0 * " + b + " * " + o + ", 1.0 - 2.0 * (1.0 - " + b + ") * (1.0 - "
                    + o + "), step(0.5, " + b + "))";
            case "pinlight" -> "mix(min(" + b + ", 2.0 * " + o + "), max(" + b + ", 2.0 * " + o
                    + " - 1.0), step(0.5, " + o + "))";
            case "screen" -> "(1.0 - (1.0 - " + b + ") * (1.0 - " + o + "))";
            case "softlight" -> "mix(2.0 * " + b + " * " + o + " + " + b + " * " + b + " * (1.0 - 2.0 * " + o
                    + "), sqrt(" + b + ") * (2.0 * " + o + " - 1.0) + 2.0 * " + b + " * (1.0 - " + o + "), step(0.5, " + o + "))";
            case "subtract" -> "(" + b + " - " + o + ")";
            case "vividlight" -> "mix(1.0 - (1.0 - " + b + ") / (2.0 * " + o + " + 1e-12), " + b
                    + " / (2.0 * (1.0 - " + o + ") + 1e-12), step(0.5, " + o + "))";
            default /* overwrite */ -> o;
        };
    }
}
