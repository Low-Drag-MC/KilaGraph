package com.lowdragmc.kilagraph.blueprint.nodes.mc.container;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.McActions;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import static com.lowdragmc.kilagraph.blueprint.nodes.mc.container.Containers.fluidIn;
import static com.lowdragmc.kilagraph.blueprint.nodes.mc.container.Containers.handler;

/**
 * Fluid tanks: finding one, reading it, and moving fluid in or out.
 *
 * <p>The same shape as {@code ContainerNodes} for the other thing blocks store, and for the same reason:
 * the capability interface is what every tank worth talking to speaks. Vanilla has no fluid-storage
 * abstraction at all — a cauldron is a block state, not a tank — so this is NeoForge's idea end to end
 * and works on modded machines exactly as it does on anything else that implements it.
 *
 * <h2>Fill and drain are the whole API</h2>
 * There is no "set tank 3 to this" here, deliberately. Fluid handlers are not addressable the way item
 * slots are: a tank decides for itself which of its internal tanks a fill goes to, and many are a single
 * logical tank with several compartments. Fill and drain are what the interface offers and what pipes
 * use, so they are what a graph gets.
 */
public final class FluidContainerNodes {

    private static final String GROUP = "mc/container";

    private FluidContainerNodes() {
    }

    // ---- resolving ---------------------------------------------------------------------------

    /** The fluid tank of a block, from a side. */
    @NodeAttribute(name = "mc_block_fluid_container", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BlockFluidContainer extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_fluid_container.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public Direction side = Direction.NORTH;
        @OutputPort public ResourceHandler<FluidResource> out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null) {
                ctx.setOutput("out", null);
                ctx.setOutput("found", false);
                return;
            }
            Direction from = ctx.getInput("side", Direction.class, Direction.NORTH);
            var handler = Capabilities.Fluid.BLOCK.getCapability(world, at, null, null, from);
            ctx.setOutput("out", handler);
            ctx.setOutput("found", handler != null);
        }
    }

    // ---- reading -----------------------------------------------------------------------------

    /** How many separate tanks the handler exposes. Zero when there is no handler. */
    @NodeAttribute(name = "mc_fluid_container_tanks", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Tanks extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_container_tanks.tooltip");
        }

        @InputPort public ResourceHandler<FluidResource> container;
        @OutputPort public int tanks;

        @Override
        public void evaluate(EvalContext ctx) {
            ResourceHandler<FluidResource> h = handler(ctx);
            ctx.setOutput("tanks", h == null ? 0 : h.size());
        }
    }

    /**
     * What is in one tank, and how much it could hold.
     *
     * <p>Capacity comes out alongside the contents because the useful quantity is almost always the
     * ratio — a tank readout, a comparator signal, a decision about whether to keep filling — and asking
     * for the two halves separately would mean resolving the same tank twice.</p>
     */
    @NodeAttribute(name = "mc_fluid_container_get", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Get extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_container_get.tooltip");
        }

        @InputPort public ResourceHandler<FluidResource> container;
        @InputPort public int tank = 0;
        @OutputPort public FluidStack out = FluidStack.EMPTY;
        @OutputPort public int capacity;
        @OutputPort public boolean empty;

        @Override
        public void evaluate(EvalContext ctx) {
            ResourceHandler<FluidResource> h = handler(ctx);
            int tank = ctx.getInt("tank", 0);
            if (h == null || tank < 0 || tank >= h.size()) {
                ctx.setOutput("out", FluidStack.EMPTY);
                ctx.setOutput("capacity", 0);
                ctx.setOutput("empty", true);
                return;
            }
            FluidStack in = fluidIn(h, tank);
            ctx.setOutput("out", in);
            ctx.setOutput("capacity", h.getCapacityAsInt(tank, h.getResource(tank)));
            ctx.setOutput("empty", in.isEmpty());
        }
    }

    // ---- moving fluid ------------------------------------------------------------------------

    /**
     * Puts fluid into a tank.
     *
     * <p>Reports how much was actually accepted, which may be less than offered — tanks fill partially
     * all the time. Simulate answers "would this fit" without changing anything, and is the way to get
     * all-or-nothing behaviour: a fill that only half fits has already moved half.</p>
     */
    @NodeAttribute(name = "mc_fluid_container_fill", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Fill extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_container_fill.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public ResourceHandler<FluidResource> container;
        @InputPort public FluidStack fluid = FluidStack.EMPTY;
        @InputPort public boolean simulate = false;
        @OutputPort public int filled;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            ResourceHandler<FluidResource> h = handler(ctx);
            FluidStack give = ctx.getInput("fluid", FluidStack.class, FluidStack.EMPTY);
            if (h == null || give == null || give.isEmpty()) {
                ctx.setOutput("filled", 0);
                McActions.done(ctx, false);
                return;
            }
            int moved = McActions.transfer(ctx, tx -> h.insert(FluidResource.of(give), give.getAmount(), tx));
            ctx.setOutput("filled", moved);
            McActions.done(ctx, moved > 0);
        }
    }

    /**
     * Takes fluid out of a tank.
     *
     * <p>Drains whatever the tank offers up to {@code amount}, which is how pipes work — a graph asking
     * for a bucket's worth from a tank holding half of one gets half, and is told so by the returned
     * stack rather than by a failure.</p>
     */
    @NodeAttribute(name = "mc_fluid_container_drain", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Drain extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_container_drain.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public ResourceHandler<FluidResource> container;
        @InputPort public int amount = 1000;
        @InputPort public boolean simulate = false;
        @OutputPort public FluidStack out = FluidStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            ResourceHandler<FluidResource> h = handler(ctx);
            int amount = ctx.getInt("amount", 1000);
            if (h == null || amount <= 0) {
                ctx.setOutput("out", FluidStack.EMPTY);
                McActions.done(ctx, false);
                return;
            }
            // The new API extracts a *named* resource where the old drain(amount) took whatever was
            // there. extractFirst is NeoForge's own primitive for exactly that translation, so the
            // search for something to take is not hand-rolled here.
            var took = McActions.transfer(ctx,
                    tx -> ResourceHandlerUtil.extractFirst(h, r -> true, amount, tx));
            FluidStack taken = took == null || took.amount() <= 0
                    ? FluidStack.EMPTY : took.resource().toStack(took.amount());
            ctx.setOutput("out", taken);
            McActions.done(ctx, !taken.isEmpty());
        }
    }

}
