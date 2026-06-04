package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

/**
 * Case conversion via enum {@link Op}. Title casing capitalises the first letter after each
 * whitespace boundary; the rest is lowercased.
 */
@NodeAttribute(name = "string_case", group = "string", graphTypes = BlueprintGraph.class)
public class CaseNode extends AnnotatedNode {

    public enum Op { LOWER, UPPER, TITLE }

    @Option public Op op = Op.LOWER;
    @InputPort public String in = "";
    @OutputPort public String out;

    @Override public Component getDisplayName() { return Component.literal("Case"); }

    @Override public void evaluate(EvalContext ctx) {
        String s = ctx.getInput("in", String.class, "");
        Op o = ctx.getOption("op", Op.class, Op.LOWER);
        String r = switch (o) {
            case UPPER -> s.toUpperCase();
            case TITLE -> titleCase(s);
            default -> s.toLowerCase();
        };
        ctx.setOutput("out", r);
    }

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean nextUpper = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { sb.append(c); nextUpper = true; continue; }
            sb.append(nextUpper ? Character.toUpperCase(c) : Character.toLowerCase(c));
            nextUpper = false;
        }
        return sb.toString();
    }
}
