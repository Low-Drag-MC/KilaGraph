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
 * Unity's Polygon: a {@code [0,1]} mask of a regular {@code sides}-gon of {@code width}×{@code height},
 * centred on the uv, with an antialiased edge ({@code fwidth}). {@code uv} defaults to the mesh uv.
 */
@NodeAttribute(name = "rt_polygon", group = "rendertype_procedural/shapes", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class PolygonNode extends ShapeNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("sides", TypeHandles.FLOAT).withDefaultValue(6f);
        context.addInputPort("width", TypeHandles.FLOAT).withDefaultValue(0.5f);
        context.addInputPort("height", TypeHandles.FLOAT).withDefaultValue(0.5f);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String sides = ctx.input("sides").code();
        String width = ctx.input("width").code();
        String height = ctx.input("height").code();
        // Apothem correction so the polygon inscribes the width/height box (Unity's aWidth/aHeight).
        ShaderExpr aw = ctx.temp(GlslType.FLOAT, width + " * cos(3.14159265359 / " + sides + ")");
        ShaderExpr ah = ctx.temp(GlslType.FLOAT, height + " * cos(3.14159265359 / " + sides + ")");
        ShaderExpr p = ctx.temp(GlslType.VEC2,
                "(" + uv(ctx).code() + " * 2.0 - 1.0) / vec2(" + aw.code() + ", " + ah.code() + ")");
        // atan(x, -y) folds Unity's uv.y *= -1 into the angle; length is sign-independent.
        ShaderExpr pCoord = ctx.temp(GlslType.FLOAT, "atan(" + p.code() + ".x, -" + p.code() + ".y)");
        ShaderExpr r = ctx.temp(GlslType.FLOAT, "(2.0 * 3.14159265359 / " + sides + ")");
        ShaderExpr dist = ctx.temp(GlslType.FLOAT, "cos(floor(0.5 + " + pCoord.code() + " / " + r.code()
                + ") * " + r.code() + " - " + pCoord.code() + ") * length(" + p.code() + ")");
        ctx.output("out", new ShaderExpr(
                "clamp((1.0 - " + dist.code() + ") / fwidth(" + dist.code() + "), 0.0, 1.0)", GlslType.FLOAT));
    }
}
