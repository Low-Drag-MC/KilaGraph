package com.lowdragmc.kilagraph.blueprint.nodes.compare;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "cmp_ge", group = "compare", graphTypes = BlueprintGraph.class)
public class GreaterEqualNode extends AnnotatedNode {
    @InputPort  public float a = 0f;
    @InputPort  public float b = 0f;
    @OutputPort public boolean out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getFloat("a", 0f) >= ctx.getFloat("b", 0f));
    }
}
