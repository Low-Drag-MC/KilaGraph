package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Euclidean distance between two entities (0 if either is null). */
@NodeAttribute(name = "mc_entity_distance", group = "mc_entity", graphTypes = BlueprintGraph.class)
public class EntityDistanceNode extends AnnotatedNode {
    @InputPort public Entity a;
    @InputPort public Entity b;
    @OutputPort public double out;

    @Override public Component getDisplayName() { return Component.literal("Entity Distance"); }

    @Override public void evaluate(EvalContext ctx) {
        Entity ea = ctx.getInput("a", Entity.class, null);
        Entity eb = ctx.getInput("b", Entity.class, null);
        ctx.setOutput("out", (ea == null || eb == null) ? 0.0 : (double) ea.distanceTo(eb));
    }
}
