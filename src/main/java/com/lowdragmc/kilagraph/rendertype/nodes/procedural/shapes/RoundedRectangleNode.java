package com.lowdragmc.kilagraph.rendertype.nodes.procedural.shapes;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Rounded Rectangle: a {@code [0,1]} mask of a {@code width}×{@code height} rectangle with
 * {@code radius} corner rounding, centred on the uv, antialiased ({@code fwidth}). {@code radius} is
 * clamped so it can't exceed the half-extents. {@code uv} defaults to the mesh uv.
 */
@NodeAttribute(name = "rt_rounded_rectangle", group = "rendertype_procedural/shapes", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class RoundedRectangleNode extends ShapeNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_rounded_rectangle.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("width", TypeHandles.FLOAT).withDefaultValue(0.5f);
        context.addInputPort("height", TypeHandles.FLOAT).withDefaultValue(0.5f);
        context.addInputPort("radius", TypeHandles.FLOAT).withDefaultValue(0.1f);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String width = ctx.input("width").code();
        String height = ctx.input("height").code();
        String radius = ctx.input("radius").code();
        ShaderExpr r = ctx.temp(GlslType.FLOAT, "max(min(min(abs(" + radius + " * 2.0), abs(" + width
                + ")), abs(" + height + ")), 1e-5)");
        ShaderExpr q = ctx.temp(GlslType.VEC2, "abs(" + uv(ctx).code() + " * 2.0 - 1.0) - vec2("
                + width + ", " + height + ") + " + r.code());
        ShaderExpr d = ctx.temp(GlslType.FLOAT, "length(max(vec2(0.0), " + q.code() + ")) / " + r.code());
        ctx.output("out", new ShaderExpr(
                "clamp((1.0 - " + d.code() + ") / fwidth(" + d.code() + "), 0.0, 1.0)", GlslType.FLOAT));
    }
}
