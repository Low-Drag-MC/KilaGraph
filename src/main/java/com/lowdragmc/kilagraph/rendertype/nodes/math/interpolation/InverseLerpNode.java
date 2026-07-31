package com.lowdragmc.kilagraph.rendertype.nodes.math.interpolation;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryNode;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/** {@code (t - a) / (b - a)}: the inverse of lerp — the parameter that lerp(a,b,·) maps to {@code t}. */
@NodeAttribute(name = "rt_inverse_lerp", group = "rendertype_math/interpolation", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class InverseLerpNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("b", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("t", RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr a = ctx.inputDynamic("a");
        ShaderExpr b = ctx.inputDynamic("b");
        ShaderExpr t = ctx.inputDynamic("t");
        int comps = Math.max(DynamicBinaryNode.components(a),
                Math.max(DynamicBinaryNode.components(b), DynamicBinaryNode.components(t)));
        GlslType r = GlslType.floatVector(comps);
        String ac = ctx.convert(a, r).code();
        String bc = ctx.convert(b, r).code();
        String tc = ctx.convert(t, r).code();
        ctx.output("out", new ShaderExpr("((" + tc + " - " + ac + ") / (" + bc + " - " + ac + "))", r));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                out = (t - a) / (b - a);""";
    }
}
