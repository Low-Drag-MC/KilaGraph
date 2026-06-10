package com.lowdragmc.kilagraph.graph.core;

import com.lowdragmc.kilagraph.graph.exec.EvalContext;

/**
 * A node (or block) the {@link com.lowdragmc.kilagraph.graph.exec.GraphExecutor} can pull a value
 * from. Implemented by {@link AnnotatedNode} (annotation-driven data/exec nodes) and by LDLib2
 * {@code BlockNode}-based readers (e.g. the InfoNode field blocks) that aren't AnnotatedNodes but
 * still produce port values.
 *
 * <p>The executor builds an {@link EvalContext} bound to the node's model, calls
 * {@link #evaluate(EvalContext)}, then flushes whatever was written via {@code ctx.setOutput(...)}
 * into the port cache.</p>
 */
public interface IGraphEvaluable {
    void evaluate(EvalContext ctx);
}
