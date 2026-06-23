package com.lowdragmc.kilagraph.rendertype.nodes.artistic.adjustment;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Contrast: scales each colour channel around a fixed midpoint ({@code pow(0.5, 2.2)}, the
 * linear value of mid-grey) by {@code contrast} (1 = unchanged, &gt;1 harder, &lt;1 flatter).
 */
@NodeAttribute(name = "rt_contrast", group = "rendertype_artistic/adjustment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ContrastNode extends ArtisticNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_contrast.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("contrast", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String in = ctx.input("in").code();
        String contrast = ctx.input("contrast").code();
        ctx.output("out", new ShaderExpr(
                "((" + in + " - 0.2176376) * " + contrast + " + 0.2176376)", GlslType.VEC3));
    }
}
