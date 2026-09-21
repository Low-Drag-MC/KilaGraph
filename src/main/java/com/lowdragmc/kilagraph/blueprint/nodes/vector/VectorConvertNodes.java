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

public final class VectorConvertNodes {
    private static final String GROUP = "vector";

    private VectorConvertNodes() {
    }

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

    private static float orFill(float[] v, int index, float fill) {
        return index < v.length ? v[index] : fill;
    }
}
