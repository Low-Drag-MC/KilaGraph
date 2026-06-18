package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Convert a numeric value to {@code int}, choosing how to drop the fractional part. Input is
 * {@code UNKNOWN} so any {@link Number} wire coerces; non-numeric values yield 0.
 */
@NodeAttribute(name = "convert_to_int", group = "convert", graphTypes = BlueprintGraph.class)
public class ToIntNode extends AnnotatedNode {

    public enum Op { TRUNC, FLOOR, CEIL, ROUND }

    @Option public Op op = Op.TRUNC;
    @OutputPort public int out;
@Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("in", TypeHandles.UNKNOWN);
    }

    @Override public void evaluate(EvalContext ctx) {
        float v = ctx.getInput("in", Float.class, 0f);
        Op o = ctx.getOption("op", Op.class, Op.TRUNC);
        int r = switch (o) {
            case FLOOR -> (int) Math.floor(v);
            case CEIL -> (int) Math.ceil(v);
            case ROUND -> Math.round(v);
            default -> (int) v;
        };
        ctx.setOutput("out", r);
    }
}
