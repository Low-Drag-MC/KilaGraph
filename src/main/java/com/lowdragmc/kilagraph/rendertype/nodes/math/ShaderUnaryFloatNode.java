package com.lowdragmc.kilagraph.rendertype.nodes.math;

import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Base for a single-argument {@code float -> float} GLSL function node ({@code out = func(a)}).
 * Subclasses supply {@link #glslFunc()} (the GLSL builtin name) and a display name; this class
 * wires the {@code a} input and {@code out} output. Float-typed to match the existing math nodes
 * (e.g. {@code ShaderFloatMultiplyNode}); compose into vectors with the Vec2/3/4 nodes.
 */
public abstract class ShaderUnaryFloatNode extends ShaderNode {

    /** The GLSL builtin invoked on the input, e.g. {@code "sin"}. */
    protected abstract String glslFunc();

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr(glslFunc() + "(" + ctx.input("a").code() + ")", GlslType.FLOAT));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
