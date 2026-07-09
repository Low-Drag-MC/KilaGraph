package com.lowdragmc.kilagraph.rendertype.nodes.constant;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.CurveValue;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import net.minecraft.network.chat.Component;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * A constant Unity-style float curve: explicit cubic bezier segments remapped to a {@code [lower, upper]}
 * range, edited via the curve-graph configurator and baked into the shader. Outputs an opaque
 * {@code CURVE} value; feed it (with a float position) into a {@code SampleCurve} node to get a float.
 * For a runtime-adjustable curve, expose a graph variable of type Curve instead.
 */
@NodeAttribute(name = "rt_curve", group = "rendertype_constant", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class CurveNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_curve.tooltip");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("curve", RenderTypeGraphTypes.CURVE)
                .withDisplayName(Component.empty())
                .withDefaultValue(CurveValue.defaultValue())
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("curve", RenderTypeGraphTypes.CURVE);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        CurveValue value = ctx.option("curve", CurveValue.class, CurveValue.defaultValue());
        ctx.output("curve", ctx.constantCurve(value));
    }
}
