package com.lowdragmc.kilagraph.rendertype.nodes.math.vector;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/** {@code a - (dot(a,b)/dot(b,b)) * b}: the rejection of {@code a} from {@code b} (the component of a orthogonal to b). */
@NodeAttribute(name = "rt_reject", group = "rendertype_math/vector", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class RejectionNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("b", RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr a = ctx.inputDynamic("a");
        ShaderExpr b = ctx.inputDynamic("b");
        GlslType r = GlslType.floatVector(Math.max(DynamicBinaryNode.components(a), DynamicBinaryNode.components(b)));
        ShaderExpr at = ctx.temp(r, ctx.convert(a, r).code());
        ShaderExpr bt = ctx.temp(r, ctx.convert(b, r).code());
        String ac = at.code();
        String bc = bt.code();
        ctx.output("out", new ShaderExpr("(" + ac + " - (dot(" + ac + ", " + bc + ") / dot(" + bc + ", " + bc + ")) * " + bc + ")", r));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                out = a - (dot(a, b) / dot(b, b)) * b;""";
    }
}
