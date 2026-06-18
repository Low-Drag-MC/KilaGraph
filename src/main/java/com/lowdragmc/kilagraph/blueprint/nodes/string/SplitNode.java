package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Split via literal delimiter (not regex). Empty delimiter → single-element list with the original.
 */
@NodeAttribute(name = "string_split", group = "string", graphTypes = BlueprintGraph.class)
public class SplitNode extends AnnotatedNode {
    @InputPort public String in = "";
    @InputPort public String delimiter = ",";
    @OutputPort public List<?> out;
@Override public void evaluate(EvalContext ctx) {
        String s = ctx.getInput("in", String.class, "");
        String d = ctx.getInput("delimiter", String.class, ",");
        if (d.isEmpty()) { ctx.setOutput("out", List.of(s)); return; }
        // Quote delimiter so it's treated literally.
        ctx.setOutput("out", Arrays.asList(s.split(Pattern.quote(d), -1)));
    }
}
