package com.lowdragmc.kilagraph.blueprint.nodes.mc.fluid;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Fluids and fluid stacks.
 *
 * <p>{@code FluidStack} is NeoForge's, not vanilla's — vanilla has no "some amount of a fluid" value at
 * all. Amounts are in millibuckets, where a bucket is 1000; that constant is not a node because it is a
 * literal, and a Constant node already says it more clearly than {@code mc_fluid_bucket_volume} would.
 *
 * <p>{@link Unpack} is how a stack is read. It is a node rather than a reflective property because
 * {@code fluid} and {@code amount} are the whole of what a fluid stack is, and because the reflective
 * set around them was NeoForge internals — {@code componentsPatch},
 * {@code isComponentsPatchEmpty}, {@code fluidHolder}, {@code copyAndClear} — none of which is data a
 * blueprint should be offered.
 */
public final class FluidNodes {

    private static final String GROUP = "mc/fluid";

    private FluidNodes() {
    }

    // ---- reading -----------------------------------------------------------------------------

    /**
     * What a fluid stack is: which fluid, how much, and whether it is nothing.
     *
     * <p>The amount is in millibuckets. {@code empty} is its own output rather than an
     * {@code amount == 0} test because an {@code EMPTY} stack and a zero-amount stack are both empty and
     * only the game's own predicate covers both.</p>
     */
    @NodeAttribute(name = "mc_fluid_stack_unpack", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Unpack extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_stack_unpack.tooltip");
        }

        @InputPort public FluidStack stack = FluidStack.EMPTY;
        @OutputPort public Fluid fluid;
        @OutputPort public int amount;
        @OutputPort public boolean empty;
        @OutputPort public Component name;

        @Override
        public void evaluate(EvalContext ctx) {
            FluidStack s = stack(ctx, "stack");
            ctx.setOutput("fluid", s.getFluid());
            ctx.setOutput("amount", s.getAmount());
            ctx.setOutput("empty", s.isEmpty());
            ctx.setOutput("name", (Object) s.getHoverName());
        }
    }

    /**
     * The bucket item that holds this fluid.
     *
     * <p>The only member of {@code Fluid} itself worth a node — a fluid's other readable properties are
     * its state definition and its NeoForge {@code FluidType}, neither of which is a pin type. A fluid
     * with no bucket form gives air.</p>
     */
    @NodeAttribute(name = "mc_fluid_bucket", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Bucket extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_bucket.tooltip");
        }

        @InputPort public Fluid in = Fluids.WATER;
        @OutputPort public Item out;

        @Override
        public void evaluate(EvalContext ctx) {
            Fluid f = ctx.getInput("in", Fluid.class, Fluids.WATER);
            ctx.setOutput("out", f == null || f == Fluids.EMPTY
                    ? Items.AIR
                    : f.getBucket());
        }
    }

    @NodeAttribute(name = "mc_fluid_stack_create", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Create extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_stack_create.tooltip");
        }

        @InputPort public Fluid fluid = Fluids.WATER;
        @InputPort public int amount = 1000;
        @OutputPort public FluidStack out;

        @Override
        public void evaluate(EvalContext ctx) {
            Fluid f = ctx.getInput("fluid", Fluid.class, Fluids.WATER);
            int amount = Math.max(0, ctx.getInt("amount", 1000));
            // An empty fluid or a zero amount both mean "nothing", and FluidStack's own constructor
            // would happily build an inconsistent stack out of one but not the other.
            ctx.setOutput("out", f == null || f == Fluids.EMPTY || amount == 0
                    ? FluidStack.EMPTY
                    : new FluidStack(f, amount));
        }
    }

    @NodeAttribute(name = "mc_fluid_stack_with_amount", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class WithAmount extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_stack_with_amount.tooltip");
        }

        @InputPort public FluidStack stack = FluidStack.EMPTY;
        @InputPort public int amount = 1000;
        @OutputPort public FluidStack out;

        @Override
        public void evaluate(EvalContext ctx) {
            FluidStack s = stack(ctx, "stack");
            int amount = Math.max(0, ctx.getInt("amount", 1000));
            if (s.isEmpty() || amount == 0) {
                ctx.setOutput("out", FluidStack.EMPTY);
                return;
            }
            // copy first: a FluidStack is mutable and the input may already have been handed to
            // another branch of this run.
            FluidStack copy = s.copy();
            copy.setAmount(amount);
            ctx.setOutput("out", copy);
        }
    }

    /** Whether two stacks hold the same fluid, ignoring amount. */
    @NodeAttribute(name = "mc_fluid_stack_same_fluid", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SameFluid extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_stack_same_fluid.tooltip");
        }

        @InputPort public FluidStack a = FluidStack.EMPTY;
        @InputPort public FluidStack b = FluidStack.EMPTY;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", FluidStack.isSameFluid(stack(ctx, "a"), stack(ctx, "b")));
        }
    }

    /**
     * The block a fluid forms in the world — water to {@code minecraft:water}.
     *
     * <p>A fluid with no block form (any non-placeable modded fluid, or {@code EMPTY}) gives air.</p>
     */
    @NodeAttribute(name = "mc_fluid_to_block", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToBlock extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_to_block.tooltip");
        }

        @InputPort public Fluid in = Fluids.WATER;
        @OutputPort public Block out;

        @Override
        public void evaluate(EvalContext ctx) {
            Fluid f = ctx.getInput("in", Fluid.class, Fluids.WATER);
            if (f == null || f == Fluids.EMPTY) {
                ctx.setOutput("out", Blocks.AIR);
                return;
            }
            ctx.setOutput("out", f.defaultFluidState().createLegacyBlock().getBlock());
        }
    }

    /**
     * The fluid a block is, or contains.
     *
     * <p>Water for a water block <em>and</em> for anything waterlogged, since it goes through the
     * block's default state's fluid state. Air gives {@code EMPTY}.</p>
     */
    @NodeAttribute(name = "mc_fluid_from_block", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromBlock extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_from_block.tooltip");
        }

        @InputPort public Block in = Blocks.WATER;
        @OutputPort public Fluid out;

        @Override
        public void evaluate(EvalContext ctx) {
            Block b = ctx.getInput("in", Block.class, Blocks.WATER);
            ctx.setOutput("out", b == null
                    ? Fluids.EMPTY
                    : b.defaultBlockState().getFluidState().getType());
        }
    }

    private static FluidStack stack(EvalContext ctx, String id) {
        FluidStack s = ctx.getInput(id, FluidStack.class, FluidStack.EMPTY);
        return s == null ? FluidStack.EMPTY : s;
    }
}
