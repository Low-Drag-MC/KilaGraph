package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.InfoPropertyBlock;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.mc.McConvert;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The properties of an {@link Entity}, one block each.
 *
 * <p>Every block here is scoped to <b>both</b> {@link EntityInfoNode} and {@link PlayerInfoNode}: a
 * player is an entity, so a Player Info context accepts all of these plus the player-only ones in
 * {@link PlayerInfoBlocks}. The type check in {@code InfoPropertyBlock} makes that safe in the other
 * direction too — a player-only block dropped into an Entity context reads as absent rather than
 * throwing.
 *
 * <h2>Vectors come out as the graph's vector type</h2>
 * Minecraft's positional getters return {@code Vec3}, which the graph deliberately does not carry (see
 * {@code McConvert}). Each block converts at its own boundary, accepting the documented double-to-float
 * precision loss. This is why they are blocks and not a reflective property: a reflected
 * {@code Vec3} getter produced a pin nothing could connect to.
 */
public final class EntityInfoBlocks {

    private static final String GROUP = "mc_entity";

    private EntityInfoBlocks() {
    }

    /** Base for the entity blocks, so each concrete one is only its ports and its read. */
    private abstract static class EntityBlock extends InfoPropertyBlock<Entity> {
        @Override
        protected final Class<Entity> targetClass() {
            return Entity.class;
        }
    }

    // ---- position and motion -----------------------------------------------------------------

    /** Where the entity is — its feet, which is what the game treats as its position. */
    @NodeAttribute(name = "mc_entity_position", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class Position extends EntityBlock {
        @OutputPort public Vector3f value;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("value", (Object) McConvert.toJoml(entity.position()));
        }
    }

    /** Where the entity is looking from — its eyes. The start of a line of sight. */
    @NodeAttribute(name = "mc_entity_eye_position", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class EyePosition extends EntityBlock {
        @OutputPort public Vector3f value;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("value", (Object) McConvert.toJoml(entity.getEyePosition()));
        }
    }

    /** The unit vector the entity is facing. Combine with the eye position to cast a ray. */
    @NodeAttribute(name = "mc_entity_look_direction", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class LookDirection extends EntityBlock {
        @OutputPort public Vector3f value;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("value", (Object) McConvert.toJoml(entity.getLookAngle()));
        }
    }

    /** How fast the entity is moving, per tick, as a vector. */
    @NodeAttribute(name = "mc_entity_velocity", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class Velocity extends EntityBlock {
        @OutputPort public Vector3f value;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("value", (Object) McConvert.toJoml(entity.getDeltaMovement()));
        }
    }

    /** The block the entity is standing in. */
    @NodeAttribute(name = "mc_entity_block_position", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class BlockPosition extends EntityBlock {
        @OutputPort public BlockPos value;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("value", entity.blockPosition());
        }
    }

    /** The entity's hitbox in world space. */
    @NodeAttribute(name = "mc_entity_bounding_box", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class BoundingBox extends EntityBlock {
        @OutputPort public AABB value;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("value", entity.getBoundingBox());
        }
    }

    /**
     * Where the entity is pointed, in degrees.
     *
     * <p>{@code yaw} turns about the vertical axis and {@code pitch} tilts up and down, negative being
     * up — the game's convention, not a mistake. Use {@code mc_entity_look_direction} instead unless the
     * angles themselves are wanted.</p>
     */
    @NodeAttribute(name = "mc_entity_rotation", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class Rotation extends EntityBlock {
        @OutputPort public float yaw;
        @OutputPort public float pitch;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("yaw", entity.getYRot());
            ctx.setOutput("pitch", entity.getXRot());
        }
    }

    // ---- identity ----------------------------------------------------------------------------

    /**
     * How to name this entity.
     *
     * <p>{@code id} is the network id: unique within a running world and <b>not</b> stable across a
     * restart. {@code uuid} is stable and is what to store. {@code name} is for showing to a player.</p>
     */
    @NodeAttribute(name = "mc_entity_identity", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class Identity extends EntityBlock {
        @OutputPort public int id;
        @OutputPort public String uuid;
        @OutputPort public Component name;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("id", entity.getId());
            ctx.setOutput("uuid", entity.getUUID().toString());
            ctx.setOutput("name", (Object) entity.getName());
        }
    }

    /** What kind of entity this is. Feed it to Entity Type Properties, or compare it to a constant. */
    @NodeAttribute(name = "mc_entity_type", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class Type extends EntityBlock {
        @OutputPort public EntityType<?> value;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("value", entity.getType());
        }
    }

    // ---- state -------------------------------------------------------------------------------

    /**
     * The entity's condition, as the flags a graph branches on.
     *
     * <p>Together rather than one block each: anything reacting to an entity asks several of these at
     * once, and a block per flag would mean six blocks stacked in the same context to answer one
     * question.</p>
     */
    @NodeAttribute(name = "mc_entity_state", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class State extends EntityBlock {
        @OutputPort public boolean alive;
        @OutputPort public boolean onGround;
        @OutputPort public boolean inWater;
        @OutputPort public boolean onFire;
        @OutputPort public boolean invisible;
        @OutputPort public boolean sprinting;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("alive", entity.isAlive());
            ctx.setOutput("onGround", entity.onGround());
            ctx.setOutput("inWater", entity.isInWater());
            ctx.setOutput("onFire", entity.isOnFire());
            ctx.setOutput("invisible", entity.isInvisible());
            ctx.setOutput("sprinting", entity.isSprinting());
        }
    }

    /** How long the entity has existed, in ticks, and how far it has fallen. */
    @NodeAttribute(name = "mc_entity_age", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class Age extends EntityBlock {
        @OutputPort public int tickCount;
        @OutputPort public float fallDistance;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            ctx.setOutput("tickCount", entity.tickCount);
            ctx.setOutput("fallDistance", entity.fallDistance);
        }
    }

    /**
     * Health, for the entities that have any.
     *
     * <p>Health lives on {@code LivingEntity}, not {@code Entity} — an arrow or a boat has none. So
     * {@code living} says whether the numbers mean anything, and they read zero when it is false rather
     * than the block refusing to evaluate. This is the property that a reflective context could not
     * reach at all, because it declared its target as {@code Entity}.</p>
     */
    @NodeAttribute(name = "mc_entity_health", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext({EntityInfoNode.class, PlayerInfoNode.class})
    public static class Health extends EntityBlock {
        @OutputPort public float value;
        @OutputPort public float max;
        @OutputPort public boolean living;

        @Override
        protected void read(Entity entity, EvalContext ctx) {
            boolean isLiving = entity instanceof LivingEntity;
            ctx.setOutput("living", isLiving);
            ctx.setOutput("value", isLiving ? ((LivingEntity) entity).getHealth() : 0f);
            ctx.setOutput("max", isLiving ? ((LivingEntity) entity).getMaxHealth() : 0f);
        }
    }
}
