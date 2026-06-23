package com.lowdragmc.kilagraph.rendertype.nodes.input.fragment;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * The GLSL built-in {@code gl_FrontFacing} — whether the fragment belongs to a front-facing primitive
 * (a {@code bool}). Fragment-only. Pairs with the Branch/logic nodes for two-sided shading (e.g. a
 * different color on back faces).
 */
@NodeAttribute(name = "rt_front_facing", group = "rendertype_input/fragment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FrontFacingNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_front_facing.tooltip");
    }

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("out", TypeHandles.BOOL);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr("gl_FrontFacing", GlslType.BOOL));
    }
}
