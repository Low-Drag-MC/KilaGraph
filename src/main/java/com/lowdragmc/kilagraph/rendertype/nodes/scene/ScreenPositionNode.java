package com.lowdragmc.kilagraph.rendertype.nodes.scene;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
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
 *   <li><b>raw</b> — homogeneous screen position. We lack the pre-divide clip position as a varying, so
 *       this is emitted as the normalized position with {@code w=1} (so {@code .xy/.w} still yields the UV).</li>
 * </ul>
 * The {@code default} mode is the canonical UV feeder for the Scene Color / Scene Depth nodes.
 */
@NodeAttribute(name = "rt_screen_position", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ScreenPositionNode extends ShaderNode {

    private static final List<String> MODES = List.of("default", "raw", "center", "tiled", "pixel");

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("mode", TypeHandles.STRING).withDefaultValue("default")
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, MODES, ScreenPositionNode::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.useMinecraftUniform("Globals", "minecraft:globals.glsl");
        String uv = "(gl_FragCoord.xy / ScreenSize)";
        String code = switch (ctx.option("mode", String.class, "default")) {
            case "pixel" -> "vec4(gl_FragCoord.xy, 0.0, 0.0)";
            case "center" -> "vec4(" + uv + " * 2.0 - 1.0, 0.0, 0.0)";
            case "tiled" -> "vec4(fract(vec2((" + uv + ".x * 2.0 - 1.0) * (ScreenSize.x / ScreenSize.y), "
                    + uv + ".y * 2.0 - 1.0)), 0.0, 0.0)";
            case "raw" -> "vec4(" + uv + ", 0.0, 1.0)";
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
