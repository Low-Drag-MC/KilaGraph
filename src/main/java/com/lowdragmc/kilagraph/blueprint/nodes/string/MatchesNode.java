package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.regex.Pattern;

/**
 * Whether a regular expression matches a string <b>in its entirety</b>.
 *
 * <p>Whole-string, not "contains a match" — {@code \d+} is true for {@code "42"} and false for
 * {@code "x42"}. That is Java's {@link String#matches} rule and the one people mean when they say
 * "matches", but it catches everyone once, so anchor-free patterns need {@code .*} on both ends. For
 * partial matching use {@code string_find}, which is also what gives back what was matched.
 *
 * <p>An invalid pattern is {@code out = false, ok = false} rather than a throw: see {@link Regex}.
 */
@NodeAttribute(name = "string_matches", group = "string", graphTypes = BlueprintGraph.class)
public class MatchesNode extends AnnotatedNode {
    @InputPort public String in = "";
    @InputPort public String pattern = "";
    @OutputPort public boolean out;
    @OutputPort public boolean ok;

    @Override
    public void evaluate(EvalContext ctx) {
        String s = ctx.getInput("in", String.class, "");
        Pattern p = Regex.compile(ctx.getInput("pattern", String.class, ""));
        ctx.setOutput("out", p != null && p.matcher(s).matches());
        ctx.setOutput("ok", p != null);
    }
}
