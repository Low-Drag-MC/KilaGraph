package com.lowdragmc.kilagraph.rendertype.nodes.artistic.gradient;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import net.minecraft.network.chat.Component;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Sample Gradient: samples a {@code Gradient} at a float {@code Time} (0..1) and outputs the
 * interpolated {@code vec4} colour. Colour and alpha keys are interpolated independently; the gradient's
 * mode picks smooth (Blend) vs stepped (Fixed). An unconnected {@code Time} defaults to the mesh UV's x,
 * so the node preview shows the gradient swept horizontally.
 */
@NodeAttribute(name = "rt_sample_gradient", group = "rendertype_artistic/gradient", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SampleGradientNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_sample_gradient.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("gradient", RenderTypeGraphTypes.GRADIENT);
        // No inline editor: an unconnected Time isn't a typed-in constant — it falls back to the mesh UV's x
        // (a useful Unity-like preview default), so a 0.0 float editor would be misleading.
        context.addInputPort("time", TypeHandles.FLOAT).withoutConfigurator();
        context.addOutputPort("color", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // Ensure the struct decl + sample function exist regardless of how the gradient expression arrives.
        ctx.useGradient();
        ShaderExpr gradient = ctx.input("gradient");
        // An unconnected Time sweeps the gradient across the mesh uv (a useful, Unity-like preview default).
        String time = ctx.isConnected("time") ? ctx.input("time").code() : ctx.meshUv().code() + ".x";
        ctx.output("color", new ShaderExpr(
                "kg_sampleGradient(" + gradient.code() + ", " + time + ")", GlslType.VEC4));
    }
}
