package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "convert_parse_number", group = "convert", graphTypes = BlueprintGraph.class)
public class ParseNumberNode extends AnnotatedNode {

    @OutputPort public float out;
@Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("in", TypeHandles.UNKNOWN);
    }

    @Override public void evaluate(EvalContext ctx) {
        Object v = ctx.getInputRaw("in");
        float result = 0f;
        if (v instanceof Number n) {
            result = n.floatValue();
        } else if (v != null) {
            try { result = Float.parseFloat(v.toString().trim()); } catch (NumberFormatException ignored) {}
        }
        ctx.setOutput("out", result);
    }
}
