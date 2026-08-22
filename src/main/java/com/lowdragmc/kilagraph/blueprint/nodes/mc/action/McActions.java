package com.lowdragmc.kilagraph.blueprint.nodes.mc.action;

import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Shared rules for every node that changes the world.
 *
 * <h2>Server only, and it says so rather than pretending</h2>
 * A client level holds an approximation: its entity list is incomplete, its block entities may be stubs,
 * and anything written to it is overwritten by the next server packet. So every action here refuses to
 * run on one, reports {@code ok = false}, and still flows to {@code next}.
 *
 * <p>Refusing rather than throwing is the deliberate part. A blueprint is authored once and may be
 * evaluated on either side — by a screen preview, by a client-side renderer, by the server that owns the
 * world — and an action that threw on the client would make the same graph crash in one place and work in
 * another. Flowing on with {@code ok = false} lets a graph branch on the result if it cares and ignore it
 * if it does not.
 *
 * <h2>Every action has an {@code ok} output</h2>
 * Actions fail for ordinary reasons: a position outside the world, an unknown sound id, a full inventory,
 * an entity that has already been removed. None of those is exceptional, and a graph that wants to know
 * should be able to ask. {@code ok} is true only when the action actually happened.
 */
public final class McActions {

    private McActions() {
    }

    /**
     * The level on {@code level}, if it is a server level that can be written to.
     *
     * <p>Returns null for a missing input and for a client level alike — both mean "do not act", and the
     * caller reports the same {@code ok = false} either way.</p>
     */
    @Nullable
    public static ServerLevel writableLevel(ExecContext ctx) {
        Level level = ctx.getInput("level", Level.class, null);
        return level instanceof ServerLevel server && !server.isClientSide ? server : null;
    }

    /** Report the outcome and continue. Every action ends this way, including the ones that failed. */
    public static void done(ExecContext ctx, boolean ok) {
        ctx.setOutput("ok", ok);
        ctx.flow("next");
    }
}
