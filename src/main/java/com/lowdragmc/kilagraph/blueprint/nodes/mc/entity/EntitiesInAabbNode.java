package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** All entities within the inclusive block box {@code [min, max]}. Empty list if level is null. */
@NodeAttribute(name = "mc_entities_in_aabb", group = "mc_entity", graphTypes = BlueprintGraph.class)
public class EntitiesInAabbNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_entities_in_aabb.tooltip");
    }

    @InputPort public Level level;
    @InputPort public BlockPos min = BlockPos.ZERO;
    @InputPort public BlockPos max = BlockPos.ZERO;
    @OutputPort public List<Entity> out;
@Override public void evaluate(EvalContext ctx) {
        Level l = ctx.getInput("level", Level.class, null);
        if (l == null) { ctx.setOutput("out", List.of()); return; }
        BlockPos a = ctx.getInput("min", BlockPos.class, BlockPos.ZERO);
        BlockPos b = ctx.getInput("max", BlockPos.class, BlockPos.ZERO);
        AABB box = new AABB(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
        ctx.setOutput("out", l.getEntitiesOfClass(Entity.class, box));
    }
}
