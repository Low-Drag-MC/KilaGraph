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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The closest entity within {@code radius} blocks of {@code center}, if any.
 *
 * <p>The sibling of {@code mc_nearest_player} for everything else in the world: what a script asks
 * when it wants <i>the</i> thing to face, lunge at or follow rather than a list to walk. The centre is
 * the graph's vector, not a block position, because the caller is almost always an entity standing at
 * a fractional position and a block's centre would be off by up to half a block in every direction.
 *
 * <ul>
 *   <li>{@code exclude} is left out of the answer — wire the asking entity here, or the search finds
 *       the asker at distance zero;</li>
 *   <li>{@code livingOnly} keeps the answer to living entities (mobs, players, armour stands) and
 *       drops items, projectiles and vehicles;</li>
 *   <li>the dead and the removed are never returned;</li>
 *   <li>the radius is a sphere, not the box the level is asked for;</li>
 *   <li>{@code distance} is the distance to the entity found, or the radius when nothing was.</li>
 * </ul>
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
            Vec3 at = new Vec3(c.x, c.y, c.z);
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
