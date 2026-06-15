package com.lowdragmc.kilagraph.rendertype.nodes.procedural.noise;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.nodes.procedural.ProceduralNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Voronoi (Worley) noise: scatters one feature point per cell (jittered by {@code angleOffset})
 * over a {@code cellDensity} grid and, per fragment, finds the nearest. {@code out} is the distance to
 * that point (cell borders → 1); {@code cells} is a per-cell random value (a flat id per region).
 * {@code uv} defaults to the mesh uv.
 */
@NodeAttribute(name = "rt_voronoi", group = "rendertype_procedural/noise", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class VoronoiNode extends ProceduralNode {

    /** Hashed feature-point offset within a cell, angle-jittered (Unity's voronoi_noise_randomVector). */
    private static final String RANDOM = """
            vec2 kg_voronoiRandom(vec2 uv, float offset) {
                mat2 m = mat2(15.27, 47.63, 99.41, 89.98);
                uv = fract(sin(m * uv) * 46839.32);
                return vec2(sin(uv.y * offset) * 0.5 + 0.5, cos(uv.x * offset) * 0.5 + 0.5);
            }""";

    /** Nearest-feature search over the 3x3 neighbourhood; writes the min distance + that cell's value. */
    private static final String VORONOI = """
            void kg_voronoi(vec2 uv, float angleOffset, float cellDensity, out float outDist, out float cells) {
                vec2 g = floor(uv * cellDensity);
                vec2 f = fract(uv * cellDensity);
                float res = 8.0;
                outDist = 8.0;
                cells = 0.0;
                for (int y = -1; y <= 1; y++) {
                    for (int x = -1; x <= 1; x++) {
                        vec2 lattice = vec2(float(x), float(y));
                        vec2 offset = kg_voronoiRandom(lattice + g, angleOffset);
                        float d = distance(lattice + offset, f);
                        if (d < res) {
                            res = d;
                            outDist = d;
                            cells = offset.x;
                        }
                    }
                }
            }""";

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("angleOffset", TypeHandles.FLOAT).withDefaultValue(2f);
        context.addInputPort("cellDensity", TypeHandles.FLOAT).withDefaultValue(5f);
        context.addOutputPort("out", TypeHandles.FLOAT);
        context.addOutputPort("cells", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.function("kg_voronoiRandom", RANDOM); // callee first, so it's declared before kg_voronoi uses it
        ctx.function("kg_voronoi", VORONOI);
        String angle = ctx.input("angleOffset").code();
        String density = ctx.input("cellDensity").code();
        ShaderExpr outV = ctx.temp(GlslType.FLOAT, "0.0");
        ShaderExpr cellsV = ctx.temp(GlslType.FLOAT, "0.0");
        ctx.line("kg_voronoi(" + uv(ctx).code() + ", " + angle + ", " + density + ", "
                + outV.code() + ", " + cellsV.code() + ");");
        ctx.output("out", outV);
        ctx.output("cells", cellsV);
    }
}
