package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * Clamping substring: bounds outside [0, len] are clipped. start > end → empty.
 */
@NodeAttribute(name = "string_substring", group = "string", graphTypes = BlueprintGraph.class)
public class SubstringNode extends AnnotatedNode {
    @InputPort public String in = "";
    @InputPort public int start = 0;
    @InputPort public int end = 0;
    @OutputPort public String out;

    @Override
    public void evaluate(EvalContext ctx) {
        String s = ctx.getInput("in", String.class, "");
        int st = Math.max(0, Math.min(s.length(), ctx.getInt("start", 0)));
        int en = Math.max(0, Math.min(s.length(), ctx.getInt("end", s.length())));
        ctx.setOutput("out", st > en ? "" : s.substring(st, en));
    }
}
