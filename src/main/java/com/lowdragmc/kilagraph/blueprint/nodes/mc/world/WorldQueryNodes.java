package com.lowdragmc.kilagraph.blueprint.nodes.mc.world;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.mc.McConvert;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;

/**
 * Read-only questions about the world.
 *
 * <p>Every node here takes a {@code level} input port, wire-only, exactly as the pre-existing
 * {@code mc_get_block*} nodes do — the executor is never given a world, so a graph that needs one says
 * so and the host supplies it through a variable. A null level yields the neutral answer rather than
 * throwing, so a graph authored in the editor (where there is no world) still evaluates.
 *
 * <p>Nothing here writes. Placing blocks, spawning entities and playing sounds are exec-flow nodes and
 * belong in a later batch; a read-only query has no ordering to respect and composes as pure data.
 */
public final class WorldQueryNodes {

    private static final String GROUP = "mc_world";

    private WorldQueryNodes() {
    }

    /**
     * The biome at a position, as its id.
     *
     * <p>An id rather than a {@code Biome} value because biomes live in a datapack registry: there is no
     * {@code BuiltInRegistries.BIOME} to resolve against at class-init time, so a {@code Biome} pin type
     * would need a codec that carries a registry with it. The id is what a graph compares anyway.</p>
     */
    @NodeAttribute(name = "mc_get_biome_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BiomeId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_get_biome_id.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public ResourceLocation out;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            if (l == null) {
                ctx.setOutput("out", null);
                return;
            }
            // unwrapKey rather than the registry: a Holder from a live world already knows its key, and
            // going back through registryAccess would be a lookup that can fail for no reason.
            ctx.setOutput("out", l.getBiome(pos(ctx)).unwrapKey()
                    .map(ResourceKey::location).orElse(null));
        }
    }

    /** The fluid at a position — water for a water block and for anything waterlogged. */
    @NodeAttribute(name = "mc_get_fluid_state", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetFluid extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_get_fluid_state.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public Fluid out;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            ctx.setOutput("out", l == null ? Fluids.EMPTY : l.getFluidState(pos(ctx)).getType());
        }
    }

    /** Block light and sky light at a position, each 0–15. */
    @NodeAttribute(name = "mc_get_light", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetLight extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_get_light.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public int block;
        @OutputPort public int sky;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            BlockPos p = pos(ctx);
            ctx.setOutput("block", l == null ? 0 : l.getBrightness(LightLayer.BLOCK, p));
            ctx.setOutput("sky", l == null ? 0 : l.getBrightness(LightLayer.SKY, p));
        }
    }

    /**
     * The surface height at a column, by heightmap.
     *
     * <p>Which heightmap matters: {@code WORLD_SURFACE} stops at the first non-air block including
     * leaves, {@code MOTION_BLOCKING} at the first block that would stop a falling entity — the one to
     * use for "where would something land".</p>
     */
    // heightmap stays an option for the same reason as Direction.Plane: Heightmap.Types is not a pin
    // type and would only ever be one here.
    @NodeAttribute(name = "mc_get_height", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetHeight extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_get_height.tooltip");
        }

        @Option public Heightmap.Types heightmap = Heightmap.Types.MOTION_BLOCKING;
        @InputPort public Level level;
        @InputPort public int x = 0;
        @InputPort public int z = 0;
        @OutputPort public int out;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            Heightmap.Types type = ctx.getOption("heightmap", Heightmap.Types.class,
                    Heightmap.Types.MOTION_BLOCKING);
            ctx.setOutput("out", l == null
                    ? 0
                    : l.getHeight(type == null ? Heightmap.Types.MOTION_BLOCKING : type,
                            ctx.getInt("x", 0), ctx.getInt("z", 0)));
        }
    }

    @NodeAttribute(name = "mc_can_see_sky", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class CanSeeSky extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_can_see_sky.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            ctx.setOutput("out", l != null && l.canSeeSky(pos(ctx)));
        }
    }

    /** The world's dimension id — {@code minecraft:overworld} and friends. */
    @NodeAttribute(name = "mc_dimension_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class DimensionId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_dimension_id.tooltip");
        }

        @InputPort public Level level;
        @OutputPort public ResourceLocation out;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            ctx.setOutput("out", l == null ? null : l.dimension().location());
        }
    }

    /**
     * Casts a ray through blocks and reports what it hit.
     *
     * <p>Returns the pieces rather than a hit-result type: the block, the face, and the exact point.
     * A {@code BlockHitResult} pin type would need a codec and a widget to carry something that is only
     * ever produced here and consumed immediately.</p>
     *
     * <p>{@code CollisionContext.empty()} rather than an entity's context, since there is no entity
     * doing the looking — the ray is the graph's, so block shapes are evaluated without a rider.</p>
     */
    @NodeAttribute(name = "mc_raycast_block", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RaycastBlock extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_raycast_block.tooltip");
        }

        @InputPort public Level level;
        @InputPort public Vector3f from;
        @InputPort public Vector3f to;
        @OutputPort public boolean hit;
        @OutputPort public BlockPos pos;
        @OutputPort public Direction face;
        @OutputPort public Vector3f point;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            if (l == null) {
                ctx.setOutput("hit", false);
                ctx.setOutput("pos", BlockPos.ZERO);
                ctx.setOutput("face", Direction.NORTH);
                ctx.setOutput("point", (Object) new Vector3f());
                return;
            }
            var result = l.clip(new ClipContext(
                    McConvert.toVec3(ctx.getInput("from", Vector3f.class, null)),
                    McConvert.toVec3(ctx.getInput("to", Vector3f.class, null)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            boolean got = result.getType() == HitResult.Type.BLOCK;
            ctx.setOutput("hit", got);
            ctx.setOutput("pos", got ? result.getBlockPos() : BlockPos.ZERO);
            ctx.setOutput("face", got ? result.getDirection() : Direction.NORTH);
            ctx.setOutput("point", (Object) McConvert.toJoml(result.getLocation()));
        }
    }

    /**
     * Every entity inside a box.
     *
     * <p>The {@code AABB}-typed counterpart of the pre-existing {@code mc_entities_in_aabb}, which
     * despite its name takes two {@code BlockPos} corners. That node predates the {@code AABB} pin type
     * and keeps its registry name so saved graphs stay valid; this one is what to reach for now.</p>
     */
    @NodeAttribute(name = "mc_entities_in_box", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EntitiesInBox extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entities_in_box.tooltip");
        }

        @InputPort public Level level;
        @InputPort public AABB box;
        @OutputPort public List<?> out;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            AABB box = ctx.getInput("box", AABB.class, null);
            ctx.setOutput("out", l == null || box == null
                    ? List.of()
                    : new ArrayList<>(l.getEntitiesOfClass(Entity.class, box)));
        }
    }

    /** Every entity of one type inside a box. */
    @NodeAttribute(name = "mc_entities_of_type_in_box", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EntitiesOfTypeInBox extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entities_of_type_in_box.tooltip");
        }

        @InputPort public Level level;
        @InputPort public AABB box;
        @InputPort public EntityType<?> type;
        @OutputPort public List<?> out;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            AABB box = ctx.getInput("box", AABB.class, null);
            EntityType<?> type = ctx.getInput("type", EntityType.class, null);
            if (l == null || box == null || type == null) {
                ctx.setOutput("out", List.of());
                return;
            }
            ctx.setOutput("out", new ArrayList<>(
                    l.getEntitiesOfClass(Entity.class, box, e -> e.getType() == type)));
        }
    }

    /** The closest player within a radius, if any. */
    @NodeAttribute(name = "mc_nearest_player", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class NearestPlayer extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_nearest_player.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public double radius = 16.0;
        @OutputPort public Player out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            Player p = null;
            if (l != null) {
                var center = pos(ctx).getCenter();
                // false = do not include creative-mode players in the search... it means the opposite:
                // the flag asks whether creative players count. Include them; a blueprint asking "who is
                // near" means everyone.
                p = l.getNearestPlayer(center.x, center.y, center.z, ctx.getDouble("radius", 16.0), false);
            }
            ctx.setOutput("out", p);
            ctx.setOutput("found", p != null);
        }
    }

    /**
     * Whether a position's chunk is loaded, and whether the position exists at all.
     *
     * <p>Everything else in this file returns a neutral answer for an unloaded chunk — air, light zero, no
     * entities — which is indistinguishable from a real one. This is the node that tells the difference, and
     * a graph scanning far-off positions should check it before believing what it read.
     *
     * <p>Deliberately does not load the chunk. Asking a question must not change the world, and a graph
     * sweeping a region would otherwise drag the whole thing into memory a block at a time.
     *
     * <p>{@code inBounds} separates the two ways this can be false. Above the build height or past the
     * world's horizontal limit there is nothing to load and never will be, so both outputs are false and the
     * answer is "look somewhere else". A position that is in bounds but not loaded is "try again later".
     * There is no fourth combination: a position out of bounds is never readable.</p>
     */
    @NodeAttribute(name = "mc_is_chunk_loaded", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class IsChunkLoaded extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_is_chunk_loaded.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public boolean out;
        @OutputPort public boolean inBounds;

        @Override
        public void evaluate(EvalContext ctx) {
            Level l = level(ctx);
            BlockPos p = pos(ctx);
            // isLoaded rather than the deprecated hasChunkAt: it asks the chunk source directly and folds
            // in the build-height check, which is the behaviour these two outputs describe.
            ctx.setOutput("out", l != null && l.isLoaded(p));
            ctx.setOutput("inBounds", l != null && l.isInWorldBounds(p));
        }
    }

    private static Level level(EvalContext ctx) {
        return ctx.getInput("level", Level.class, null);
    }

    private static BlockPos pos(EvalContext ctx) {
        BlockPos p = ctx.getInput("pos", BlockPos.class, BlockPos.ZERO);
        return p == null ? BlockPos.ZERO : p;
    }
}
