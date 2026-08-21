package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * "true"/"yes"/"1" → true; anything else → false. Numbers: non-zero → true.
 */
@NodeAttribute(name = "convert_parse_bool", group = "convert", graphTypes = BlueprintGraph.class)
public class ParseBoolNode extends AnnotatedNode {

    @OutputPort public boolean out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("in", TypeHandles.UNKNOWN);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        Object v = ctx.getInputRaw("in");
        boolean result;
        if (v instanceof Boolean b) {
            result = b;
        } else if (v instanceof Number n) {
            result = n.doubleValue() != 0.0;
        } else if (v != null) {
            String s = v.toString().trim().toLowerCase();
            result = s.equals("true") || s.equals("yes") || s.equals("1");
        } else {
            result = false;
        }
        ctx.setOutput("out", result);
    }
}
