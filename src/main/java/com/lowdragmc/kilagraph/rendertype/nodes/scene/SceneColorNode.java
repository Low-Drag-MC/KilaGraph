package com.lowdragmc.kilagraph.rendertype.nodes.scene;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Scene Color node: the opaque scene colour behind this fragment, sampled from the captured
 * opaque colour buffer ({@code SceneCaptureManager}). The {@code uv} input is a screen-space UV; left
 * unconnected it defaults to the fragment's screen position ({@code gl_FragCoord.xy / ScreenSize}).
 *
 * <p><b>Only meaningful for translucent materials</b> ({@code BlendMode != OPAQUE}): the capture is taken
 * after all opaque geometry, so an opaque material draws before it exists. Fragment-only.</p>
 *
 * <p><b>Editor previews show black</b> — the capture runs during the world render ({@code SceneCaptureManager}),
 * which does not happen behind the graph editor's offscreen preview scene, so there is no scene to sample there
 * (as in Unity). It renders correctly in-world.</p>
 */
@NodeAttribute(name = "rt_scene_color", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SceneColorNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_scene_color.tooltip");
    }


    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        // Screen-space UV. Unconnected -> the fragment's own screen position (handled in compile), so no
        // literal configurator.
        context.addInputPort("uv", RenderTypeGraphTypes.VEC2).withoutConfigurator();
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr uv = ctx.isConnected("uv") ? ctx.input("uv") : ctx.screenUv();
        ctx.output("out", ctx.sampleSceneColor(uv));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                uniform sampler2D KG_SceneColor;
                out = texture(KG_SceneColor, uv).rgb;""";
    }
}
