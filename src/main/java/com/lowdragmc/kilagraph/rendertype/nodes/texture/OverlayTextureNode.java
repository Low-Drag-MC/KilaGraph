package com.lowdragmc.kilagraph.rendertype.nodes.texture;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Vanilla's overlay texture ({@code Sampler1}). Outputs the sampler handle; its presence makes the
 * compiler flag {@code usesOverlay} so the runtime enables vanilla overlay binding (no default texture
 * is registered — the vanilla pipeline owns it). Feed it into a {@code SamplerTexture2DNode}.
 */
@NodeAttribute(name = "rt_overlay_texture", group = "rendertype_texture", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class OverlayTextureNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_overlay_texture.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("sampler", RenderTypeGraphTypes.SAMPLER2D);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("sampler", ctx.overlaySampler());
    }
}
