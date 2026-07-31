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

/** {@code reflect(i, n)}: reflects incident vector {@code i} about (normalized) normal {@code n}. */
@NodeAttribute(name = "rt_reflect", group = "rendertype_math/vector", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ReflectNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("i", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("n", RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr i = ctx.inputDynamic("i");
        ShaderExpr n = ctx.inputDynamic("n");
        GlslType r = GlslType.floatVector(Math.max(DynamicBinaryNode.components(i), DynamicBinaryNode.components(n)));
        ctx.output("out", new ShaderExpr("reflect(" + ctx.convert(i, r).code() + ", " + ctx.convert(n, r).code() + ")", r));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                out = reflect(i, n);""";
    }
}
