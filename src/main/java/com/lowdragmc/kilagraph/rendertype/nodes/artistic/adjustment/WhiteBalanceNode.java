package com.lowdragmc.kilagraph.rendertype.nodes.artistic.adjustment;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's White Balance: warms/cools ({@code temperature}) and green/magenta-shifts ({@code tint}) the
 * colour by computing a balance in LMS cone-response space (the same math as Unity's grading), both
 * roughly in {@code [-1,1]}, 0 = unchanged.
 */
@NodeAttribute(name = "rt_white_balance", group = "rendertype_artistic/adjustment", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class WhiteBalanceNode extends ArtisticNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("temperature", TypeHandles.FLOAT);
        context.addInputPort("tint", TypeHandles.FLOAT);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String in = ctx.input("in").code();
        String temp = ctx.input("temperature").code();
        String tint = ctx.input("tint").code();
        ShaderExpr t1 = ctx.temp(GlslType.FLOAT, "(" + temp + " * 10.0 / 6.0)");
        ShaderExpr t2 = ctx.temp(GlslType.FLOAT, "(" + tint + " * 10.0 / 6.0)");
        ShaderExpr x = ctx.temp(GlslType.FLOAT,
                "(0.31271 - " + t1.code() + " * (" + t1.code() + " < 0.0 ? 0.1 : 0.05))");
        ShaderExpr y = ctx.temp(GlslType.FLOAT, "(2.87 * " + x.code() + " - 3.0 * " + x.code() + " * "
                + x.code() + " - 0.27509507 + " + t2.code() + " * 0.05)");
        // CIE xy (with Y = 1) -> XYZ -> LMS cone responses (Y term folds to the constant since Y = 1).
        ShaderExpr X = ctx.temp(GlslType.FLOAT, "(" + x.code() + " / " + y.code() + ")");
        ShaderExpr Z = ctx.temp(GlslType.FLOAT, "((1.0 - " + x.code() + " - " + y.code() + ") / " + y.code() + ")");
        ShaderExpr L = ctx.temp(GlslType.FLOAT, "(0.7328 * " + X.code() + " + 0.4296 - 0.1624 * " + Z.code() + ")");
        ShaderExpr M = ctx.temp(GlslType.FLOAT, "(-0.7036 * " + X.code() + " + 1.6975 + 0.0061 * " + Z.code() + ")");
        ShaderExpr S = ctx.temp(GlslType.FLOAT, "(0.0030 * " + X.code() + " + 0.0136 + 0.9834 * " + Z.code() + ")");
        ShaderExpr balance = ctx.temp(GlslType.VEC3, "vec3(0.949237 / " + L.code() + ", 1.03542 / "
                + M.code() + ", 1.08728 / " + S.code() + ")");
        // RGB -> LMS, apply balance, LMS -> RGB (mul as explicit row dot products).
        ShaderExpr lms = ctx.temp(GlslType.VEC3, "(vec3("
                + "dot(vec3(0.390405, 0.549941, 0.00892632), " + in + "), "
                + "dot(vec3(0.0708416, 0.963172, 0.00135775), " + in + "), "
                + "dot(vec3(0.0231082, 0.128021, 0.936245), " + in + ")) * " + balance.code() + ")");
        String l = lms.code();
        ctx.output("out", new ShaderExpr("vec3("
                + "dot(vec3(2.85847, -1.62879, -0.0248910), " + l + "), "
                + "dot(vec3(-0.210182, 1.15820, 0.000324281), " + l + "), "
                + "dot(vec3(-0.0418120, -0.118169, 1.06867), " + l + "))", GlslType.VEC3));
    }
}
