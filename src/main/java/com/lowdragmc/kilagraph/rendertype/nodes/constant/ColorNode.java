package com.lowdragmc.kilagraph.rendertype.nodes.constant;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslFormat;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IOptionBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * A constant color literal: outputs a {@code vec4} baked into the shader. For a runtime-adjustable
 * color, expose a global graph variable (which compiles to a material uniform) rather than using this
 * node.
 *
 * <p>The {@code mode} dropdown picks the editor:
 * <ul>
 *   <li>{@code default} — the built-in {@link TypeHandles#COLOR} ARGB color picker, components in 0..1.</li>
 *   <li>{@code hdr} — an {@link TypeHandles#HDR_COLOR} picker with an extra intensity, so components may
 *       exceed 1 (emission / bloom). The intensity is premultiplied into rgb when baked.</li>
 * </ul>
 * Both options are always defined so switching modes never loses the other one's value; only the
 * inactive one's inspector row is suppressed.
 */
@NodeAttribute(name = "rt_color", group = "rendertype_constant", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ColorNode extends ShaderNode {

    public static final String MODE_DEFAULT = "default";
    public static final String MODE_HDR = "hdr";
    private static final List<String> MODES = List.of(MODE_DEFAULT, MODE_HDR);

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        // built first so its value is readable below — the builder hands the option back from build()
        var modeOption = context.addOption("mode", TypeHandles.STRING).withDefaultValue(MODE_DEFAULT)
                .withTooltips(Tooltips.of(
                        "kg.node.rt_color.option.mode.tooltip.default",
                        "kg.node.rt_color.option.mode.tooltip.hdr"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, MODES, ColorNode::label))
                .build();
        var hdr = MODE_HDR.equals(readMode(modeOption));

        // TypeHandles.COLOR (ARGB int) carries a ColorConfigurator, so this renders a color picker.
        // Never retype this option: a saved constant whose type no longer matches is silently dropped.
        IOptionBuilder<?> colorBuilder = context.addOption("color", TypeHandles.COLOR);
        colorBuilder.withDefaultValue(-1); // white, full alpha
        if (hdr) colorBuilder.withoutConfigurator();
        colorBuilder.build();

        // Defaults to HDRColor.white() via the type handle's registered default-value supplier.
        IOptionBuilder<?> hdrBuilder = context.addOption("hdr_color", TypeHandles.HDR_COLOR);
        if (!hdr) hdrBuilder.withoutConfigurator();
        hdrBuilder.build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("color", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        float r, g, b, a;
        if (MODE_HDR.equals(mode())) {
            var color = ctx.option("hdr_color", HDRColor.class, HDRColor.white()).toVector4f();
            r = color.x;
            g = color.y;
            b = color.z;
            a = color.w;
        } else {
            int argb = ctx.option("color", Integer.class, -1);
            a = ((argb >> 24) & 0xFF) / 255f;
            r = ((argb >> 16) & 0xFF) / 255f;
            g = ((argb >> 8) & 0xFF) / 255f;
            b = (argb & 0xFF) / 255f;
        }
        String code = "vec4(" + GlslFormat.f(r) + ", " + GlslFormat.f(g) + ", "
                + GlslFormat.f(b) + ", " + GlslFormat.f(a) + ")";
        ctx.output("color", new ShaderExpr(code, GlslType.VEC4));
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "mode".equals(optionId) ? MODES : List.of();
    }

    @Override
    public String glslExample() {
        return """
                // baked from the color picker
                color = vec4(1.0, 0.5, 0.25, 1.0);
                // hdr mode bakes color * intensity
                color = vec4(2.5, 1.25, 0.625, 1.0);""";
    }

    private static String label(String mode) {
        return MODE_HDR.equals(mode) ? "HDR" : "Default";
    }

    private String mode() {
        return readMode(getNodeOptionById("mode"));
    }

    private static String readMode(INodeOption option) {
        Object raw = option == null ? null : option.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof String s && MODES.contains(s) ? s : MODE_DEFAULT;
    }
}
