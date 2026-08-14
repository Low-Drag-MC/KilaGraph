package com.lowdragmc.kilagraph.blueprint.nodes.compare;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.Objects;

/**
 * {@code a != b} — strict {@link Objects#equals} inversion. Both inputs are typed UNKNOWN.
 */
@NodeAttribute(name = "cmp_neq", group = "compare", graphTypes = BlueprintGraph.class)
public class NotEqualsNode extends AnnotatedNode {
    @OutputPort public boolean out;
@Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("a", TypeHandles.UNKNOWN);
        ctx.addInputPort("b", TypeHandles.UNKNOWN);
    }

    @Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", !Objects.equals(ctx.getInputRaw("a"),
                ctx.getInputRaw("b")));
    }
}
