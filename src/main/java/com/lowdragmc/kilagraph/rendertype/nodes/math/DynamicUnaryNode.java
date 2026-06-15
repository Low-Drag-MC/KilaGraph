package com.lowdragmc.kilagraph.rendertype.nodes.math;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Base for a single-argument component-wise node over the {@linkplain RenderTypeGraphTypes#DYNAMIC
 * dynamic} float-vector type ({@code out = f(a)}): one node serves float/vec2/vec3/vec4 and the output
 * matches the input width. Subclasses build the GLSL from the operand via {@link #emit(String)} — a
 * builtin call (see {@link DynamicUnaryFuncNode}) or any width-preserving expression (e.g. {@code "-(" +
 * a + ")"}). The input is read at its natural type via {@link ShaderCompileContext#inputDynamic}.
 */
public abstract class DynamicUnaryNode extends ShaderNode {

    /** Build the GLSL result expression from the operand (output keeps the operand's width). */
    protected abstract String emit(String a);

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr a = ctx.inputDynamic("a");
        ctx.output("out", new ShaderExpr(emit(a.code()), a.type()));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
