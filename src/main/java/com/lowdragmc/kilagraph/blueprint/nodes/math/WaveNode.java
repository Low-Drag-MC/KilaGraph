package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.List;

@NodeAttribute(name = "math_wave", group = "math", graphTypes = BlueprintGraph.class)
public class WaveNode extends AnnotatedNode {
    public enum Op { SINE, TRIANGLE, SAWTOOTH, SQUARE }

    @Option public Op op = Op.SINE;
    @InputPort public float in = 0f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        float t = ctx.getFloat("in", 0f);
        Op raw = ctx.getOption("op", Op.class, Op.SINE);
        Op o = raw == null ? Op.SINE : raw;
        float r = switch (o) {
            case TRIANGLE -> 2f * Math.abs(2f * (t - (float) Math.floor(t + 0.5f))) - 1f;
            case SAWTOOTH -> 2f * (t - (float) Math.floor(0.5f + t));
            case SQUARE -> 1f - 2f * Math.round(t - (float) Math.floor(t));
            case SINE -> (float) Math.sin(t * 2d * Math.PI);
        };
        ctx.setOutput("out", r);
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "op".equals(optionId)
                ? List.of("SINE", "TRIANGLE", "SAWTOOTH", "SQUARE")
                : List.of();
    }
}
