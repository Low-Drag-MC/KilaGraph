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
 * Single trig function picker. Enum {@link Op} drives LDLib2's {@code EnumAccessor} which renders
 * a dropdown selector automatically.
 */
@NodeAttribute(name = "math_trig", group = "math", graphTypes = BlueprintGraph.class)
public class TrigNode extends AnnotatedNode {

    public enum Op { SIN, COS, TAN, ASIN, ACOS, ATAN }

    @Option public Op op = Op.SIN;
    @InputPort public float in = 0f;
    @OutputPort public float out;

    @Override public Component getDisplayName() { return Component.literal("Trig"); }

    @Override public void evaluate(EvalContext ctx) {
        float v = ctx.getInput("in", Float.class, 0f);
        Op o = ctx.getOption("op", Op.class, Op.SIN);
        double r = switch (o) {
            case COS -> Math.cos(v);
            case TAN -> Math.tan(v);
            case ASIN -> Math.asin(v);
            case ACOS -> Math.acos(v);
            case ATAN -> Math.atan(v);
            default -> Math.sin(v);
        };
        ctx.setOutput("out", (float) r);
    }
}
