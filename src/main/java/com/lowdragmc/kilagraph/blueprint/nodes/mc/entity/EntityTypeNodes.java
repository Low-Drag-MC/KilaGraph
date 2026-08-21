package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/**
 * What an entity <em>type</em> is, as opposed to what a live entity is doing.
 *
 * <p>The distinction is the point. {@code info_entity} stays a reflective context because a live
 * {@code Entity} has 200 readable members and a genuine long tail — every subclass adds more, and no
 * hand-written node set could keep up. An {@code EntityType} is the opposite: a registry singleton with
 * a handful of static facts, most of its reflective surface being spawning internals
 * ({@code clientTrackingRange}, {@code updateInterval}, {@code trackDeltas}, {@code canSerialize},
 * {@code baseClass}) that mean nothing inside a blueprint.
 *
 * <p>So the type's facts are a node and the entity's state is a context. Getting from one to the other
 * is {@code mc_entity_is_type} in the testing direction and {@code info_entity}'s {@code type} property
 * in the reading direction.
 */
public final class EntityTypeNodes {

    private static final String GROUP = "mc_entity";

    private EntityTypeNodes() {
    }

    /**
     * An entity type's name and hitbox.
     *
     * <p>{@code width} is the full edge of the square footprint, not a radius — {@code EntityType}'s own
     * convention, so a player reads 0.6 by 1.8. These are the numbers to build an {@code AABB} from when
     * a graph wants to know whether an entity of some type would fit somewhere before spawning one.</p>
     */
    @NodeAttribute(name = "mc_entity_type_props", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Props extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_type_props.tooltip");
        }

        @InputPort public EntityType<?> in;
        @OutputPort public Component name;
        @OutputPort public float width;
        @OutputPort public float height;
        @OutputPort public boolean fireImmune;
        @OutputPort public String category;

        @Override
        public void evaluate(EvalContext ctx) {
            EntityType<?> type = ctx.getInput("in", EntityType.class, null);
            if (type == null) type = EntityType.PIG;
            ctx.setOutput("name", (Object) type.getDescription());
            ctx.setOutput("width", type.getWidth());
            ctx.setOutput("height", type.getHeight());
            ctx.setOutput("fireImmune", type.fireImmune());
            // The serialized name ("monster", "creature"), not the enum constant — it is the form that
            // appears in datapacks and in commands, so it is the one a graph will be comparing against.
            ctx.setOutput("category", type.getCategory().getName());
        }
    }
}
