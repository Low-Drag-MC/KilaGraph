package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces every regex match, with {@code $1} in the replacement standing for a capture group.
 *
 * <p>The group references are what make this more than {@code string_replace}: {@code (\w+)=(\w+)} with
 * {@code $2=$1} swaps the two halves of every assignment in the text. A literal {@code $} or {@code \} in
 * the replacement has to be escaped as {@code \$} / {@code \\}, and getting that wrong is a failure of the
 * replacement rather than of the pattern — both land on {@code ok = false} here, because from the graph's
 * side there is one question ("did this work") and one repair ("fix the text you typed").
 *
 * <p>{@code count} is how many matches were replaced, so a graph can tell "changed nothing" from
 * "matched nothing" without comparing strings. On failure the input passes through unchanged with
 * {@code count = 0}.
 *
 * <p>Invalid patterns and runaway backtracking work as described in {@link Regex}.
 */
@NodeAttribute(name = "string_replace_regex", group = "string", graphTypes = BlueprintGraph.class)
public class ReplaceRegexNode extends AnnotatedNode {
    @InputPort public String in = "";
    @InputPort public String pattern = "";
    @InputPort public String replacement = "";
    @OutputPort public String out = "";
    @OutputPort public int count;
    @OutputPort public boolean ok;

    @Override
    public void evaluate(EvalContext ctx) {
        String s = ctx.getInput("in", String.class, "");
        String repl = ctx.getInput("replacement", String.class, "");
        Pattern p = Regex.compile(ctx.getInput("pattern", String.class, ""));
        if (p == null) {
            fail(ctx, s);
            return;
        }

        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        int replaced = 0;
        try {
            while (m.find()) {
                m.appendReplacement(sb, repl);
                replaced++;
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            // A malformed replacement, or a $n naming a group the pattern does not have. Both are typos in
            // a text field, so they read as a failed node rather than as a crashed graph.
            fail(ctx, s);
            return;
        }
        m.appendTail(sb);

        ctx.setOutput("out", sb.toString());
        ctx.setOutput("count", replaced);
        ctx.setOutput("ok", true);
    }

    private static void fail(EvalContext ctx, String original) {
        ctx.setOutput("out", original);
        ctx.setOutput("count", 0);
        ctx.setOutput("ok", false);
    }
}
