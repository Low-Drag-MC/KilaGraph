package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The first place a regular expression matches, with its capture groups.
 *
 * <h2>This is the node that turns text back into data</h2>
 * {@code mc_run_command} hands back a line of chat and a number, and until now the graph could only ask
 * whether that line contained a literal substring. A pattern with groups is the way something like
 * "there are 3 of a max of 20 players" becomes two integers.
 *
 * <h2>Ports</h2>
 * {@code match} is the whole matched text, {@code groups} holds the parenthesised captures in order
 * (group 1 first — the whole match is already on its own port, so it is not repeated at index 0), and
 * {@code start}/{@code end} are its position, so {@code string_substring} can cut around it. A group that
 * did not participate in the match comes back as an empty string, which the list cannot tell apart from a
 * group that matched nothing; when that distinction matters, test the surrounding text instead.
 *
 * <p>No match leaves {@code match} empty, {@code groups} empty and {@code start}/{@code end} at -1 —
 * not 0, which is a real index and would read as "matched at the front".
 *
 * <p>Searches from the beginning and stops at the first hit. An invalid pattern reports {@code ok = false}
 * rather than throwing, and beware runaway backtracking on adversarial patterns — see {@link Regex}.
 */
@NodeAttribute(name = "string_find", group = "string", graphTypes = BlueprintGraph.class)
public class FindNode extends AnnotatedNode {
    @InputPort public String in = "";
    @InputPort public String pattern = "";
    @OutputPort public boolean found;
    @OutputPort public String match = "";
    @OutputPort public List<?> groups;
    @OutputPort public int start;
    @OutputPort public int end;
    @OutputPort public boolean ok;

    @Override
    public void evaluate(EvalContext ctx) {
        String s = ctx.getInput("in", String.class, "");
        Pattern p = Regex.compile(ctx.getInput("pattern", String.class, ""));
        ctx.setOutput("ok", p != null);

        Matcher m = p == null ? null : p.matcher(s);
        if (m == null || !m.find()) {
            ctx.setOutput("found", false);
            ctx.setOutput("match", "");
            ctx.setOutput("groups", List.of());
            ctx.setOutput("start", -1);
            ctx.setOutput("end", -1);
            return;
        }

        List<String> groups = new ArrayList<>(m.groupCount());
        for (int i = 1; i <= m.groupCount(); i++) {
            String g = m.group(i);
            groups.add(g == null ? "" : g);
        }
        ctx.setOutput("found", true);
        ctx.setOutput("match", m.group());
        ctx.setOutput("groups", groups);
        ctx.setOutput("start", m.start());
        ctx.setOutput("end", m.end());
    }
}
