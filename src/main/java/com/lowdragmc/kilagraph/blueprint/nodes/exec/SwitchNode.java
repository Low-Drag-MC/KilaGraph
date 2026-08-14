package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortConnectorUI;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * N-way exec selector by integer index. {@code selector} == i (1-based) → {@code casei} fires.
 * Out-of-range selector → {@code defaultExec}.
 *
 * <p>String/Enum-keyed dispatch can come later; the int-selector form covers the common cases
 * (state machines, dispatch tables) without a new TypeHandle option.</p>
 */
@NodeAttribute(name = "exec_switch", group = "exec", graphTypes = BlueprintGraph.class)
public class SwitchNode extends AnnotatedNode {

    @Option public int cases = 3;
    @ExecInputPort public ExecutionFlow in;
    @InputPort public int selector = 1;
    @ExecOutputPort public ExecutionFlow defaultExec;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("cases", Integer.class, cases));
        for (int i = 1; i <= n; i++) {
            ctx.addOutputPort(PortIds.caseId(i), TypeHandles.EXECUTION_FLOW)
                    .withConnectorUI(PortConnectorUI.FLOW);
        }
    }

    @Override
    public void execute(ExecContext ctx) {
        int n = Math.max(1, ctx.getOption("cases", Integer.class, cases));
        int sel = ctx.getInt("selector", 0);
        if (sel >= 1 && sel <= n) ctx.flow(PortIds.caseId(sel));
        else ctx.flow("defaultExec");
    }
}
