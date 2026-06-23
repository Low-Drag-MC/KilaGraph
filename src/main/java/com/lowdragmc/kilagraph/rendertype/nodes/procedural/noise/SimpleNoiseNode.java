package com.lowdragmc.kilagraph.rendertype.nodes.procedural.noise;

import net.minecraft.network.chat.Component;
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
 * Unity's Simple Noise: value noise summed over three octaves (freq ×1/2/4, amp ×0.125/0.25/0.5),
 * giving a soft {@code [0,1]} fractal noise from a hashed lattice. {@code uv} defaults to the mesh uv;
 * {@code scale} sets the base frequency.
 */
@NodeAttribute(name = "rt_simple_noise", group = "rendertype_procedural/noise", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SimpleNoiseNode extends ProceduralNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_simple_noise.tooltip");
    }


    /** One octave of bilinearly-interpolated, smoothstep-faded value noise over the integer lattice. */
    private static final String VALUE_NOISE = """
            float kg_valueNoise(vec2 uv) {
                vec2 i = floor(uv);
                vec2 f = fract(uv);
                f = f * f * (3.0 - 2.0 * f);
                float r0 = fract(sin(dot(i + vec2(0.0, 0.0), vec2(12.9898, 78.233))) * 43758.5453);
                float r1 = fract(sin(dot(i + vec2(1.0, 0.0), vec2(12.9898, 78.233))) * 43758.5453);
                float r2 = fract(sin(dot(i + vec2(0.0, 1.0), vec2(12.9898, 78.233))) * 43758.5453);
                float r3 = fract(sin(dot(i + vec2(1.0, 1.0), vec2(12.9898, 78.233))) * 43758.5453);
                return mix(mix(r0, r1, f.x), mix(r2, r3, f.x), f.y);
            }""";

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("scale", TypeHandles.FLOAT).withDefaultValue(10f);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.function("kg_valueNoise", VALUE_NOISE);
        String scale = ctx.input("scale").code();
        ShaderExpr p = ctx.temp(GlslType.VEC2, "(" + uv(ctx).code() + " * " + scale + ")");
        String code = "(kg_valueNoise(" + p.code() + " / 1.0) * 0.125"
                + " + kg_valueNoise(" + p.code() + " / 2.0) * 0.25"
                + " + kg_valueNoise(" + p.code() + " / 4.0) * 0.5)";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }
}
