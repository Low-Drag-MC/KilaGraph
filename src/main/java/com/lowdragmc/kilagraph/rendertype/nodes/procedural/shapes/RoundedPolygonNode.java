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
 * Unity's Rounded Polygon: a {@code [0,1]} mask of a regular {@code sides}-gon of {@code width}×
 * {@code height} whose corners are arc-rounded by {@code roundness}, centred on the uv and antialiased
 * ({@code fwidth}). Uses an exact regular-polygon signed distance field (so the corner rounding is a
 * true offset of the SDF), matching the node's mask semantics. {@code uv} defaults to the mesh uv.
 */
@NodeAttribute(name = "rt_rounded_polygon", group = "rendertype_procedural/shapes", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class RoundedPolygonNode extends ShapeNode {

    /** Exact signed distance to a unit-circumradius regular n-gon, inset by {@code round} (rounds corners). */
    private static final String SDF = """
            float kg_sdRoundedPolygon(vec2 p, float n, float round) {
                n = max(n, 3.0);
                float an = 3.14159265359 / n;
                vec2 acs = vec2(cos(an), sin(an));
                float bn = mod(atan(p.x, p.y), 2.0 * an) - an;
                p = length(p) * vec2(cos(bn), abs(sin(bn)));
                p -= acs;
                p.y += clamp(-p.y, 0.0, acs.y);
                return length(p) * sign(p.x) - round;
            }""";

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("width", TypeHandles.FLOAT).withDefaultValue(0.5f);
        context.addInputPort("height", TypeHandles.FLOAT).withDefaultValue(0.5f);
        context.addInputPort("sides", TypeHandles.FLOAT).withDefaultValue(6f);
        context.addInputPort("roundness", TypeHandles.FLOAT).withDefaultValue(0.3f);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.function("kg_sdRoundedPolygon", SDF);
        String width = ctx.input("width").code();
        String height = ctx.input("height").code();
        String sides = ctx.input("sides").code();
        String roundness = ctx.input("roundness").code();
        ShaderExpr p = ctx.temp(GlslType.VEC2,
                "((" + uv(ctx).code() + " * 2.0 - 1.0) / vec2(" + width + ", " + height + "))");
        ShaderExpr d = ctx.temp(GlslType.FLOAT,
                "kg_sdRoundedPolygon(" + p.code() + ", " + sides + ", " + roundness + ")");
        // SDF fill: well inside (d<0) -> 1, edge (d=0) -> 0.5, outside -> 0, one-pixel antialiased.
        ctx.output("out", new ShaderExpr(
                "clamp(0.5 - " + d.code() + " / fwidth(" + d.code() + "), 0.0, 1.0)", GlslType.FLOAT));
    }
}
