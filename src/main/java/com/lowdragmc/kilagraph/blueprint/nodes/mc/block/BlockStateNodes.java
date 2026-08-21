package com.lowdragmc.kilagraph.blueprint.nodes.mc.block;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * Block states: the default state of a block, and the properties that distinguish one state from
 * another.
 *
 * <h2>Properties are addressed by name, and their values are text</h2>
 * A {@code Property<T>} is generic over its value type — {@code Boolean} for {@code waterlogged},
 * {@code Direction} for {@code facing}, an {@code Integer} range for {@code level}, an arbitrary enum
 * for {@code half}. A node cannot have a port whose type depends on a string typed at runtime, and
 * even a dynamic port could not, because the property set depends on the <em>state on the wire</em>
 * rather than on an option.
 *
 * <p>So the exchange currency is the property's own serialised name, via {@code Property.getName(T)}
 * and {@code Property.getValue(String)} — the same text that appears in {@code /setblock}, in a
 * blockstate JSON, and in the F3 screen. That makes these nodes total (every property is reachable),
 * self-documenting (the strings are ones users already know), and honest about what they are: a
 * reflective-style escape hatch, not type-safe access.
 *
 * <p>{@code mc_block_state_properties} exists so a graph can discover the names rather than guess.
 */
public final class BlockStateNodes {

    private static final String GROUP = "mc_block";

    private BlockStateNodes() {
    }

    /** The state's block — the bridge back from BlockState to Block. */
    @NodeAttribute(name = "mc_block_state_block", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class StateBlock extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_state_block.tooltip");
        }

        @InputPort public BlockState in;
        @OutputPort public Block out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState s = ctx.getInput("in", BlockState.class, null);
            ctx.setOutput("out", s == null ? Blocks.AIR : s.getBlock());
        }
    }

    /**
     * The state's physical flags, in one node.
     *
     * <p>Seven booleans and a light level rather than eight nodes, because they are never wanted alone —
     * anything walking a region is asking several of these about every state it sees, and a node per flag
     * would mean seven wires from the same state. {@code air} and {@code hasBlockEntity} are the two that
     * carry most of the weight in practice: the first is how a graph tests "is there anything here",
     * without which {@code mc_is_empty_block}'s level lookup is the only route.</p>
     */
    @NodeAttribute(name = "mc_block_state_flags", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Flags extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_state_flags.tooltip");
        }

        @InputPort public BlockState in;
        @OutputPort public boolean air;
        @OutputPort public boolean solid;
        @OutputPort public boolean liquid;
        @OutputPort public boolean canOcclude;
        @OutputPort public boolean blocksMotion;
        @OutputPort public boolean hasBlockEntity;
        @OutputPort public boolean randomlyTicking;
        @OutputPort public int lightEmission;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState s = ctx.getInput("in", BlockState.class, null);
            if (s == null) s = Blocks.AIR.defaultBlockState();
            ctx.setOutput("air", s.isAir());
            ctx.setOutput("solid", s.isSolid());
            ctx.setOutput("liquid", s.liquid());
            ctx.setOutput("canOcclude", s.canOcclude());
            ctx.setOutput("blocksMotion", s.blocksMotion());
            ctx.setOutput("hasBlockEntity", s.hasBlockEntity());
            ctx.setOutput("randomlyTicking", s.isRandomlyTicking());
            ctx.setOutput("lightEmission", s.getLightEmission());
        }
    }

    /**
     * A block's physical constants.
     *
     * <p>These live on {@code Block} rather than on a state because they are properties of the block
     * type: the movement factors are what makes ice slippery and soul sand slow, and
     * {@code explosionResistance} and {@code destroyTime} are what a graph implementing mining or
     * blast logic compares against. All floats, all read straight off the block.</p>
     */
    @NodeAttribute(name = "mc_block_props", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BlockProps extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_props.tooltip");
        }

        @InputPort public Block in = Blocks.STONE;
        @OutputPort public Component name;
        @OutputPort public float explosionResistance;
        @OutputPort public float friction;
        @OutputPort public float speedFactor;
        @OutputPort public float jumpFactor;
        @OutputPort public float destroyTime;

        @Override
        public void evaluate(EvalContext ctx) {
            Block b = ctx.getInput("in", Block.class, Blocks.STONE);
            if (b == null) b = Blocks.AIR;
            ctx.setOutput("name", (Object) b.getName());
            ctx.setOutput("explosionResistance", b.getExplosionResistance());
            ctx.setOutput("friction", b.getFriction());
            ctx.setOutput("speedFactor", b.getSpeedFactor());
            ctx.setOutput("jumpFactor", b.getJumpFactor());
            ctx.setOutput("destroyTime", b.defaultDestroyTime());
        }
    }

    /** A block's default state — the bridge from the Block type to the BlockState type. */
    @NodeAttribute(name = "mc_block_default_state", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class DefaultState extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_default_state.tooltip");
        }

        @InputPort public Block block = Blocks.STONE;
        @OutputPort public BlockState out;

        @Override
        public void evaluate(EvalContext ctx) {
            Block b = ctx.getInput("block", Block.class, Blocks.STONE);
            ctx.setOutput("out", (b == null ? Blocks.AIR : b).defaultBlockState());
        }
    }

    /** Every property name this state carries, for feeding a For Each or a dropdown. */
    @NodeAttribute(name = "mc_block_state_properties", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Properties extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_state_properties.tooltip");
        }

        @InputPort public BlockState state;
        @OutputPort public List<?> out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState s = ctx.getInput("state", BlockState.class, null);
            List<String> names = new ArrayList<>();
            if (s != null) {
                for (Property<?> p : s.getProperties()) names.add(p.getName());
            }
            ctx.setOutput("out", names);
        }
    }

    /**
     * Reads one property as text. {@code found} distinguishes "this state has no such property" from
     * a property whose value happens to be the empty string.
     */
    @NodeAttribute(name = "mc_block_state_get_property", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetProperty extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_state_get_property.tooltip");
        }

        @InputPort public BlockState state;
        @InputPort public String name = "";
        @OutputPort public String value;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState s = ctx.getInput("state", BlockState.class, null);
            Property<?> p = property(s, ctx.getInput("name", String.class, ""));
            if (p == null) {
                ctx.setOutput("value", "");
                ctx.setOutput("found", false);
                return;
            }
            ctx.setOutput("value", read(s, p));
            ctx.setOutput("found", true);
        }
    }

    /**
     * Writes one property from text.
     *
     * <p>{@code ok} is false, and {@code out} is the input state unchanged, when the property does not
     * exist or the text is not one of its legal values. Returning the original rather than null keeps
     * a chain of these nodes usable: one bad edit does not poison the rest.</p>
     */
    @NodeAttribute(name = "mc_block_state_set_property", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetProperty extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_state_set_property.tooltip");
        }

        @InputPort public BlockState state;
        @InputPort public String name = "";
        @InputPort public String value = "";
        @OutputPort public BlockState out;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState s = ctx.getInput("state", BlockState.class, null);
            Property<?> p = property(s, ctx.getInput("name", String.class, ""));
            BlockState result = p == null
                    ? null
                    : write(s, p, ctx.getInput("value", String.class, ""));
            ctx.setOutput("out", result == null ? s : result);
            ctx.setOutput("ok", result != null);
        }
    }

    @NodeAttribute(name = "mc_block_state_is_block", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class IsBlock extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_state_is_block.tooltip");
        }

        @InputPort public BlockState state;
        @InputPort public Block block = Blocks.STONE;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState s = ctx.getInput("state", BlockState.class, null);
            Block b = ctx.getInput("block", Block.class, Blocks.STONE);
            ctx.setOutput("out", s != null && b != null && s.is(b));
        }
    }

    /** The state as it would appear in a structure rotated by {@code rotation}. */
    @NodeAttribute(name = "mc_block_state_rotate", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Rotate extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_state_rotate.tooltip");
        }

        @InputPort public BlockState state;
        @InputPort public Rotation rotation = Rotation.NONE;
        @OutputPort public BlockState out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState s = ctx.getInput("state", BlockState.class, null);
            Rotation r = ctx.getInput("rotation", Rotation.class, Rotation.NONE);
            ctx.setOutput("out", s == null ? null : s.rotate(r == null ? Rotation.NONE : r));
        }
    }

    @NodeAttribute(name = "mc_block_state_mirror", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class MirrorState extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_state_mirror.tooltip");
        }

        @InputPort public BlockState state;
        @InputPort public Mirror mirror = Mirror.NONE;
        @OutputPort public BlockState out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState s = ctx.getInput("state", BlockState.class, null);
            Mirror m = ctx.getInput("mirror", Mirror.class, Mirror.NONE);
            ctx.setOutput("out", s == null ? null : s.mirror(m == null ? Mirror.NONE : m));
        }
    }

    /**
     * The fluid occupying this state — water for a water block, and also for a waterlogged one.
     *
     * <p>Not the same question as "is this block a fluid": a waterlogged stair is a stair whose state
     * contains water, and this reports the water.</p>
     */
    @NodeAttribute(name = "mc_block_state_fluid", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class StateFluid extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_state_fluid.tooltip");
        }

        @InputPort public BlockState state;
        @OutputPort public Fluid out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState s = ctx.getInput("state", BlockState.class, null);
            ctx.setOutput("out", s == null ? Fluids.EMPTY : s.getFluidState().getType());
        }
    }

    // ---- shared ------------------------------------------------------------------------------

    /** The property called {@code name} on {@code state}'s block, or null. */
    private static Property<?> property(BlockState state, String name) {
        if (state == null || name == null || name.isEmpty()) return null;
        return state.getBlock().getStateDefinition().getProperty(name);
    }

    /**
     * The state's value for {@code p} as its serialised name.
     *
     * <p>Generic helper rather than inline because {@code getValue}/{@code getName} have to agree on
     * the same {@code T}, and a wildcard {@code Property<?>} cannot express that at the call site.</p>
     */
    private static <T extends Comparable<T>> String read(BlockState state, Property<T> p) {
        return p.getName(state.getValue(p));
    }

    /** {@code state} with {@code p} set from text, or null when the text is not a legal value. */
    private static <T extends Comparable<T>> BlockState write(BlockState state, Property<T> p, String value) {
        return p.getValue(value).map(v -> state.setValue(p, v)).orElse(null);
    }
}
