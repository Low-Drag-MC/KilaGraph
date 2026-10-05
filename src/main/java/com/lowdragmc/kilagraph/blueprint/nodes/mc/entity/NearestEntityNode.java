package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.mc.McConvert;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The closest living (or, with {@code livingOnly} off, any) entity within a sphere of {@code radius} around
 * {@code center}, skipping {@code exclude}. {@code distance} is the radius when nothing was found.
 */
@NodeAttribute(name = "mc_nearest_entity", group = "mc/entity", graphTypes = BlueprintGraph.class)
public class NearestEntityNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nearest_entity.tooltip");
    }

    @InputPort public Level level;
    @InputPort public Vector3f center;
    @InputPort public double radius = 8.0;
    @InputPort public Entity exclude;
    @InputPort public boolean livingOnly = true;
    @OutputPort public Entity out;
    @OutputPort public boolean found;
    @OutputPort public double distance;

    @Override
    public void evaluate(EvalContext ctx) {
        Level l = ctx.getInput("level", Level.class, null);
        Vector3f c = ctx.getInput("center", Vector3f.class, null);
        double r = ctx.getDouble("radius", 8.0);
        Entity skip = ctx.getInput("exclude", Entity.class, null);
        boolean living = ctx.getBool("livingOnly", true);
        Entity best = null;
        double bestSq = r * r;
        if (l != null && c != null && r > 0) {
            Vec3 at = McConvert.toVec3(c);
            AABB box = AABB.ofSize(at, r * 2, r * 2, r * 2);
            for (Entity e : l.getEntitiesOfClass(Entity.class, box, e -> e != skip && e.isAlive()
                    && (!living || e instanceof LivingEntity))) {
                double sq = e.distanceToSqr(at);
                if (sq <= bestSq && (best == null || sq < bestSq)) {
                    best = e;
                    bestSq = sq;
                }
            }
        }
        ctx.setOutput("out", best);
        ctx.setOutput("found", best != null);
        ctx.setOutput("distance", best == null ? Math.max(0, r) : Math.sqrt(bestSq));
    }
}
