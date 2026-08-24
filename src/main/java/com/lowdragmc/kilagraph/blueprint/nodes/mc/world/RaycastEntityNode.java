package com.lowdragmc.kilagraph.blueprint.nodes.mc.world;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.mc.McConvert;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The first entity a ray passes through.
 *
 * <p>The counterpart of {@code mc_raycast_block}, and together with it the answer to "what is this player
 * looking at" — cast both along the look direction and take whichever hit is nearer. They are separate
 * nodes because the game's own hit detection is: blocks are traced through the world's collision shapes,
 * entities through their bounding boxes, and the two answers are independent.
 *
 * <h2>The ignore input</h2>
 * A ray cast from an entity's own eyes starts <em>inside</em> that entity's hitbox, so without excluding
 * it the first thing hit is always itself. Wiring the entity that is looking into {@code ignore} is
 * almost always what you want; leaving it unset is right only for a ray that comes from somewhere no
 * entity occupies.
 *
 * <p>Only entities that can be interacted with are considered — the same filter the game uses for
 * attacks, so a spectator or a dead entity is passed through rather than blocking the ray.
 */
@NodeAttribute(name = "mc_raycast_entity", group = "mc_world", graphTypes = BlueprintGraph.class)
public class RaycastEntityNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_raycast_entity.tooltip");
    }

    @InputPort public Level level;
    @InputPort public Vector3f from;
    @InputPort public Vector3f to;
    @InputPort public Entity ignore;
    @OutputPort public boolean hit;
    @OutputPort public Entity entity;
    @OutputPort public Vector3f point;

    @Override
    public void evaluate(EvalContext ctx) {
        Level world = ctx.getInput("level", Level.class, null);
        Vector3f fromV = ctx.getInput("from", Vector3f.class, null);
        Vector3f toV = ctx.getInput("to", Vector3f.class, null);
        if (world == null || fromV == null || toV == null) {
            ctx.setOutput("hit", false);
            ctx.setOutput("entity", null);
            ctx.setOutput("point", (Object) new Vector3f());
            return;
        }
        Vec3 start = McConvert.toVec3(fromV);
        Vec3 end = McConvert.toVec3(toV);
        Entity ignore = ctx.getInput("ignore", Entity.class, null);

        // The search box is the ray's own bounds, grown by a block: an entity whose centre is outside the
        // ray's box can still have a hitbox that the ray clips, and the game's own projectile code
        // inflates for the same reason.
        AABB search = new AABB(start, end).inflate(1.0);
        var result = ProjectileUtil.getEntityHitResult(
                world, ignore, start, end, search,
                e -> e != ignore && e.isPickable() && e.isAlive(),
                // 26.1 exposes the hitbox margin; 0 is what the old 6-arg overload passed.
                0.0f);

        boolean got = result != null;
        ctx.setOutput("hit", got);
        ctx.setOutput("entity", got ? result.getEntity() : null);
        ctx.setOutput("point", (Object) (got ? McConvert.toJoml(result.getLocation()) : new Vector3f()));
    }
}
