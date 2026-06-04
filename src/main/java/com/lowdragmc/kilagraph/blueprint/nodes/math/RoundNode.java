package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

/**
 * Rounding picker. Enum {@link Op} → {@code EnumAccessor} dropdown.
 */
@NodeAttribute(name = "math_round", group = "math", graphTypes = BlueprintGraph.class)
public class RoundNode extends AnnotatedNode {

    public enum Op { ROUND, FLOOR, CEIL, TRUNC }

    @Option public Op op = Op.ROUND;
    @InputPort public float in = 0f;
    @OutputPort public float out;

    @Override public Component getDisplayName() { return Component.literal("Round"); }

    @Override public void evaluate(EvalContext ctx) {
        float v = ctx.getInput("in", Float.class, 0f);
        Op o = ctx.getOption("op", Op.class, Op.ROUND);
        float r = switch (o) {
            case FLOOR -> (float) Math.floor(v);
            case CEIL -> (float) Math.ceil(v);
            case TRUNC -> (float) (long) v;
            default -> Math.round(v);
        };
        ctx.setOutput("out", r);
    }
}
