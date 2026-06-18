package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * Convert a numeric value to {@code float}. Input is {@code UNKNOWN} so any {@link Number} wire
 * coerces (e.g. an {@code int} producer); non-numeric values yield 0.
 */
@NodeAttribute(name = "convert_to_float", group = "convert", graphTypes = BlueprintGraph.class)
public class ToFloatNode extends AnnotatedNode {

    @OutputPort public float out;

    @Override public Component getDisplayName() { return Component.literal("To Float"); }

    @Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("in", TypeHandles.UNKNOWN);
    }

    @Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getInput("in", Float.class, 0f));
    }
}
