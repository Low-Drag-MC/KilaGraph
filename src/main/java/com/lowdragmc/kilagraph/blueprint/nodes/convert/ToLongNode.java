package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * {@link ToIntNode} without the 32-bit ceiling: the value as a {@code long}.
 *
 * <p>The one to reach for when the number is a tick, a counter, an id or a packed position — all the
 * things a world produces that do not fit an {@code int} and do not survive a {@code float}. It is
 * also how a whole number gets <em>onto</em> a wire deliberately: the math nodes pick their lane from
 * what reaches them (see {@link NumericLane}), and this node is the way to say "that one is whole"
 * about a value whose producer did not.</p>
 */
@NodeAttribute(name = "convert_to_long", group = "convert", graphTypes = BlueprintGraph.class)
public class ToLongNode extends AnnotatedNode {

    @Option public ToIntNode.Op op = ToIntNode.Op.TRUNC;
    @OutputPort public long out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("in", TypeHandles.UNKNOWN);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ToIntNode.whole(ctx, "in", ctx.getOption("op", ToIntNode.Op.class,
                ToIntNode.Op.TRUNC)));
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "op".equals(optionId) ? List.of("TRUNC", "FLOOR", "CEIL", "ROUND") : List.of();
    }
}
