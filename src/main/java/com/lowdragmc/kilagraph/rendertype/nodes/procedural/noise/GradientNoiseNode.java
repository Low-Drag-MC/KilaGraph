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
 * Unity's Gradient Noise: Perlin-style gradient noise — random unit gradients at the lattice corners,
 * dotted with the local offset and quintic-faded — biased into {@code [0,1]}. {@code uv} defaults to
 * the mesh uv; {@code scale} sets the frequency.
 */
@NodeAttribute(name = "rt_gradient_noise", group = "rendertype_procedural/noise", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class GradientNoiseNode extends ProceduralNode {

    /** A hashed pseudo-random unit gradient direction for a lattice point (Unity's GradientNoise_Dir). */
    private static final String DIR = """
            vec2 kg_gradientDir(vec2 p) {
                p = mod(p, 289.0);
                float x = mod((34.0 * p.x + 1.0) * p.x, 289.0) + p.y;
                x = mod((34.0 * x + 1.0) * x, 289.0);
                x = fract(x / 41.0) * 2.0 - 1.0;
                return normalize(vec2(x - floor(x + 0.5), abs(x) - 0.5));
            }""";

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("scale", TypeHandles.FLOAT).withDefaultValue(10f);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.function("kg_gradientDir", DIR);
        String scale = ctx.input("scale").code();
        ShaderExpr p = ctx.temp(GlslType.VEC2, "(" + uv(ctx).code() + " * " + scale + ")");
        ShaderExpr ip = ctx.temp(GlslType.VEC2, "floor(" + p.code() + ")");
        ShaderExpr fp = ctx.temp(GlslType.VEC2, "fract(" + p.code() + ")");
        String i = ip.code();
        String f = fp.code();
        String d00 = "dot(kg_gradientDir(" + i + "), " + f + ")";
        String d01 = "dot(kg_gradientDir(" + i + " + vec2(0.0, 1.0)), " + f + " - vec2(0.0, 1.0))";
        String d10 = "dot(kg_gradientDir(" + i + " + vec2(1.0, 0.0)), " + f + " - vec2(1.0, 0.0))";
        String d11 = "dot(kg_gradientDir(" + i + " + vec2(1.0, 1.0)), " + f + " - vec2(1.0, 1.0))";
        ShaderExpr fade = ctx.temp(GlslType.VEC2,
                f + " * " + f + " * " + f + " * (" + f + " * (" + f + " * 6.0 - 15.0) + 10.0)");
        String w = fade.code();
        String code = "(mix(mix(" + d00 + ", " + d10 + ", " + w + ".x), mix(" + d01 + ", " + d11 + ", " + w + ".x), " + w + ".y) + 0.5)";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }
}
