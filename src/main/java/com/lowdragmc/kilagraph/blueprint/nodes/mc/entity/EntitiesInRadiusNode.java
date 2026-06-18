package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** All entities within {@code radius} blocks of {@code center}. Empty list if level is null. */
@NodeAttribute(name = "mc_entities_in_radius", group = "mc_entity", graphTypes = BlueprintGraph.class)
public class EntitiesInRadiusNode extends AnnotatedNode {
    @InputPort public Level level;
    @InputPort public BlockPos center = BlockPos.ZERO;
    @InputPort public double radius = 8.0;
    @OutputPort public List<Entity> out;

    @Override public Component getDisplayName() { return Component.literal("Entities In Radius"); }

    @Override public void evaluate(EvalContext ctx) {
        Level l = ctx.getInput("level", Level.class, null);
        if (l == null) { ctx.setOutput("out", List.of()); return; }
        BlockPos c = ctx.getInput("center", BlockPos.class, BlockPos.ZERO);
        double r = ctx.getInput("radius", Double.class, 8.0);
        Vec3 cv = c.getCenter();
        AABB box = AABB.ofSize(cv, r * 2, r * 2, r * 2);
        ctx.setOutput("out", l.getEntitiesOfClass(Entity.class, box));
    }
}
