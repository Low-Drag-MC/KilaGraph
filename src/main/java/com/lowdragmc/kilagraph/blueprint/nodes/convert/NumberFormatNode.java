package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.text.DecimalFormat;

/**
 * Formats a number through {@link DecimalFormat} with the given {@code pattern}. Invalid patterns
 * yield the number's own {@code toString()}.
 *
 * <p>A whole number is formatted <em>as</em> a whole number rather than through a {@code float}, so a
 * tick count or an id past 2^24 formats as the value it was given instead of the nearest float. See
 * {@code NumericLane}.</p>
 */
@NodeAttribute(name = "convert_number_format", group = "convert", graphTypes = BlueprintGraph.class)
public class NumberFormatNode extends AnnotatedNode {

    @Option public String pattern = "#.##";
    @InputPort public float in = 0f;
    @OutputPort public String out;

    @Override
    public void evaluate(EvalContext ctx) {
        Object raw = ctx.getInputRaw("in");
        Number v = raw instanceof Number n ? n : 0f;
        String p = ctx.getOption("pattern", String.class, "#.##");
        try {
            var format = new DecimalFormat(p);
            ctx.setOutput("out", NumericLane.isIntegral(v)
                    ? format.format(v.longValue()) : format.format(v.doubleValue()));
        } catch (IllegalArgumentException e) {
            ctx.setOutput("out", v.toString());
        }
    }
}
