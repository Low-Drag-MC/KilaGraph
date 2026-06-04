package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * {@link String#format} with N argument inputs. Malformed pattern → returns the pattern unchanged.
 */
@NodeAttribute(name = "string_format", group = "string", graphTypes = BlueprintGraph.class)
public class FormatNode extends AnnotatedNode {
    @Option public String pattern = "%s";
    @Option public int inputs = 1;
    @OutputPort public String out;

    @Override public Component getDisplayName() { return Component.literal("Format"); }

    @Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(0, optionValue("inputs", Integer.class, inputs));
        for (int i = 1; i <= n; i++) ctx.addInputPort("arg" + i, TypeHandles.UNKNOWN);
    }

    @Override public void evaluate(EvalContext ctx) {
        int n = Math.max(0, ctx.getOption("inputs", Integer.class, inputs));
        Object[] args = new Object[n];
        for (int i = 1; i <= n; i++) args[i - 1] = ctx.getInput("arg" + i).orElse(null);
        String p = ctx.getOption("pattern", String.class, "%s");
        try {
            ctx.setOutput("out", String.format(p, args));
        } catch (java.util.IllegalFormatException e) {
            ctx.setOutput("out", p);
        }
    }
}
