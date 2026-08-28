package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * {@link ToFloatNode} at double precision. Accepts any numeric wire; anything else yields 0.
 *
 * <p>Worth reaching for when the arithmetic downstream is real-valued but the inputs are large — a
 * double keeps 53 bits of mantissa against a float's 24, so a position or a tick count converted
 * here still lands on the number it was given.</p>
 */
@NodeAttribute(name = "convert_to_double", group = "convert", graphTypes = BlueprintGraph.class)
public class ToDoubleNode extends AnnotatedNode {

    @OutputPort public double out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("in", TypeHandles.UNKNOWN);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getDouble("in", 0d));
    }
}
