package com.lowdragmc.kilagraph.rendertype.nodes.artistic.curve;

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
 * Samples a {@code Curve} at a float {@code Time} (0..1) and outputs the interpolated float, remapped to
 * the curve's {@code [lower, upper]} range. An unconnected {@code Time} defaults to the mesh UV's x, so
 * the node preview shows the curve swept horizontally.
 */
@NodeAttribute(name = "rt_sample_curve", group = "rendertype_artistic/curve", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SampleCurveNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_sample_curve.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("curve", RenderTypeGraphTypes.CURVE);
        // No inline editor: an unconnected Time isn't a typed-in constant — it falls back to the mesh UV's x
        // (a useful Unity-like preview default), so a 0.0 float editor would be misleading.
        context.addInputPort("time", TypeHandles.FLOAT).withoutConfigurator();
        context.addOutputPort("value", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // Ensure the struct decl + sample function exist regardless of how the curve expression arrives.
        ctx.useCurve();
        ShaderExpr curve = ctx.input("curve");
        // An unconnected Time sweeps the curve across the mesh uv (a useful, Unity-like preview default).
        String time = ctx.isConnected("time") ? ctx.input("time").code() : ctx.meshUv().code() + ".x";
        ctx.output("value", new ShaderExpr(
                "kg_sampleCurve(" + curve.code() + ", " + time + ")", GlslType.FLOAT));
    }

    @Override
    public String glslExample() {
        return """
                value = kg_sampleCurve(curve, time);""";
    }
}
