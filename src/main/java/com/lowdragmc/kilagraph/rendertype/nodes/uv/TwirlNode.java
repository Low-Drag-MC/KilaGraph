package com.lowdragmc.kilagraph.rendertype.nodes.uv;

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
import org.joml.Vector2f;

/**
 * Unity's Twirl: a swirl distortion whose rotation angle grows with the distance from {@code center}
 * (scaled by {@code strength}), plus an {@code offset}. {@code uv} defaults to the mesh uv.
 */
@NodeAttribute(name = "rt_twirl", group = "rendertype_uv", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TwirlNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("center", RenderTypeGraphTypes.VEC2).withDefaultValue(new Vector2f(0.5f, 0.5f));
        context.addInputPort("strength", TypeHandles.FLOAT).withDefaultValue(10f);
        context.addInputPort("offset", RenderTypeGraphTypes.VEC2);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String center = ctx.input("center").code();
        String offset = ctx.input("offset").code();
        String strength = ctx.input("strength").code();
        ShaderExpr d = ctx.temp(GlslType.VEC2, "(" + ctx.input("uv").code() + " - " + center + ")");
        ShaderExpr a = ctx.temp(GlslType.FLOAT, "(" + strength + " * length(" + d.code() + "))");
        String x = "cos(" + a.code() + ") * " + d.code() + ".x - sin(" + a.code() + ") * " + d.code() + ".y";
        String y = "sin(" + a.code() + ") * " + d.code() + ".x + cos(" + a.code() + ") * " + d.code() + ".y";
        String code = "(vec2(" + x + ", " + y + ") + " + center + " + " + offset + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC2));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
