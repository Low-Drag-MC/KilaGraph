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
 * Vanilla's lightmap texture ({@code Sampler2}). Outputs the sampler handle; its presence makes the
 * compiler flag {@code usesLightmap} so the runtime enables vanilla lightmap binding (no default texture
 * is registered — the vanilla pipeline owns it). Feed it into a {@code SamplerTexture2DNode}.
 */
@NodeAttribute(name = "rt_lightmap_texture", group = "rendertype_texture", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class LightMapTextureNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_lightmap_texture.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("sampler", RenderTypeGraphTypes.SAMPLER2D);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("sampler", ctx.lightmapSampler());
    }

    @Override
    public String glslExample() {
        return """
                uniform sampler2D Sampler2;
                sampler = Sampler2;""";
    }
}
