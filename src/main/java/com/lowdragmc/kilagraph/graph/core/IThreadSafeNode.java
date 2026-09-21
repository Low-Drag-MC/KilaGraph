package com.lowdragmc.kilagraph.graph.core;

/**
 * A node's answer to "may a host run me off the game thread?".
 *
 * <p><b>An interface because there is no one base class.</b> KilaGraph's nodes come in three
 * families that meet only at LDLib2's {@code Node}: {@link AnnotatedNode},
 * {@link AnnotatedBlockNode} (the info blocks) and {@code ContextNode}. Asking
 * {@code instanceof AnnotatedNode} would read two families' silence as a yes. It is not on
 * LDLib2's {@code Node} because this is about <b>execution</b>, which is KilaGraph's half.
 *
 * <p><b>Default true</b>, because almost every node here is arithmetic or a read-only query and
 * a host only parallelises where nothing is writing. Unsafe is the exception, so the exception is
 * what gets written down.
 *
 * <p><b>Override to false when the node writes something the game thread also touches</b>: the
 * world, an entity, a block entity, a container, a UI element, a static cache, a shared random
 * source. Mutating a value the graph itself produced — a tag from {@code mc_entity_nbt}, a list —
 * does not count.
 *
 * <p>⚠️ Not the same question as "is the node instance stateless" (every node here is; state lives
 * in the {@code EvalContext}). A stateless node can still summon an entity.
 *
 * <p>⚠️ An exec pin is not the test either: {@code mc_loot_table_roll} has none and advances the
 * level's random source, while an exec pin may only be sequencing reads.
 *
 * <p><b>A host should downgrade, not refuse.</b> Unreal asks this per node when compiling an
 * Animation Blueprint ({@code FAnimNode_Base::CanUpdateInWorkerThread},
 * {@code AnimBlueprintCompiler.cpp:1053}) and derives the graph's threading flag from the answers
 * ({@code :1157}) — then warns, naming the node.
 */
public interface IThreadSafeNode {

    default boolean isThreadSafe() {
        return true;
    }
}
