package com.lowdragmc.kilagraph.rendertype.nodes.lighting;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "rt_lighting_ubo", group = "rendertype_lighting", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class LightUboNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_lighting_ubo.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("Light0_Direction", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("Light1_Direction", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // KG_Lighting is a slice-view of Minecraft's own Lighting buffer (identical values, no #moj_import)
        // — keeps graphs reading the vanilla light directions injectable under an Iris shaderpack.
        ctx.useUniformBlock(com.lowdragmc.kilagraph.rendertype.runtime.KGLightingUniforms.BLOCK);
        ctx.output("Light0_Direction", new ShaderExpr(
                com.lowdragmc.kilagraph.rendertype.runtime.KGLightingUniforms.accessor("Light0_Direction"), GlslType.VEC3));
        ctx.output("Light1_Direction", new ShaderExpr(
                com.lowdragmc.kilagraph.rendertype.runtime.KGLightingUniforms.accessor("Light1_Direction"), GlslType.VEC3));
    }
}
