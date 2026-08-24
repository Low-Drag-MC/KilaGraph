package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Narrowing an entity to what it actually is.
 *
 * <h2>Why the graph needs these and not just a Cast</h2>
 * {@code Player} is a pin type of its own, and {@code Player} is a subclass of {@code Entity}, so a
 * player flows down an entity wire without complaint — the graph's liberal wire rules see a widening
 * and allow it. Going the other way is the problem: an entity <em>might</em> be a player, and the
 * generic {@code Cast} node's answer to "it is not" is to throw, which kills the run.
 *
 * <p>These nodes are the safe form: a value and a flag, so the usual pattern is a Branch on {@code ok}.
 * That is the same shape as every other fallible read in this library — a registry lookup, a property
 * get — rather than a special case a blueprint author has to learn.
 *
 * <p>{@code convert_instanceof} answers the same question without producing the narrowed value; reach
 * for it when the answer is all that is wanted.
 */
public final class EntityCastNodes {

    private static final String GROUP = "mc/entity";

    private EntityCastNodes() {
    }

    /**
     * An entity as a player, when it is one.
     *
     * <p>Covers both sides: a server player and a client player are both {@code Player}, so a graph does
     * not have to care which it got.</p>
     */
    @NodeAttribute(name = "mc_entity_as_player", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class AsPlayer extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_as_player.tooltip");
        }

        @InputPort
        public Entity entity;
        @OutputPort
        public Player out;
        @OutputPort
        public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            Object e = ctx.getInput("entity", Entity.class, null);
            Player player = e instanceof Player p ? p : null;
            ctx.setOutput("out", player);
            ctx.setOutput("ok", player != null);
        }
    }

    /**
     * Whether an entity is alive in the sense that has health at all.
     *
     * <p>There is no {@code LivingEntity} pin type — adding one would mean a third entity type on the
     * wires for no gain, since every node that needs living-entity data already checks. What a graph
     * does want is to know <em>before</em> asking, so that a health read on an armour stand is a
     * deliberate skip rather than a silent zero. Hence a test rather than a cast.</p>
     */
    @NodeAttribute(name = "mc_entity_is_living", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class IsLiving extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_is_living.tooltip");
        }

        @InputPort
        public Entity entity;
        @OutputPort
        public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", ctx.getInput("entity", Entity.class, null) instanceof LivingEntity);
        }
    }
}
