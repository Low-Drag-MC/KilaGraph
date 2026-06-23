package com.lowdragmc.kilagraph.rendertype.nodes.constant;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.GradientValue;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import net.minecraft.network.chat.Component;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * A constant Unity-style gradient: a list of colour keys + independent alpha keys with a Blend/Fixed mode,
 * edited via the built-in gradient-bar configurator and baked into the shader. Outputs an opaque
 * {@code GRADIENT} value; feed it (with a float position) into a {@code SampleGradient} node to get a colour.
 * For a runtime-adjustable gradient, expose a graph variable of type Gradient instead.
 */
@NodeAttribute(name = "rt_gradient", group = "rendertype_constant", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class GradientNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_gradient.tooltip");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("gradient", RenderTypeGraphTypes.GRADIENT)
                .withDisplayName(Component.empty())
                .withDefaultValue(GradientValue.defaultValue())
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("gradient", RenderTypeGraphTypes.GRADIENT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        GradientValue value = ctx.option("gradient", GradientValue.class, GradientValue.defaultValue());
        ctx.output("gradient", ctx.constantGradient(value));
    }
}
