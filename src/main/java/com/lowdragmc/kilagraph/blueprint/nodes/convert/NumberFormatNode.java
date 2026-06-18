package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.text.DecimalFormat;

/**
 * Formats a Float through {@link DecimalFormat} with the given {@code pattern}. Invalid patterns
 * yield {@code Float.toString()}.
 */
@NodeAttribute(name = "convert_number_format", group = "convert", graphTypes = BlueprintGraph.class)
public class NumberFormatNode extends AnnotatedNode {

    @Option public String pattern = "#.##";
    @InputPort public float in = 0f;
    @OutputPort public String out;
@Override public void evaluate(EvalContext ctx) {
        float v = ctx.getInput("in", Float.class, 0f);
        String p = ctx.getOption("pattern", String.class, "#.##");
        try {
            ctx.setOutput("out", new DecimalFormat(p).format(v));
        } catch (IllegalArgumentException e) {
            ctx.setOutput("out", Float.toString(v));
        }
    }
}
