package com.lowdragmc.kilagraph.rendertype.nodes.texture;

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

@NodeAttribute(name = "rt_sampler_texture2d", group = "rendertype_texture", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SamplerTexture2DNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_sampler_texture2d.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("sampler", RenderTypeGraphTypes.SAMPLER2D).withoutConfigurator();
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addOutputPort("color", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // Unconnected sampler/uv fall back to the missing-texture sampler and the mesh uv, so the node
        // always samples something (the texture is meant to come from a Sampler2D constant/variable).
        ShaderExpr sampler = ctx.isConnected("sampler") ? ctx.input("sampler") : ctx.missingSampler();
        ShaderExpr uv = ctx.input("uv");
        ctx.output("color", new ShaderExpr("texture(" + sampler.code() + ", " + uv.code() + ")", GlslType.VEC4));
    }

    @Override
    protected String previewOutputPortId() {
        return "color";
    }
}

