package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.List;

/**
 * Convert between degrees and radians. Enum {@link Op} → {@code EnumAccessor} dropdown.
 */
@NodeAttribute(name = "math_angle_convert", group = "math", graphTypes = BlueprintGraph.class)
public class AngleConvertNode extends AnnotatedNode {

    public enum Op { DEG_TO_RAD, RAD_TO_DEG }

    @Option public Op op = Op.DEG_TO_RAD;
    @InputPort public float in = 0f;
    @OutputPort public float out;
@Override public void evaluate(EvalContext ctx) {
        float v = ctx.getFloat("in", 0f);
        Op o = ctx.getOption("op", Op.class, Op.DEG_TO_RAD);
        float r = switch (o) {
            case RAD_TO_DEG -> (float) Math.toDegrees(v);
            default -> (float) Math.toRadians(v);
        };
        ctx.setOutput("out", r);
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "op".equals(optionId) ? List.of("DEG_TO_RAD", "RAD_TO_DEG") : List.of();
    }
}
