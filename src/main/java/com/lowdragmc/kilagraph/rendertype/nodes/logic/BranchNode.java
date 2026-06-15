package com.lowdragmc.kilagraph.rendertype.nodes.logic;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.DynamicBinaryNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Branch node: {@code out = predicate ? True : False}. This is a data-flow <em>select</em>, not a
 * control-flow branch — <b>both</b> {@code True} and {@code False} are always evaluated (the shader computes
 * both and the GPU picks one), so it never skips work. The True/False operands are the
 * {@linkplain RenderTypeGraphTypes#DYNAMIC dynamic} float-vector type and are broadcast to the wider of the
 * two; the result carries that width. GLSL's {@code bool ? vecN : vecN} selects the whole operand.
 *
 * <p>For real conditional execution or loops ({@code if}/{@code for} that gate or repeat work) use the
 * {@link ExpressionNode} and write the GLSL directly — the same approach Unity takes via its Custom Function
 * node, and the only way to express true control flow in this flat-expression compiler.</p>
 */
@NodeAttribute(name = "rt_branch", group = "rendertype_logic", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class BranchNode extends ShaderNode {

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("predicate", TypeHandles.BOOL);
        context.addInputPort("t", RenderTypeGraphTypes.DYNAMIC);
        context.addInputPort("f", RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr pred = ctx.input("predicate");
        ShaderExpr t = ctx.inputDynamic("t");
        ShaderExpr f = ctx.inputDynamic("f");
        GlslType result = GlslType.floatVector(Math.max(DynamicBinaryNode.components(t), DynamicBinaryNode.components(f)));
        String code = "(" + pred.code() + " ? " + ctx.convert(t, result).code()
                + " : " + ctx.convert(f, result).code() + ")";
        ctx.output("out", new ShaderExpr(code, result));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
