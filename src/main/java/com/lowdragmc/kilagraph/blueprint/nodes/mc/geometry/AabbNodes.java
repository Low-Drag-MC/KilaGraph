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
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Axis-aligned bounding boxes: building them, taking them apart, growing them, and asking how two of
 * them relate.
 *
 * <p>Reading a box's edges is {@link Unpack} and its centre is {@link Center}. Both used to be
 * reflective info properties, and a box is exactly the kind of value that does not want a context node:
 * six doubles with no long tail, whose reflective set also dragged in {@code minPosition} /
 * {@code maxPosition} / {@code bottomCenter} as {@code Vec3}-typed pins that had to be curated away
 * one by one to stop them rendering as dead ends.
 *
 * <p>A null box input is treated as the unit cube at the origin rather than propagating null, matching
 * the handle's default value — a box is a value, and every operation here is total.
 */
public final class AabbNodes {

    private static final String GROUP = "mc/geometry";
    private static final AABB UNIT = new AABB(0, 0, 0, 1, 1, 1);

    private AabbNodes() {
    }

    // ---- decomposition -----------------------------------------------------------------------

    /**
     * A box's six edges.
     *
     * <p>{@code double} rather than {@code float} outputs: {@code AABB} is double-precision and these are
     * world coordinates, where a float loses sub-block resolution past a few million blocks — the
     * precision cliff {@code McConvert} documents for the vector boundary. Nothing forces this pair of
     * nodes through that boundary, so they do not pay it.</p>
     */
    @NodeAttribute(name = "mc_aabb_unpack", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Unpack extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_aabb_unpack.tooltip");
        }

        @InputPort public AABB in;
        @OutputPort public double minX;
        @OutputPort public double minY;
        @OutputPort public double minZ;
        @OutputPort public double maxX;
        @OutputPort public double maxY;
        @OutputPort public double maxZ;

        @Override
        public void evaluate(EvalContext ctx) {
            AABB b = box(ctx, "in");
            ctx.setOutput("minX", b.minX);
            ctx.setOutput("minY", b.minY);
            ctx.setOutput("minZ", b.minZ);
            ctx.setOutput("maxX", b.maxX);
            ctx.setOutput("maxY", b.maxY);
            ctx.setOutput("maxZ", b.maxZ);
        }
    }

    /**
     * A box's centre and its extent along each axis.
     *
     * <p>The centre comes out as the graph's vector type, which is the whole reason this is a node:
     * {@code AABB.getCenter()} returns a {@code Vec3}, which is not a pin type, so exposing it
     * reflectively produced a pin that connected to nothing (see {@code McConvert}). Sizes are doubles
     * for the same reason as {@link Unpack}.</p>
     */
    @NodeAttribute(name = "mc_aabb_center", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Center extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_aabb_center.tooltip");
        }

        @InputPort public AABB in;
        @OutputPort public Vector3f center;
        @OutputPort public double xSize;
        @OutputPort public double ySize;
        @OutputPort public double zSize;

        @Override
        public void evaluate(EvalContext ctx) {
            AABB b = box(ctx, "in");
            ctx.setOutput("center", (Object) McConvert.toJoml(b.getCenter()));
            ctx.setOutput("xSize", b.getXsize());
            ctx.setOutput("ySize", b.getYsize());
            ctx.setOutput("zSize", b.getZsize());
        }
    }

    // ---- construction ------------------------------------------------------------------------

    /** The box spanning two points, in either order — {@code AABB(Vec3, Vec3)} normalises the corners. */
    @NodeAttribute(name = "mc_aabb_from_corners", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromCorners extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_aabb_from_corners.tooltip");
        }

        @InputPort public Vector3f a;
        @InputPort public Vector3f b;
        @OutputPort public AABB out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", new AABB(
                    McConvert.toVec3(ctx.getInput("a", Vector3f.class, null)),
                    McConvert.toVec3(ctx.getInput("b", Vector3f.class, null))));
        }
    }

    /** The one-block cube a position names. */
    @NodeAttribute(name = "mc_aabb_from_block_pos", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromBlockPos extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_aabb_from_block_pos.tooltip");
        }

        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public AABB out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockPos p = ctx.getInput("pos", BlockPos.class, BlockPos.ZERO);
            ctx.setOutput("out", new AABB(p == null ? BlockPos.ZERO : p));
        }
    }

    /**
     * A cube of a given total width centred on a point.
     *
     * <p>{@code size} is the full edge length, not a radius — {@code AABB.ofSize}'s convention. A size
     * of 2 around the origin therefore spans -1 to 1.</p>
     */
    @NodeAttribute(name = "mc_aabb_around", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Around extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_aabb_around.tooltip");
        }

        @InputPort public Vector3f center;
        @InputPort public float size = 1f;
        @OutputPort public AABB out;

        @Override
        public void evaluate(EvalContext ctx) {
            double s = ctx.getFloat("size", 1f);
            ctx.setOutput("out", AABB.ofSize(
                    McConvert.toVec3(ctx.getInput("center", Vector3f.class, null)), s, s, s));
        }
    }

    // ---- transformation ----------------------------------------------------------------------

    /** Grows the box by {@code amount} on every side; a negative amount shrinks it. */
    @NodeAttribute(name = "mc_aabb_inflate", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Inflate extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_aabb_inflate.tooltip");
        }

        @InputPort public AABB in;
        @InputPort public float amount = 1f;
        @OutputPort public AABB out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", box(ctx, "in").inflate(ctx.getFloat("amount", 1f)));
        }
    }

    /** The smallest box containing both inputs. */
    @NodeAttribute(name = "mc_aabb_union", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Union extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_aabb_union.tooltip");
        }

        @InputPort public AABB a;
        @InputPort public AABB b;
        @OutputPort public AABB out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", box(ctx, "a").minmax(box(ctx, "b")));
        }
    }

    /**
     * The overlapping part of two boxes.
     *
     * <p>Undefined when they do not overlap: {@code AABB.intersect} will happily produce a box with
     * inverted edges. Test with {@code mc_aabb_intersects} first — which is exactly why that node
     * exists separately rather than as a second output here, since the answer is useful on its own.</p>
     */
    @NodeAttribute(name = "mc_aabb_intersect", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Intersect extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_aabb_intersect.tooltip");
        }

        @InputPort public AABB a;
        @InputPort public AABB b;
        @OutputPort public AABB out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", box(ctx, "a").intersect(box(ctx, "b")));
        }
    }

    // ---- predicates --------------------------------------------------------------------------

    @NodeAttribute(name = "mc_aabb_intersects", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Intersects extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_aabb_intersects.tooltip");
        }

        @InputPort public AABB a;
        @InputPort public AABB b;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", box(ctx, "a").intersects(box(ctx, "b")));
        }
    }

    /** Whether a point is inside the box. The maximum edges are exclusive, as everywhere in the game. */
    @NodeAttribute(name = "mc_aabb_contains", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Contains extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_aabb_contains.tooltip");
        }

        @InputPort public AABB box;
        @InputPort public Vector3f point;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            var v = McConvert.toVec3(ctx.getInput("point", Vector3f.class, null));
            ctx.setOutput("out", box(ctx, "box").contains(v.x, v.y, v.z));
        }
    }

    private static AABB box(EvalContext ctx, String id) {
        AABB b = ctx.getInput(id, AABB.class, UNIT);
        return b == null ? UNIT : b;
    }
}
