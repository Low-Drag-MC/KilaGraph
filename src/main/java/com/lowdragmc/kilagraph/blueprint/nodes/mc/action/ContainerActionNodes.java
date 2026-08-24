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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.McActions;
import static com.lowdragmc.kilagraph.blueprint.nodes.mc.container.Containers.handler;

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

    private static final String GROUP = "mc_container";

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

        @InputPort public ResourceHandler<ItemResource> container;
        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public boolean simulate = false;
        @OutputPort public ItemStack remainder = ItemStack.EMPTY;
        @OutputPort public int inserted;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            ResourceHandler<ItemResource> h = handler(ctx);
            ItemStack give = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            if (h == null || give == null || give.isEmpty()) {
                ctx.setOutput("remainder", give == null ? ItemStack.EMPTY : give);
                ctx.setOutput("inserted", 0);
                McActions.done(ctx, false);
                return;
            }
            // 26.1 does the slot walk itself and reports how much went in.
            int moved = McActions.transfer(ctx, tx -> h.insert(ItemResource.of(give), give.getCount(), tx));
            ItemStack left = give.copyWithCount(give.getCount() - moved);
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

        @InputPort public ResourceHandler<ItemResource> container;
        @InputPort public int slot = 0;
        @InputPort public int amount = 1;
        @InputPort public boolean simulate = false;
        @OutputPort public ItemStack out = ItemStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            ResourceHandler<ItemResource> h = handler(ctx);
            int slot = ctx.getInt("slot", 0);
            int amount = ctx.getInt("amount", 1);
            if (h == null || slot < 0 || slot >= h.size() || amount <= 0) {
                ctx.setOutput("out", ItemStack.EMPTY);
                McActions.done(ctx, false);
                return;
            }
            // The new API extracts a *named* resource, so what is in the slot has to be read first;
            // an empty slot has nothing to name and yields nothing, as before.
            ItemResource want = h.getResource(slot);
            int moved = want.isEmpty() ? 0
                    : McActions.transfer(ctx, tx -> h.extract(slot, want, amount, tx));
            ItemStack taken = moved <= 0 ? ItemStack.EMPTY : want.toStack(moved);
            ctx.setOutput("out", taken);
            McActions.done(ctx, !taken.isEmpty());
        }
    }

    // There is no mc_container_set on 26.1, and the reason is worth stating precisely so nobody
    // re-litigates it: ResourceHandler itself has no overwrite operation, and while NeoForge does
    // declare `IndexModifier.set(index, resource, amount)` as a successor to IItemHandlerModifiable,
    // the only class in the whole of NeoForge 26.1.2 that implements it is ResourceHandlerSlot — a
    // menu-slot adapter. Nothing a capability lookup hands back does, VanillaContainerWrapper least of
    // all, so the node would report ok = false for every chest in the game. Extract then insert
    // instead; that is what the transfer API wants, and it respects the inventory's own rules.
}
