package com.lowdragmc.kilagraph.rendertype.nodes.math;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Base for a two-argument component-wise node over the {@linkplain RenderTypeGraphTypes#DYNAMIC dynamic}
 * float-vector type. Both operands are read at their natural type and broadcast to the wider of the two
 * (so {@code float * vec3 → vec3}); the result carries that inferred width. Subclasses build the GLSL via
 * {@link #emit(String, String)} — an operator ({@code "(" + a + " + " + b + ")"}) or a vecN-overloaded
 * builtin ({@code "pow(" + a + ", " + b + ")"}; see {@link DynamicBinaryFuncNode}).
 */
public abstract class DynamicBinaryNode extends ShaderNode {

    /** Build the GLSL result expression from the two operands (already cast to the common width). */
    protected abstract String emit(String a, String b);

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
        GlslType result = GlslType.floatVector(Math.max(components(a), components(b)));
        String ac = ctx.convert(a, result).code();
        String bc = ctx.convert(b, result).code();
        ctx.output("out", new ShaderExpr(emit(ac, bc), result));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    /** The float-component count of an expression (scalars and non-vectors count as 1). */
    public static int components(ShaderExpr e) {
        GlslType t = e.type();
        return t != null && t.isFloatVector() ? t.components() : 1;
    }
}
