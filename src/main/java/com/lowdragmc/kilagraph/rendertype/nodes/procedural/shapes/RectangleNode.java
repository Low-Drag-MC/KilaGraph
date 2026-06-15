package com.lowdragmc.kilagraph.rendertype.nodes.procedural.shapes;

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
 * Unity's Rectangle: a {@code [0,1]} mask, 1 inside an axis-aligned rectangle of {@code width}×{@code
 * height} centred on the uv, with an antialiased edge ({@code fwidth}). {@code uv} defaults to the mesh uv.
 */
@NodeAttribute(name = "rt_rectangle", group = "rendertype_procedural/shapes", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class RectangleNode extends ShapeNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("width", TypeHandles.FLOAT).withDefaultValue(0.5f);
        context.addInputPort("height", TypeHandles.FLOAT).withDefaultValue(0.5f);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String width = ctx.input("width").code();
        String height = ctx.input("height").code();
        ShaderExpr d = ctx.temp(GlslType.VEC2,
                "abs(" + uv(ctx).code() + " * 2.0 - 1.0) - vec2(" + width + ", " + height + ")");
        ShaderExpr aa = ctx.temp(GlslType.VEC2, "(1.0 - " + d.code() + " / fwidth(" + d.code() + "))");
        ctx.output("out", new ShaderExpr(
                "clamp(min(" + aa.code() + ".x, " + aa.code() + ".y), 0.0, 1.0)", GlslType.FLOAT));
    }
}
