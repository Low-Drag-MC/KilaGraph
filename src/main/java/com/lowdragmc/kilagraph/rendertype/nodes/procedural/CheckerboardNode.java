package com.lowdragmc.kilagraph.rendertype.nodes.procedural;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Unity's Checkerboard: a two-colour checker over the uv at {@code frequency} tiles, derivative-aware
 * so the squares stay crisp without aliasing as they shrink in screen space (hence
 * {@link StageAffinity#FRAGMENT_ONLY}). {@code uv} defaults to the mesh uv; outputs the blended colour.
 */
@NodeAttribute(name = "rt_checkerboard", group = "rendertype_procedural", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class CheckerboardNode extends ProceduralNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_checkerboard.tooltip");
    }


    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY; // uses dFdx/dFdy
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("colorA", RenderTypeGraphTypes.VEC3).withDefaultValue(new Vector3f(0.2f, 0.2f, 0.2f));
        context.addInputPort("colorB", RenderTypeGraphTypes.VEC3).withDefaultValue(new Vector3f(0.7f, 0.7f, 0.7f));
        context.addInputPort("frequency", RenderTypeGraphTypes.VEC2).withDefaultValue(new Vector2f(1f, 1f));
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String freq = ctx.input("frequency").code();
        String colorA = ctx.input("colorA").code();
        String colorB = ctx.input("colorB").code();
        ShaderExpr uv = ctx.temp(GlslType.VEC2, "((" + uv(ctx).code() + " + 0.5) * " + freq + ")");
        String u = uv.code();
        ShaderExpr dd = ctx.temp(GlslType.VEC4, "vec4(dFdx(" + u + "), dFdy(" + u + "))");
        String d = dd.code();
        ShaderExpr dl = ctx.temp(GlslType.VEC2,
                "sqrt(vec2(dot(" + d + ".xz, " + d + ".xz), dot(" + d + ".yw, " + d + ".yw)))");
        ShaderExpr dist = ctx.temp(GlslType.VEC2, "(4.0 * abs(fract(" + u + " + 0.25) - 0.5) - 1.0)");
        ShaderExpr scale = ctx.temp(GlslType.VEC2, "(0.35 / " + dl.code() + ")");
        ShaderExpr limit = ctx.temp(GlslType.FLOAT,
                "sqrt(clamp(1.1 - max(" + dl.code() + ".x, " + dl.code() + ".y), 0.0, 1.0))");
        ShaderExpr va = ctx.temp(GlslType.VEC2, "clamp(" + dist.code() + " * " + scale.code() + ", -1.0, 1.0)");
        ShaderExpr alpha = ctx.temp(GlslType.FLOAT, "clamp(0.5 + 0.5 * " + va.code() + ".x * "
                + va.code() + ".y * " + limit.code() + ", 0.0, 1.0)");
        ctx.output("out", new ShaderExpr(
                "mix(" + colorA + ", " + colorB + ", vec3(" + alpha.code() + "))", GlslType.VEC3));
    }

    @Override
    public String glslExample() {
        return """
                // derivative-aware; the gist is
                vec2 t = fract((uv + 0.5) * frequency);
                float c = step(0.5, t.x) != step(0.5, t.y)
                        ? 1.0 : 0.0;
                out = mix(colorA, colorB, c);""";
    }
}
