package com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.util.INodeDescription;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Turning, reflecting, decomposing and enumerating directions.
 *
 * <p>Complete on purpose: {@code Direction} has no long tail worth a reflective context. Its readable
 * members were six real ones ({@code opposite}, {@code axis}, {@code stepX/Y/Z}, {@code toYRot}) sitting
 * among {@code declaringClass}, {@code ordinal} and {@code describeConstable} — enum plumbing that a
 * property searcher should never have been offering. Every useful one has a node here.
 *
 * <p>A null direction input reads as NORTH throughout, matching the handle's default — the same
 * convention {@code mc_direction_axis} and {@code mc_direction_opposite} use.
 */
public final class DirectionNodes {

    private static final String GROUP = "mc/geometry";

    private DirectionNodes() {
    }

    /** The direction facing the other way. */
    @NodeAttribute(name = "mc_direction_opposite", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Opposite extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_direction_opposite.tooltip");
        }

        @InputPort public Direction in = Direction.NORTH;
        @OutputPort public Direction out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", dir(ctx, "in").getOpposite());
        }
    }

    /**
     * Which axis the direction lies along.
     *
     * <p>Typed {@code Axis} rather than the {@code "x"}/{@code "y"}/{@code "z"} string an earlier
     * version of this node produced. The string form was only ever compared against literals; the typed
     * form goes straight into {@code Turn} and anything else that wants an axis, and still renders as
     * text on a String pin because anything coerces to String.</p>
     */
    @NodeAttribute(name = "mc_direction_axis", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Axis extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_direction_axis.tooltip");
        }

        @InputPort public Direction in = Direction.NORTH;
        @OutputPort public Direction.Axis out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", dir(ctx, "in").getAxis());
        }
    }

    /** Applies a block {@link Rotation} — the same transform a structure block would. */
    @NodeAttribute(name = "mc_direction_rotate", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Rotate extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_direction_rotate.tooltip");
        }

        @InputPort public Direction in = Direction.NORTH;
        @InputPort public Rotation rotation = Rotation.NONE;
        @OutputPort public Direction out;

        @Override
        public void evaluate(EvalContext ctx) {
            Rotation r = ctx.getInput("rotation", Rotation.class, Rotation.NONE);
            ctx.setOutput("out", (r == null ? Rotation.NONE : r).rotate(dir(ctx, "in")));
        }
    }

    /** Mirrors across a plane. */
    @NodeAttribute(name = "mc_direction_mirror", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class MirrorNode extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_direction_mirror.tooltip");
        }

        @InputPort public Direction in = Direction.NORTH;
        @InputPort public Mirror mirror = Mirror.NONE;
        @OutputPort public Direction out;

        @Override
        public void evaluate(EvalContext ctx) {
            Mirror m = ctx.getInput("mirror", Mirror.class, Mirror.NONE);
            ctx.setOutput("out", (m == null ? Mirror.NONE : m).mirror(dir(ctx, "in")));
        }
    }

    /**
     * A quarter turn about an axis, in either direction.
     *
     * <p>One node with a {@code clockwise} option rather than two, because the pair are the same
     * operation and a graph choosing between them at runtime is a normal thing to want.</p>
     */
    @NodeAttribute(name = "mc_direction_turn", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Turn extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_direction_turn.tooltip");
        }

        @InputPort public Direction in = Direction.NORTH;
        // An input port rather than an option: the type is already a pin type, so a port gets the same
        // inline dropdown AND can be driven by a wire. An option is a no-connector port — it can
        // never be computed, so it is only right when the type is not one the graph carries.
        @InputPort public Direction.Axis axis = Direction.Axis.Y;
        @InputPort public boolean clockwise = true;
        @OutputPort public Direction out;

        @Override
        public void evaluate(EvalContext ctx) {
            Direction d = dir(ctx, "in");
            Direction.Axis a = ctx.getInput("axis", Direction.Axis.class, Direction.Axis.Y);
            if (a == null) a = Direction.Axis.Y;
            boolean cw = ctx.getInput("clockwise", Boolean.class, true);
            ctx.setOutput("out", cw ? d.getClockWise(a) : d.getCounterClockWise(a));
        }
    }

    /**
     * The direction's unit offset, as three ints.
     *
     * <p>Three ints rather than a vector because {@code Direction.getNormal()} hands back a
     * {@code Vec3i}, and the graph deliberately has no integer-vector type — {@code BlockPos} is its
     * integer triple, and a normal is an offset rather than a position. Feed these into
     * {@code mc_block_pos_create} if a position is what is wanted.</p>
     */
    @NodeAttribute(name = "mc_direction_normal", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Normal extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_direction_normal.tooltip");
        }

        @InputPort public Direction in = Direction.NORTH;
        @OutputPort public int x;
        @OutputPort public int y;
        @OutputPort public int z;

        @Override
        public void evaluate(EvalContext ctx) {
            Direction d = dir(ctx, "in");
            ctx.setOutput("x", d.getStepX());
            ctx.setOutput("y", d.getStepY());
            ctx.setOutput("z", d.getStepZ());
        }
    }

    /**
     * A direction's rotation angle and its two index encodings.
     *
     * <p>{@code yRot} is the degrees-about-Y a model or entity needs to face this way, and is the reason
     * the node exists — it is the one {@code Direction} property that is arithmetic rather than a lookup.
     * The index outputs are the game's own two packings: {@code data2D} numbers the four horizontal faces
     * (and is −1 for up/down), {@code data3D} numbers all six. Both are what an array indexed by
     * direction wants.</p>
     */
    @NodeAttribute(name = "mc_direction_data", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Data extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_direction_data.tooltip");
        }

        @InputPort public Direction in = Direction.NORTH;
        @OutputPort public float yRot;
        @OutputPort public int data2D;
        @OutputPort public int data3D;

        @Override
        public void evaluate(EvalContext ctx) {
            Direction d = dir(ctx, "in");
            ctx.setOutput("yRot", d.toYRot());
            ctx.setOutput("data2D", d.get2DDataValue());
            ctx.setOutput("data3D", d.get3DDataValue());
        }
    }

    /** Parses a name like {@code north}. Unknown names give NORTH and {@code found = false}. */
    @NodeAttribute(name = "mc_direction_from_name", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromName extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_direction_from_name.tooltip");
        }

        @InputPort public String name = "north";
        @OutputPort public Direction out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            Direction d = Direction.byName(ctx.getInput("name", String.class, ""));
            ctx.setOutput("out", d == null ? Direction.NORTH : d);
            ctx.setOutput("found", d != null);
        }
    }

    /**
     * The face a vector points most nearly along.
     *
     * <p>How you turn an arbitrary heading — an entity's look vector, a difference between two
     * positions — into one of the six faces.</p>
     */
    @NodeAttribute(name = "mc_direction_nearest", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Nearest extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_direction_nearest.tooltip");
        }

        @InputPort public Vector3f vector;
        @OutputPort public Direction out;

        @Override
        public void evaluate(EvalContext ctx) {
            Vector3f v = ctx.getInput("vector", Vector3f.class, null);
            ctx.setOutput("out", v == null
                    ? Direction.NORTH
                    : Direction.getApproximateNearest(v.x, v.y, v.z));
        }
    }

    /**
     * All directions, or just the four horizontal ones.
     *
     * <p>The list to feed a For Each when a graph has to look at every neighbour. The horizontal-only
     * form is the common case for anything walking on the ground, hence an option rather than making
     * every caller filter.</p>
     */
    // plane stays an option: Direction.Plane is not a pin type, and inventing one so that a single
    // node's dropdown could be wired would be a type nothing else can use.
    @NodeAttribute(name = "mc_direction_all", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class All extends AnnotatedNode implements INodeDescription {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_direction_all.tooltip");
        }

        @Option public Direction.Plane plane = Direction.Plane.HORIZONTAL;
        @OutputPort public List<?> out;

        @Override
        public void evaluate(EvalContext ctx) {
            Direction.Plane p = ctx.getOption("plane", Direction.Plane.class, Direction.Plane.HORIZONTAL);
            List<Direction> all = new ArrayList<>(6);
            if (p == null) {
                // no plane selected means every face, which is neither Plane constant
                all.addAll(List.of(Direction.values()));
            } else {
                for (Direction d : p) all.add(d);
            }
            ctx.setOutput("out", all);
        }

        @Override
        public List<String> optionChoices(String optionId) {
            return "plane".equals(optionId) ? List.of("HORIZONTAL", "VERTICAL") : List.of();
        }
    }

    private static Direction dir(EvalContext ctx, String id) {
        Direction d = ctx.getInput(id, Direction.class, Direction.NORTH);
        return d == null ? Direction.NORTH : d;
    }
}
