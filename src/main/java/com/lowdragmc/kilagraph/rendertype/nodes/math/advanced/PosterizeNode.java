package com.lowdragmc.kilagraph.rendertype.nodes.math.advanced;

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

/** {@code floor(in * steps) / steps}: quantises {@code in} into {@code steps} bands, component-wise. */
@NodeAttribute(name = "rt_posterize", group = "rendertype_math/advanced", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class PosterizeNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("steps", RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr in = ctx.inputDynamic("in");
        ShaderExpr steps = ctx.inputDynamic("steps");
        GlslType r = GlslType.floatVector(Math.max(DynamicBinaryNode.components(in), DynamicBinaryNode.components(steps)));
        String inc = ctx.convert(in, r).code();
        ShaderExpr st = ctx.temp(r, ctx.convert(steps, r).code());
        ctx.output("out", new ShaderExpr("(floor(" + inc + " * " + st.code() + ") / " + st.code() + ")", r));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
