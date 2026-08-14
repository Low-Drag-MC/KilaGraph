package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Standard if. Pulls {@code cond}, fires one of {@code trueExec}/{@code falseExec}.
 */
@NodeAttribute(name = "exec_branch", group = "exec", graphTypes = BlueprintGraph.class)
public class BranchNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;
    @InputPort public boolean cond = false;
    @ExecOutputPort public ExecutionFlow trueExec;
    @ExecOutputPort public ExecutionFlow falseExec;

    @Override
    public void execute(ExecContext ctx) {
        ctx.flow(ctx.getBool("cond", false) ? "trueExec" : "falseExec");
    }
}
