package com.lowdragmc.kilagraph.rendertype.nodes.scene;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.kilagraph.rendertype.gui.ChoiceConfigurator;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Unity's Scene Depth node: the depth of the opaque scene behind this fragment, sampled from the captured
 * depth buffer ({@code SceneCaptureManager}). The {@code uv} input is a screen-space UV; left unconnected
 * it defaults to the fragment's screen position. {@code Sampling} mirrors Unity:
 * <ul>
 *   <li><b>linear01</b> — {@code 0}(near)..{@code 1}(far), linearised via the inverse projection.</li>
 *   <li><b>raw</b> — the raw hardware depth {@code [0,1]}.</li>
 *   <li><b>eye</b> — distance from the camera in world units.</li>
 * </ul>
 *
 * <p><b>Only meaningful for translucent materials</b> (the capture is taken after opaque geometry).
 * Fragment-only. The canonical use is a soft-particle / depth-fade by comparing against the fragment's
 * own depth.</p>
 *
 * <p><b>Editor previews show a flat value</b> — the capture runs during the world render, not behind the graph
 * editor's offscreen preview scene, so there is no scene depth to sample there (as in Unity). It renders
 * correctly in-world.</p>
 */
@NodeAttribute(name = "rt_scene_depth", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SceneDepthNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_scene_depth.tooltip");
    }

    private static final List<String> MODES = List.of("linear01", "raw", "eye");

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("sampling", TypeHandles.STRING).withDefaultValue("linear01")
                .withTooltips(Tooltips.of(
                        "kg.node.rt_scene_depth.option.sampling.tooltip.linear01",
                        "kg.node.rt_scene_depth.option.sampling.tooltip.raw",
                        "kg.node.rt_scene_depth.option.sampling.tooltip.eye"))
                .withConfigurable((vc, t) -> ChoiceConfigurator.build(vc, MODES, SceneDepthNode::label)).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.VEC2).withoutConfigurator();
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr uv = ctx.isConnected("uv") ? ctx.input("uv") : ctx.screenUv();
        ShaderExpr depth = switch (ctx.option("sampling", String.class, "linear01")) {
            case "raw" -> ctx.sampleSceneDepthRaw(uv);
            case "eye" -> ctx.sampleSceneDepthEye(uv);
            default -> ctx.sampleSceneDepthLinear01(uv);
        };
        ctx.output("out", depth);
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    private static String label(String mode) {
        return switch (mode) {
            case "linear01" -> "Linear 01";
            case "raw" -> "Raw";
            case "eye" -> "Eye";
            default -> mode;
        };
    }
}
