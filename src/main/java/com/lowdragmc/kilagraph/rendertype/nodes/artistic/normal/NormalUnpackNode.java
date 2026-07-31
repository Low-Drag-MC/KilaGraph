package com.lowdragmc.kilagraph.rendertype.nodes.artistic.normal;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Normal Unpack: turns a {@code [0,1]} normal-map sample into a signed tangent-space normal —
 * {@code normalize(in * 2 - 1)}.
 */
@NodeAttribute(name = "rt_normal_unpack", group = "rendertype_artistic/normal", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class NormalUnpackNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_normal_unpack.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr(
                "normalize(" + ctx.input("in").code() + " * 2.0 - 1.0)", GlslType.VEC3));
    }

    @Override
    public String glslExample() {
        return """
                out = normalize(in * 2.0 - 1.0);""";
    }
}
