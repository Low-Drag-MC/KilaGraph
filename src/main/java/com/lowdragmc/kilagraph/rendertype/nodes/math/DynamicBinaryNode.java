package com.lowdragmc.kilagraph.rendertype.nodes.math;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

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

    /**
     * What to call the two operands on the canvas and in the docs. The ports keep their {@code a}/{@code b}
     * <em>ids</em> — those are the wire identity, and renaming them would break every saved graph — so this
     * only changes their labels.
     *
     * <p>Override it when the operands are not interchangeable and {@code a}/{@code b} says nothing about
     * which is which, as in {@code step(edge, x)}. Commutative operators (add, multiply, min) read fine
     * as-is and should leave it alone.</p>
     *
     * @return the two labels, in order
     */
    protected String[] portLabels() {
        return new String[]{"a", "b"};
    }

    /** Derived from {@link #emit} itself, so the documented GLSL can never drift from the emitted code. */
    @Override
    public String glslExample() {
        String[] labels = portLabels();
        return "out = " + emit(labels[0], labels[1]) + ";";
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        String[] labels = portLabels();
        defineOperand(context, "a", labels[0]);
        defineOperand(context, "b", labels[1]);
        context.addOutputPort("out", RenderTypeGraphTypes.DYNAMIC);
    }

    /** Adds an operand port, labelling it only when the subclass asked for a name other than its id. */
    private static void defineOperand(IPortDefinitionContext context, String id, String label) {
        var builder = context.addInputPort(id, RenderTypeGraphTypes.DYNAMIC);
        if (!id.equals(label)) {
            builder.withDisplayName(Component.literal(label));
        }
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
