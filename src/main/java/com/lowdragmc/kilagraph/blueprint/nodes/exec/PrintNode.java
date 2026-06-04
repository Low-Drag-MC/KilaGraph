package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Logs {@code value} through the KilaGraph logger and stashes the most recent value under
 * {@code state()["last"]} so tests (and future debugger hooks) can read it back without parsing
 * the log stream.
 */
@NodeAttribute(name = "exec_print", group = "exec", graphTypes = BlueprintGraph.class)
public class PrintNode extends AnnotatedNode {
    private static final Logger LOGGER = LogUtils.getLogger();

    @ExecInputPort public ExecutionFlow trigger;
    @ExecOutputPort public ExecutionFlow next;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", TypeHandles.UNKNOWN);
    }

    @Override
    public void execute(ExecContext ctx) {
        Object v = ctx.getInput("value").orElse(null);
        LOGGER.info("[KGPrint] {}", v);
        ctx.state().put("last", v);
        ctx.flow("next");
    }
}
