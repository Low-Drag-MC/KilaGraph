package com.lowdragmc.kilagraph.blueprint.nodes.mc.action;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Moving items in and out of an inventory. See {@link McActions} for the rules every action shares, and
 * {@code ContainerNodes} for how an inventory is found in the first place.
 *
 * <h2>Simulate</h2>
 * Insert and extract both take a {@code simulate} input, which is the capability's own idea and worth
 * exposing rather than hiding: it answers "would this fit" / "is this available" without changing
 * anything. A graph that has to move an item somewhere and fall back elsewhere on failure should simulate
 * first, because an insert that only half fits has already moved half.
 */
public final class ContainerActionNodes {

    private static final String GROUP = "mc/container";

    private ContainerActionNodes() {
    }

    /**
     * Puts a stack into an inventory, into whichever slots will take it.
     *
     * <p>Distributes across slots the way a hopper does, rather than demanding one slot hold the lot.
     * What would not fit comes out on {@code remainder} — losing it silently is the failure mode this
     * shape exists to avoid, the same as {@code mc_give_item}.</p>
     */
    @NodeAttribute(name = "mc_container_insert", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Insert extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_container_insert.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public IItemHandler container;
        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public boolean simulate = false;
        @OutputPort public ItemStack remainder = ItemStack.EMPTY;
        @OutputPort public int inserted;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            IItemHandler h = ctx.getInput("container", IItemHandler.class, null);
            ItemStack give = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            if (h == null || give == null || give.isEmpty()) {
                ctx.setOutput("remainder", give == null ? ItemStack.EMPTY : give);
                ctx.setOutput("inserted", 0);
                McActions.done(ctx, false);
                return;
            }
            boolean simulate = ctx.getBool("simulate", false);
            ItemStack left = give.copy();
            for (int i = 0; i < h.getSlots() && !left.isEmpty(); i++) {
                left = h.insertItem(i, left, simulate);
            }
            int moved = give.getCount() - left.getCount();
            ctx.setOutput("remainder", left);
            ctx.setOutput("inserted", moved);
            // Partial success is still success: moved > 0 means the world changed. The caller checks
            // remainder when it needs all-or-nothing, which is what simulate is for.
            McActions.done(ctx, moved > 0);
        }
    }

    /**
     * Takes items out of one slot.
     *
     * <p>Returns what was actually removed, which may be fewer than asked for. An empty result and
     * {@code ok = false} mean the slot had nothing to give.</p>
     */
    @NodeAttribute(name = "mc_container_extract", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Extract extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_container_extract.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public IItemHandler container;
        @InputPort public int slot = 0;
        @InputPort public int amount = 1;
        @InputPort public boolean simulate = false;
        @OutputPort public ItemStack out = ItemStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            IItemHandler h = ctx.getInput("container", IItemHandler.class, null);
            int slot = ctx.getInt("slot", 0);
            int amount = ctx.getInt("amount", 1);
            if (h == null || slot < 0 || slot >= h.getSlots() || amount <= 0) {
                ctx.setOutput("out", ItemStack.EMPTY);
                McActions.done(ctx, false);
                return;
            }
            ItemStack taken = h.extractItem(slot, amount, ctx.getBool("simulate", false));
            ctx.setOutput("out", taken);
            McActions.done(ctx, !taken.isEmpty());
        }
    }

    /**
     * Overwrites one slot, ignoring what was there.
     *
     * <h2>Not every inventory allows this</h2>
     * Setting a slot outright bypasses the validity checks that insert respects — a furnace would happily
     * be given a diamond as fuel. The capability only offers it on inventories that opt in, so this node
     * reports {@code ok = false} for one that does not, rather than pretending. Prefer
     * {@code mc_container_insert} unless you specifically mean to overwrite.
     */
    @NodeAttribute(name = "mc_container_set", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Set extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_container_set.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public IItemHandler container;
        @InputPort public int slot = 0;
        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            IItemHandler h = ctx.getInput("container", IItemHandler.class, null);
            int slot = ctx.getInt("slot", 0);
            ItemStack put = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            if (!(h instanceof IItemHandlerModifiable mod) || slot < 0 || slot >= h.getSlots() || put == null) {
                McActions.done(ctx, false);
                return;
            }
            // Copy: the inventory takes ownership of what it is handed, and the input may be read again
            // by another branch of this run.
            mod.setStackInSlot(slot, put.copy());
            McActions.done(ctx, true);
        }
    }
}
