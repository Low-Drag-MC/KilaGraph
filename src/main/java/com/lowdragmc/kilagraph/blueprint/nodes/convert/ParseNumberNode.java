package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Reads a number out of text.
 *
 * <p>The text decides the width, so nothing is thrown away on the way in: digits alone parse as a
 * whole number and keep all of themselves, and anything with a point or an exponent parses at double
 * precision. It used to be {@code Float.parseFloat} for both, which meant a pasted id or tick count
 * arrived as the nearest float — a different number, silently. Text that is not a number still yields
 * 0 rather than failing the run.</p>
 *
 * <p>A value that is <em>already</em> a number passes through unchanged, rather than being narrowed
 * to a float first.</p>
 */
@NodeAttribute(name = "convert_parse_number", group = "convert", graphTypes = BlueprintGraph.class)
public class ParseNumberNode extends AnnotatedNode {

    @OutputPort public float out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("in", TypeHandles.UNKNOWN);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        Object v = ctx.getInputRaw("in");
        if (v instanceof Number n) {
            ctx.setOutput("out", n);
            return;
        }
        if (v == null) {
            ctx.setOutput("out", 0f);
            return;
        }
        String s = v.toString().trim();
        try {
            // Whole first: parseLong rejects a point, an exponent and anything that overflows, so
            // everything it does not take falls through to the wider parse rather than being lost.
            ctx.setOutput("out", Long.parseLong(s));
        } catch (NumberFormatException notWhole) {
            try {
                ctx.setOutput("out", Double.parseDouble(s));
            } catch (NumberFormatException notANumber) {
                ctx.setOutput("out", 0f);
            }
        }
    }
}
