package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortConnectorUI;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Unreal's {@code MultiGate}: each trigger of {@code in} fires the next output that has not fired
 * yet — {@code out1}, {@code out2}, … in order, or a random one still unused with {@code random}.
 * Once every output has fired, further triggers do nothing, unless {@code loop} starts the round
 * over. {@code startIndex} (1-based; below 1 means "where it left off") picks the first output of
 * a round; {@code reset} forgets which have fired.
 */
@NodeAttribute(name = "exec_multi_gate", group = "exec", graphTypes = BlueprintGraph.class)
public class MultiGateNode extends AnnotatedNode {

    @Option public int outputs = 2;
    @Option public boolean random = false;
    @Option public boolean loop = false;
    @ExecInputPort public ExecutionFlow in;
    @ExecInputPort public ExecutionFlow reset;
    @InputPort public int startIndex = 0;

    private static final String USED = "used";
    private static final String NEXT = "next";

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("outputs", Integer.class, outputs));
        for (int i = 1; i <= n; i++) {
            ctx.addOutputPort(PortIds.out(i), TypeHandles.EXECUTION_FLOW)
                    .withConnectorUI(PortConnectorUI.FLOW);
        }
    }

    @Override
    public void execute(ExecContext ctx) {
        var state = ctx.state();
        int n = Math.max(1, ctx.getOption("outputs", Integer.class, outputs));
        if (ctx.enteredThrough("reset")) {
            state.remove(USED);
            state.remove(NEXT);
            return;
        }
        boolean[] used = state.get(USED) instanceof boolean[] u && u.length == n ? u : new boolean[n];
        int start = ctx.getInt("startIndex", startIndex);
        boolean fresh = !state.containsKey(USED);
        int next = state.get(NEXT) instanceof Integer i ? i : (start >= 1 && start <= n ? start - 1 : 0);
        if (fresh && start >= 1 && start <= n) next = start - 1;

        if (allUsed(used)) {
            if (!ctx.getOption("loop", Boolean.class, loop)) return;
            used = new boolean[n];
            next = start >= 1 && start <= n ? start - 1 : 0;
        }
        int pick;
        if (ctx.getOption("random", Boolean.class, random)) {
            int free = 0;
            for (boolean b : used) if (!b) free++;
            int skip = ThreadLocalRandom.current().nextInt(free);
            pick = 0;
            for (int i = 0; i < n; i++) {
                if (used[i]) continue;
                if (skip-- == 0) { pick = i; break; }
            }
        } else {
            pick = next;
            for (int tries = 0; tries < n && used[pick]; tries++) pick = (pick + 1) % n;
        }
        used[pick] = true;
        state.put(USED, used);
        state.put(NEXT, (pick + 1) % n);
        ctx.flow(PortIds.out(pick + 1));
    }

    private static boolean allUsed(boolean[] used) {
        for (boolean b : used) if (!b) return false;
        return true;
    }
}
