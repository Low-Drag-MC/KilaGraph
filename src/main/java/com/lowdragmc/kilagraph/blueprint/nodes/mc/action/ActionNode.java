package com.lowdragmc.kilagraph.blueprint.nodes.mc.action;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.IThreadSafeNode;

/**
 * The base of every node in this package: something that <b>changes the world</b>.
 *
 * <p>It exists to state one fact in one place — these nodes are not thread-safe
 * ({@link IThreadSafeNode#isThreadSafe()}) — rather than repeating it thirty-four times and
 * discovering later that someone added a thirty-fifth without it. {@link McActions} already owns
 * the other two shared rules of this package (server-only, and an {@code ok} output instead of a
 * throw); this owns the third.
 *
 * <p>Deliberately empty otherwise. It declares no fields, so it cannot affect the reflective port
 * and option scan that {@code AnnotatedNode} runs over a concrete node class.
 *
 * <p>⚠️ <b>"Writes the world" is the test, not "has an exec pin."</b> The two coincide for every
 * node here — all thirty-four carry one — but a pure node can write just as well: a value node
 * that fills a static cache belongs under this class despite having no exec flow, and a node with
 * an exec pin that only sequences reads does not.
 */
public abstract class ActionNode extends AnnotatedNode {

    @Override
    public boolean isThreadSafe() {
        return false;
    }
}
