package com.lowdragmc.kilagraph.rendertype.nodes.math.range;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * A deterministic per-pixel pseudo-random scalar in {@code [min, max]}, hashed from a {@code seed} vec2
 * (the classic {@code fract(sin(dot(seed, k)) * m)} hash). Same seed → same value (not time-varying).
 */
@NodeAttribute(name = "rt_random_range", group = "rendertype_math/range", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class RandomRangeNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("seed", RenderTypeGraphTypes.VEC2);
        context.addInputPort("min", TypeHandles.FLOAT);
        context.addInputPort("max", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String seed = ctx.input("seed").code();
        String rnd = "fract(sin(dot(" + seed + ", vec2(12.9898, 78.233))) * 43758.5453)";
        String code = "mix(" + ctx.input("min").code() + ", " + ctx.input("max").code() + ", " + rnd + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
