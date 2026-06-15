package com.lowdragmc.kilagraph.rendertype.nodes.texture;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.Sampler2DValue;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * A texture source: outputs a {@code SAMPLER2D} configured via its {@code texture} option (a
 * {@link Sampler2DValue} — custom/atlas location + sampler params, edited with the SAMPLER2D
 * configurator). Mirrors {@code ColorNode}: an option-driven constant the compiler bakes into the
 * material (a unique {@code uniform sampler2D kg_tex_*} with the chosen texture + filter/address/mipmap).
 * Feed its output into a {@code SamplerTexture2DNode}.
 */
@NodeAttribute(name = "rt_texture", group = "rendertype_texture", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TextureNode extends ShaderNode {
    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        // SAMPLER2D carries the Sampler2DConfigurator (custom/atlas picker + sampler params + preview).
        context.addOption("texture", RenderTypeGraphTypes.SAMPLER2D)
                .withDisplayName(Component.empty())
                .withDefaultValue(Sampler2DValue.defaultValue())
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("sampler", RenderTypeGraphTypes.SAMPLER2D);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        Sampler2DValue value = ctx.option("texture", Sampler2DValue.class, Sampler2DValue.defaultValue());
        ctx.output("sampler", ctx.textureSampler(value));
    }

    @Override
    public float getNodeWidth() {
        return 120;
    }
}
