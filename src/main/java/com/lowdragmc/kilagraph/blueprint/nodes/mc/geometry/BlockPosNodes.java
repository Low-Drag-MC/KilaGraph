package com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.mc.McConvert;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Block coordinates: taking them apart, arithmetic on them, and crossing into other types.
 *
 * <p>{@link Unpack} is deliberately a node and not a reflective info property. A {@code BlockPos} is
 * three integers; there is no long tail of interesting members to justify a whole context node plus a
 * Field block plus a searcher, and the reflective set for it was mostly noise ({@code mutable},
 * {@code immutable}, {@code asLong}, {@code toShortString}) around the three fields anyone wants.
 * Reading x/y/z through a dedicated node is also the only form that keeps them in the executor's
 * numeric lane instead of boxing each one.
 */
public final class BlockPosNodes {

    private static final String GROUP = "mc/geometry";

    private BlockPosNodes() {
    }

    /**
     * A position's three coordinates.
     *
     * <p>The inverse of {@code mc_block_pos_create}. Three {@code int} outputs rather than a vector,
     * because a {@code BlockPos} <em>is</em> an integer triple — going through
     * {@code mc_block_pos_to_vector} to read a coordinate would round-trip through floats.</p>
     */
    @NodeAttribute(name = "mc_block_pos_unpack", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Unpack extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_pos_unpack.tooltip");
        }

        @InputPort public BlockPos in = BlockPos.ZERO;
        @OutputPort public int x;
        @OutputPort public int y;
        @OutputPort public int z;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockPos p = pos(ctx, "in");
            ctx.setOutput("x", p.getX());
            ctx.setOutput("y", p.getY());
            ctx.setOutput("z", p.getZ());
        }
    }

    @NodeAttribute(name = "mc_block_pos_add", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Add extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_pos_add.tooltip");
        }

        @InputPort public BlockPos a = BlockPos.ZERO;
        @InputPort public BlockPos b = BlockPos.ZERO;
        @OutputPort public BlockPos out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockPos p = pos(ctx, "a");
            BlockPos q = pos(ctx, "b");
            ctx.setOutput("out", p.offset(q));
        }
    }

    @NodeAttribute(name = "mc_block_pos_subtract", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Subtract extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_pos_subtract.tooltip");
        }

        @InputPort public BlockPos a = BlockPos.ZERO;
        @InputPort public BlockPos b = BlockPos.ZERO;
        @OutputPort public BlockPos out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockPos p = pos(ctx, "a");
            BlockPos q = pos(ctx, "b");
            // subtract returns Vec3i; wrap so the pin keeps its BlockPos type
            var d = p.subtract(q);
            ctx.setOutput("out", new BlockPos(d.getX(), d.getY(), d.getZ()));
        }
    }

    /**
     * Distance between two positions, both squared and not.
     *
     * <p>Both are emitted because the squared one is the useful one and the plain one is the one people
     * reach for. A range test wants {@code sqr} against a squared radius — that is the whole reason
     * {@code distSqr} exists in the game's own code — while a number shown to a player wants
     * {@code out}. Offering only the square root would quietly push every range check through an
     * avoidable one.</p>
     */
    @NodeAttribute(name = "mc_block_pos_distance", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Distance extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_pos_distance.tooltip");
        }

        @InputPort public BlockPos a = BlockPos.ZERO;
        @InputPort public BlockPos b = BlockPos.ZERO;
        @OutputPort public float out;
        @OutputPort public float sqr;

        @Override
        public void evaluate(EvalContext ctx) {
            double d2 = pos(ctx, "a").distSqr(pos(ctx, "b"));
            ctx.setOutput("out", (float) Math.sqrt(d2));
            ctx.setOutput("sqr", (float) d2);
        }
    }

    /**
     * A position as a vector — the block's corner, or the middle of the block it names.
     *
     * <p>The distinction matters and is the reason for the option rather than two nodes: a
     * {@code BlockPos} is a block, not a point, so "where is it" has two defensible answers. Entity
     * positions and ray endpoints want the centre; anything reconstructing a grid wants the corner.
     * Centre is the default because it is what {@code BlockPos.getCenter()} exists for.</p>
     */
    @NodeAttribute(name = "mc_block_pos_to_vector", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToVector extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_pos_to_vector.tooltip");
        }

        @InputPort public BlockPos in = BlockPos.ZERO;
        // An input port rather than an option: the type is already a pin type, so a port gets the same
        // inline dropdown AND can be driven by a wire. An option is a no-connector port — it can
        // never be computed, so it is only right when the type is not one the graph carries.
        @InputPort public boolean center = true;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockPos p = pos(ctx, "in");
            boolean middle = ctx.getInput("center", Boolean.class, true);
            ctx.setOutput("out", (Object) (middle
                    ? McConvert.toJoml(p.getCenter())
                    : new Vector3f(p.getX(), p.getY(), p.getZ())));
        }
    }

    /**
     * The block containing a point.
     *
     * <p>Floor, not round — {@code BlockPos.containing}, the same rule the game uses to decide which
     * block an entity is standing in. Rounding would put the boundary in the middle of a block and make
     * a position at {@code y = 0.6} report the block above it.</p>
     */
    @NodeAttribute(name = "mc_block_pos_from_vector", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromVector extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_pos_from_vector.tooltip");
        }

        @InputPort public Vector3f in;
        @OutputPort public BlockPos out;

        @Override
        public void evaluate(EvalContext ctx) {
            var v = McConvert.toVec3(ctx.getInput("in", Vector3f.class, null));
            ctx.setOutput("out", BlockPos.containing(v.x, v.y, v.z));
        }
    }

    /**
     * Every position in the box between two corners, inclusive.
     *
     * <p><b>Capped, and it says so.</b> {@code betweenClosed} is lazy in the game's code; a graph node
     * has to hand back a real list, and the box a user types is not bounded by anything. A 200-block
     * cube is eight million entries — enough to stall a tick and then run the server out of memory. So
     * the list stops at {@link #LIMIT} and {@code truncated} says whether it did, rather than silently
     * returning a prefix that looks complete.</p>
     */
    @NodeAttribute(name = "mc_block_pos_between", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Between extends AnnotatedNode {
        /** Roughly a 32-block cube. Big enough for real use, small enough to never be the problem. */
        public static final int LIMIT = 32768;

        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_pos_between.tooltip");
        }

        @InputPort public BlockPos min = BlockPos.ZERO;
        @InputPort public BlockPos max = BlockPos.ZERO;
        @OutputPort public List<?> out;
        @OutputPort public boolean truncated;

        @Override
        public void evaluate(EvalContext ctx) {
            List<BlockPos> positions = new ArrayList<>();
            boolean cut = false;
            for (BlockPos p : BlockPos.betweenClosed(pos(ctx, "min"), pos(ctx, "max"))) {
                if (positions.size() >= LIMIT) {
                    cut = true;
                    break;
                }
                // betweenClosed reuses one mutable cursor, so every element has to be copied out —
                // storing the cursor itself would give a list of N references to the final position.
                positions.add(p.immutable());
            }
            ctx.setOutput("out", positions);
            ctx.setOutput("truncated", cut);
        }
    }

    private static BlockPos pos(EvalContext ctx, String id) {
        BlockPos p = ctx.getInput(id, BlockPos.class, BlockPos.ZERO);
        return p == null ? BlockPos.ZERO : p;
    }
}
