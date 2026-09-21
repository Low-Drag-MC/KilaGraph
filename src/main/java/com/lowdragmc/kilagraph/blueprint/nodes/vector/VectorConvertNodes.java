package com.lowdragmc.kilagraph.blueprint.nodes.vector;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static com.lowdragmc.kilagraph.graph.type.Vectors.at;
import static com.lowdragmc.kilagraph.graph.type.Vectors.components;

/**
 * Width changes, stated rather than implied.
 *
 * <p>The vector pins already accept any width and read a missing component as zero, so these nodes
 * are not needed to make a wire connect. They are needed to make the <b>pin type</b> say what came
 * out: a graph that widens a Vector2 and then feeds a genuinely-3D node wants the pin to read VEC3
 * from that point on, and it wants to choose what the new component holds rather than always
 * getting a zero.</p>
 *
 * <p>Hence {@code fill}: widening to a point in space wants 0, widening a colour to RGBA wants 1,
 * and neither is a sensible default for the other. Narrowing has no such choice and no such port —
 * {@code vector_to_vec2} simply keeps the first two components.</p>
 */
public final class VectorConvertNodes {
    private static final String GROUP = "vector";

    private VectorConvertNodes() {
    }

    /** The first two components, as a VEC2 pin. */
    @NodeAttribute(name = "vector_to_vec2", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToVec2 extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            ctx.addOutputPort("out", KGTypeHandles.VEC2);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            ctx.setOutput("out", (Object) new Vector2f(at(v, 0), at(v, 1)));
        }
    }

    /** Three components, as a VEC3 pin; anything the input did not carry reads {@code fill}. */
    @NodeAttribute(name = "vector_to_vec3", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToVec3 extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            fillPort(ctx);
            ctx.addOutputPort("out", KGTypeHandles.VEC3);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            float fill = ctx.getFloat("fill", 0f);
            ctx.setOutput("out", (Object) new Vector3f(
                    orFill(v, 0, fill), orFill(v, 1, fill), orFill(v, 2, fill)));
        }
    }

    /** Four components, as a VEC4 pin; anything the input did not carry reads {@code fill}. */
    @NodeAttribute(name = "vector_to_vec4", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToVec4 extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            fillPort(ctx);
            ctx.addOutputPort("out", KGTypeHandles.VEC4);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            float fill = ctx.getFloat("fill", 0f);
            ctx.setOutput("out", (Object) new Vector4f(
                    orFill(v, 0, fill), orFill(v, 1, fill), orFill(v, 2, fill), orFill(v, 3, fill)));
        }
    }

    private static void fillPort(IPortDefinitionContext ctx) {
        ctx.addInputPort("fill", Float.class).withDefaultValue(0f);
    }

    /**
     * ⚠️ {@code index < v.length}, not "is it zero": the component the input genuinely carried is
     * kept even when it is zero, and only a component that was never there takes the fill.
     */
    private static float orFill(float[] v, int index, float fill) {
        return index < v.length ? v[index] : fill;
    }
}
