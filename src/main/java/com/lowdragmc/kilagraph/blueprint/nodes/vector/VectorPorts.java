package com.lowdragmc.kilagraph.blueprint.nodes.vector;

import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.graph.type.Vectors;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * The port shapes of the width-polymorphic vector nodes.
 *
 * <p>These are declared imperatively rather than with {@code @InputPort} because a VECTOR port's
 * handle is not its field's Java type — {@code Vector3f} resolves to {@code VEC3}, which is the
 * right answer for the nodes that really are three-dimensional and the wrong one for these.
 * {@code docs/CONVENTIONS.md} §1 puts exactly this case in the imperative hooks.</p>
 *
 * <p>Every VECTOR input needs {@link Vectors#CODEC} as well as the handle, or its constant
 * serializes through the {@code Vector3f} accessor and a Vector2 comes back as a Vector3. Pairing
 * the two here is the point of this class: a node that forgot the codec would look fine until
 * someone saved a Vector4 literal and reloaded it.</p>
 */
final class VectorPorts {

    private VectorPorts() {
    }

    /** A VECTOR input: any width in, editable as a width picker plus components when unwired. */
    static void in(IPortDefinitionContext ctx, String id) {
        ctx.addInputPort(id, KGTypeHandles.VECTOR).withCodec(Vectors.CODEC);
    }

    /** A VECTOR output: whatever width the node's arithmetic produced. */
    static void out(IPortDefinitionContext ctx, String id) {
        ctx.addOutputPort(id, KGTypeHandles.VECTOR);
    }

    /** {@code in → out}: the shape of every component-wise unary operation. */
    static void unary(IPortDefinitionContext ctx) {
        in(ctx, "in");
        out(ctx, "out");
    }

    /** {@code a, b → out}: the shape of every component-wise binary operation. */
    static void binary(IPortDefinitionContext ctx) {
        in(ctx, "a");
        in(ctx, "b");
        out(ctx, "out");
    }

    /** {@code a, b → out:float}: the shape of every reduction over two vectors. */
    static void reduce(IPortDefinitionContext ctx) {
        in(ctx, "a");
        in(ctx, "b");
        ctx.addOutputPort("out", Float.class);
    }
}
