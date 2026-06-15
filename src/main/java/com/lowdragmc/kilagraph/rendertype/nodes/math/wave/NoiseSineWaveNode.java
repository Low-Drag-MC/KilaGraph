package com.lowdragmc.kilagraph.rendertype.nodes.math.wave;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Noise Sine Wave: {@code sin(in)} plus a pseudo-random term in {@code [minMax.x, minMax.y]}, so the
 * sine is jittered by hashed noise. Output width follows {@code in}.
 */
@NodeAttribute(name = "rt_noise_sine_wave", group = "rendertype_math/wave", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class NoiseSineWaveNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("minMax", RenderTypeGraphTypes.VEC2);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr in = ctx.inputDynamic("in");
        String mm = ctx.input("minMax").code();
        ShaderExpr sinIn = ctx.temp(in.type(), "sin(" + in.code() + ")");
        String rnd = "fract(sin(" + sinIn.code() + " * 12.9898) * 43758.5453)";
        String noise = "mix((" + mm + ").x, (" + mm + ").y, " + rnd + ")";
        ctx.output("out", new ShaderExpr("(" + sinIn.code() + " + " + noise + ")", in.type()));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
