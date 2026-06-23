package com.lowdragmc.kilagraph.rendertype.nodes.input.fragment;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * The GLSL built-in {@code gl_FragCoord} — the raw window-space fragment coordinate (a {@code vec4}:
 * {@code xy} = window pixel, {@code z} = window-space depth in {@code [0,1]}, {@code w} = {@code 1/clip.w}).
 * Fragment-only.
 *
 * <p>Distinct from the Screen Position node, which normalizes {@code xy} into UV space and carries no
 * depth/w. This reports the built-in verbatim, so it stays valid in the per-node preview as-is.</p>
 */
@NodeAttribute(name = "rt_fragment_coord", group = "rendertype_input/fragment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FragmentCoordinateNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_fragment_coord.tooltip");
    }

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr("gl_FragCoord", GlslType.VEC4));
    }
}
