package com.lowdragmc.kilagraph.blueprint.nodes.compare;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * {@code a != b}, the inverse of {@code Equals}. Both inputs are typed UNKNOWN unless a type is picked
 * ({@link ComparedType}).
 *
 * <p>Numbers compare by value, everything else by {@code Objects.equals} — see
 * {@link NumericLane#valuesEqual} for why that distinction has to exist.</p>
 */
@NodeAttribute(name = "cmp_neq", group = "compare", graphTypes = BlueprintGraph.class)
public class NotEqualsNode extends AnnotatedNode {
    @OutputPort public boolean out;

    @Override
    protected void onDefineExtraOptions(IOptionDefinitionContext ctx) {
        ComparedType.define(ctx, this);
    }

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        TypeHandle type = ComparedType.of(optionValue(ComparedType.OPTION, String.class, ""));
        if (type == null) {
            ctx.addInputPort("a", TypeHandles.UNKNOWN);
            ctx.addInputPort("b", TypeHandles.UNKNOWN);
        } else {
            ComparedType.addInput(ctx, this, "a", type);
            ComparedType.addInput(ctx, this, "b", type);
        }
    }

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", !NumericLane.valuesEqual(ctx.getInputRaw("a"),
                ctx.getInputRaw("b")));
    }
}
