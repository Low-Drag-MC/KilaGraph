package com.lowdragmc.kilagraph.rendertype.nodes.math;

import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Base for a two-argument {@code (float, float) -> float} node. Subclasses build the GLSL expression
 * from the two operand expressions via {@link #emit(String, String)} — either an operator
 * ({@code "(" + a + " + " + b + ")"}) or a builtin call ({@code "pow(" + a + ", " + b + ")"}).
 * Float-typed to match the existing math nodes; compose into vectors with the Vec2/3/4 nodes.
 */
public abstract class ShaderBinaryFloatNode extends ShaderNode {

    /** Build the GLSL result expression from the two already-emitted operand expressions. */
    protected abstract String emit(String a, String b);

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", TypeHandles.FLOAT);
        context.addInputPort("b", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr(emit(ctx.input("a").code(), ctx.input("b").code()), GlslType.FLOAT));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
