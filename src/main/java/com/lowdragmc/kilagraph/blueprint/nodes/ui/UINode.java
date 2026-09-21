package com.lowdragmc.kilagraph.blueprint.nodes.ui;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.IThreadSafeNode;

/**
 * The base of every node under {@code ui/}: something that reaches into a live UI tree.
 *
 * <p>It exists to state one fact in one place — these nodes are not thread-safe
 * ({@link IThreadSafeNode#isThreadSafe()}) — instead of repeating it in ninety places and
 * finding out later that the ninety-first was added without it.
 *
 * <p><b>The readers are in here too, and that is not an oversight.</b> A {@code UIElement} tree is
 * owned by the thread that draws it, and it is rebuilt, re-laid-out and re-styled during a frame.
 * Reading a child list or a computed size from somewhere else is a torn read, not a safe one — so
 * unlike the world, where "reads are fine while nothing is writing" holds, here something
 * <i>is</i> writing.
 *
 * <p>Deliberately empty otherwise. It declares no fields, so it cannot affect the reflective port
 * and option scan {@code AnnotatedNode} runs over a concrete node class.
 */
public abstract class UINode extends AnnotatedNode {

    @Override
    public boolean isThreadSafe() {
        return false;
    }
}
