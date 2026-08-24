package com.lowdragmc.kilagraph.blueprint.nodes.mc.container;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * Finding an inventory, and reading what is in it.
 *
 * <h2>Why {@code IItemHandler} and not vanilla's {@code Container}</h2>
 * The pin type is NeoForge's capability interface, which is the one abstraction that covers a chest, a
 * furnace's three slots, a player's inventory, an entity's inventory and any modded machine alike — it is
 * what hoppers and pipes already speak. A node set built on vanilla {@code Container} would work on
 * vanilla blocks and silently fail on every modded one, which is the opposite of what a scripting layer
 * should do.
 *
 * <h2>Resolve once, use many times</h2>
 * Finding an inventory is a capability lookup, so the resolving nodes are separate from the reading ones:
 * a graph resolves a chest once and then asks it several questions, rather than re-resolving per query.
 * That is the same reason {@code InfoContextNode} exists for entities.
 *
 * <p>Every reader is null-safe: no inventory means zero slots and empty stacks, never a crash. A graph
 * pointed at a block that turns out not to be a container is a normal situation, not an error.
 */
public final class ContainerNodes {

    private static final String GROUP = "mc/container";

    private ContainerNodes() {
    }

    // ---- resolving ---------------------------------------------------------------------------

    /**
     * The inventory of a block, from a side.
     *
     * <p>The side matters and is not decoration: a furnace exposes its input from the top, its fuel from
     * the sides and its output from below, and that is how a hopper interacts with one. Leaving it
     * unset asks from the north, which for most blocks is the same as any other side.</p>
     */
    @NodeAttribute(name = "mc_block_container", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BlockContainer extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_container.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public Direction side = Direction.NORTH;
        @OutputPort public IItemHandler out;
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
            // Nulls for state and block entity let the capability resolve them itself, which is also the
            // path that works for a block that has no block entity at all.
            IItemHandler handler = Capabilities.ItemHandler.BLOCK.getCapability(world, at, null, null, from);
            ctx.setOutput("out", handler);
            ctx.setOutput("found", handler != null);
        }
    }

    /**
     * The inventory of an entity.
     *
     * <h2>Almost every entity has one, and it may not be what you expect</h2>
     * A chest minecart or a donkey with a chest resolves to storage, as you would hope. But NeoForge also
     * registers a handler over <b>hands and armour</b> for every living entity, so a pig resolves too —
     * to six slots that are its equipment. A graph that finds a container on a mob and starts inserting
     * is putting items in the mob's hands.
     *
     * <p>So {@code found} being true does not mean "this thing is a chest". If storage is what a graph
     * means, it should check what kind of entity it has first.
     *
     * <p>A player is handled separately again — vanilla routes it through its own wrapper — so use
     * {@code mc_player_container} for one of those.
     */
    @NodeAttribute(name = "mc_entity_container", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EntityContainer extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_container.tooltip");
        }

        @InputPort public Entity entity;
        @OutputPort public IItemHandler out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            IItemHandler handler = e == null ? null : e.getCapability(Capabilities.ItemHandler.ENTITY);
            ctx.setOutput("out", handler);
            ctx.setOutput("found", handler != null);
        }
    }

    /**
     * A player's inventory, as a container.
     *
     * <p>Separate from {@code mc_entity_container} because vanilla does not expose a player's inventory
     * through the entity capability — it has to be wrapped explicitly. The slot order is the inventory's
     * own: hotbar first, then the main grid, then armour and the off-hand.</p>
     */
    @NodeAttribute(name = "mc_player_container", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class PlayerContainer extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_player_container.tooltip");
        }

        @InputPort public Player player;
        @OutputPort public IItemHandler out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            var p = ctx.getInput("player", Player.class, null);
            IItemHandler handler = p == null
                    ? null
                    : new InvWrapper(p.getInventory());
            ctx.setOutput("out", handler);
            ctx.setOutput("found", handler != null);
        }
    }

    // ---- reading -----------------------------------------------------------------------------

    /** How many slots the inventory has. Zero when there is no inventory. */
    @NodeAttribute(name = "mc_container_size", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Size extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_container_size.tooltip");
        }

        @InputPort public IItemHandler container;
        @OutputPort public int slots;

        @Override
        public void evaluate(EvalContext ctx) {
            IItemHandler h = handler(ctx);
            ctx.setOutput("slots", h == null ? 0 : h.getSlots());
        }
    }

    /**
     * What is in one slot.
     *
     * <p>An out-of-range slot reads as empty rather than throwing, so a loop that runs past the end of a
     * smaller-than-expected inventory degrades instead of breaking the flow.</p>
     */
    @NodeAttribute(name = "mc_container_get", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Get extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_container_get.tooltip");
        }

        @InputPort public IItemHandler container;
        @InputPort public int slot = 0;
        @OutputPort public ItemStack out = ItemStack.EMPTY;
        @OutputPort public boolean empty;

        @Override
        public void evaluate(EvalContext ctx) {
            IItemHandler h = handler(ctx);
            int slot = ctx.getInt("slot", 0);
            ItemStack stack = h == null || slot < 0 || slot >= h.getSlots()
                    ? ItemStack.EMPTY
                    // Copy: the capability contract says the returned stack must not be modified, and a
                    // graph value can be handed to anything downstream.
                    : h.getStackInSlot(slot).copy();
            ctx.setOutput("out", stack);
            ctx.setOutput("empty", stack.isEmpty());
        }
    }

    /** How many of one item the inventory holds in total, across every slot. */
    @NodeAttribute(name = "mc_container_count", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Count extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_container_count.tooltip");
        }

        @InputPort public IItemHandler container;
        @InputPort public Item item;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            IItemHandler h = handler(ctx);
            Item want = ctx.getInput("item", Item.class, null);
            int total = 0;
            if (h != null && want != null) {
                for (int i = 0; i < h.getSlots(); i++) {
                    ItemStack in = h.getStackInSlot(i);
                    if (in.is(want)) total += in.getCount();
                }
            }
            ctx.setOutput("count", total);
        }
    }

    /**
     * The first slot holding a given item, or the first empty slot.
     *
     * <p>Both questions in one node because they are the same search and a graph moving items asks them
     * together: where is the thing, and where can I put one. Leaving {@code item} unset searches for an
     * empty slot.</p>
     */
    @NodeAttribute(name = "mc_container_find", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Find extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_container_find.tooltip");
        }

        @InputPort public IItemHandler container;
        @InputPort public Item item;
        @OutputPort public int slot;
        @OutputPort public boolean found;
        @OutputPort public int firstEmpty;

        @Override
        public void evaluate(EvalContext ctx) {
            IItemHandler h = handler(ctx);
            Item want = ctx.getInput("item", Item.class, null);
            int match = -1;
            int empty = -1;
            if (h != null) {
                for (int i = 0; i < h.getSlots(); i++) {
                    ItemStack in = h.getStackInSlot(i);
                    if (empty < 0 && in.isEmpty()) empty = i;
                    if (match < 0 && want != null && in.is(want)) match = i;
                    if (match >= 0 && empty >= 0) break;
                }
            }
            ctx.setOutput("slot", match);
            ctx.setOutput("found", match >= 0);
            ctx.setOutput("firstEmpty", empty);
        }
    }

    static IItemHandler handler(EvalContext ctx) {
        return ctx.getInput("container", IItemHandler.class, null);
    }
}
