package com.lowdragmc.kilagraph.rendertype.nodes.scene;

import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Unity's Screen Position node: the fragment's screen-space position in a chosen coordinate space.
 * Fragment-only (built on {@code gl_FragCoord}). Modes mirror Unity:
 * <ul>
 *   <li><b>default</b> — normalized {@code [0,1]}, origin lower-left.</li>
 *   <li><b>center</b> — {@code [-1,1]}, origin at screen center.</li>
 *   <li><b>tiled</b> — centered + aspect-corrected, fractional (a repeating tile).</li>
 *   <li><b>pixel</b> — raw pixel coordinates {@code [0,ScreenSize]}.</li>
 *   <li><b>raw</b> — homogeneous screen position (Unity's pre-divide {@code ComputeScreenPos}): {@code .xy/.w}
 *       yields the UV, {@code .z/.w} the window depth, and <b>{@code .w} is the fragment's eye-space distance
 *       from the camera</b> (= clip-space w), reconstructed from {@code gl_FragCoord.z} via the same
 *       {@code IProjMat} basis as Scene Depth "eye". This makes a depth fade
 *       {@code saturate((SceneDepth[eye] - ScreenPosition[raw].w) / d)} camera-position independent.</li>
 * </ul>
 * The {@code default} mode is the canonical UV feeder for the Scene Color / Scene Depth nodes.
 */
@NodeAttribute(name = "rt_screen_position", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ScreenPositionNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_screen_position.tooltip");
    }


    private static final List<String> MODES = List.of("default", "raw", "center", "tiled", "pixel");

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("mode", TypeHandles.STRING).withDefaultValue("default")
                .withTooltips(Tooltips.of(
                        "kg.node.rt_screen_position.option.mode.tooltip.default",
                        "kg.node.rt_screen_position.option.mode.tooltip.raw",
                        "kg.node.rt_screen_position.option.mode.tooltip.center",
                        "kg.node.rt_screen_position.option.mode.tooltip.tiled",
                        "kg.node.rt_screen_position.option.mode.tooltip.pixel"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, MODES, ScreenPositionNode::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // Build every mode on the editor-preview-aware screen UV. In a node/graph preview the geometry
        // covers only a small screen sub-rect, so raw gl_FragCoord/ScreenSize would sample just that corner
        // (center/tiled/pixel would collapse to near-constant values); screenUv() maps the mesh uv there and
        // keeps true gl_FragCoord screen-space in-world. See ShaderGraphCompiler#screenUv.
        String uv = ctx.screenUv().code();
        String mode = ctx.option("mode", String.class, "default");
        // ScreenSize from our own KG_Globals UBO (no Minecraft include) so pixel/tiled stay injectable.
        String screenSize = (mode.equals("pixel") || mode.equals("tiled")) ? ctx.screenSize().code() : null;
        String code = switch (mode) {
            case "pixel" -> "vec4(" + uv + " * " + screenSize + ", 0.0, 0.0)";
            case "center" -> "vec4(" + uv + " * 2.0 - 1.0, 0.0, 0.0)";
            case "tiled" -> "vec4(fract(vec2((" + uv + ".x * 2.0 - 1.0) * (" + screenSize + ".x / " + screenSize + ".y), "
                    + uv + ".y * 2.0 - 1.0)), 0.0, 0.0)";
            // Homogeneous (pre-divide) screen position: w = the fragment's eye-space depth, so .xy/.w = uv and
            // (SceneDepth[eye] - .w) is a camera-independent depth fade. See ShaderCompileContext#fragmentEyeDepth.
            case "raw" -> {
                String w = ctx.temp(GlslType.FLOAT, ctx.fragmentEyeDepth().code()).code();
                yield "vec4(" + uv + " * " + w + ", gl_FragCoord.z * " + w + ", " + w + ")";
            }
            default -> "vec4(" + uv + ", 0.0, 0.0)";
        };
        ctx.output("out", new ShaderExpr(code, GlslType.VEC4));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    private static String label(String mode) {
        return Character.toUpperCase(mode.charAt(0)) + mode.substring(1);
    }
}
