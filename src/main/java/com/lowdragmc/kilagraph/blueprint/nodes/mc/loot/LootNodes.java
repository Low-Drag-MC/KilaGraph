package com.lowdragmc.kilagraph.blueprint.nodes.mc.loot;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolling loot tables and asking what a block would drop.
 *
 * <h2>Random, and therefore not cacheable</h2>
 * Everything here rolls dice. That makes these the only data nodes in the mod whose answer changes between
 * two evaluations with identical inputs, which matters because the executor memoises per generation: within
 * one run a graph reading the same roll twice sees one result, and across runs it sees new ones. A graph
 * that wants two independent chest-fulls needs two runs or two nodes, not one node read twice.
 *
 * <h2>Server side only</h2>
 * Loot tables live on the server's reloadable registries, reached through {@code level.getServer()}, so a
 * client-side world has none. That is reported as {@code found = false} like any other missing content.
 *
 * <h2>Parameters, and why rolling can fail after the table was found</h2>
 * A loot table declares what context it needs — an entity, a tool, a position — and asks for it while
 * rolling. These nodes supply the position always and the entity and tool when given, which covers chest,
 * fishing, gift and most entity tables. A table wanting something else, a damage source say, will find it
 * missing part-way through; that is {@code ok = false} with whatever it managed to produce discarded,
 * rather than an exception escaping into the graph.
 *
 * <p>No vanilla table actually does this — vanilla's conditions and functions all read their parameters
 * through the null-tolerant accessor, so they quietly do nothing instead of complaining. The guard is there
 * for datapack and mod content, which is under no such discipline, and it is therefore not covered by a
 * test: there is nothing in the game to trigger it with.
 */
public final class LootNodes {

    private static final String GROUP = "mc/loot";

    private LootNodes() {
    }

    /**
     * One roll of a named loot table.
     *
     * <p>This is the node for treasure. Chest contents, fishing catches, villager gifts and mob drops are
     * all loot tables, and a command can put them in a container but cannot hand the items to a graph.
     *
     * <p>{@code entity} and {@code tool} are the context most tables ask for: an entity table needs to know
     * who died, a fishing table wants the rod. Leaving them empty is fine for a chest table and is what
     * makes those the simplest case.</p>
     */
    @NodeAttribute(name = "mc_loot_table_roll", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Roll extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_loot_table_roll.tooltip");
        }

        @InputPort public Level level;
        @InputPort public Identifier table;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public Entity entity;
        @InputPort public ItemStack tool = ItemStack.EMPTY;
        @OutputPort public List<?> out;
        @OutputPort public int count;
        @OutputPort public boolean found;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            ServerLevel world = serverLevel(ctx);
            Identifier id = ctx.getInput("table", Identifier.class, null);
            BlockPos pos = ctx.getInput("pos", BlockPos.class, BlockPos.ZERO);
            if (world == null || id == null || pos == null) {
                fail(ctx, false);
                return;
            }

            LootTable table = world.getServer().reloadableRegistries()
                    .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
            // An unknown id gives back the empty table rather than nothing, so identity against EMPTY is
            // how a missing table is told from one that legitimately rolled zero items.
            if (table == LootTable.EMPTY) {
                fail(ctx, false);
                return;
            }

            ItemStack heldTool = ctx.getInput("tool", ItemStack.class, ItemStack.EMPTY);
            LootParams params = new LootParams.Builder(world)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, ctx.getInput("entity", Entity.class, null))
                    .withOptionalParameter(LootContextParams.TOOL,
                            heldTool == null || heldTool.isEmpty() ? null : heldTool)
                    // EMPTY requires nothing, so building the parameters cannot fail here; a table that
                    // wants more than was supplied fails while rolling instead, below.
                    .create(LootContextParamSets.EMPTY);

            List<ItemStack> items;
            try {
                items = new ArrayList<>(table.getRandomItems(params));
            } catch (RuntimeException e) {
                fail(ctx, true);
                return;
            }
            ctx.setOutput("out", items);
            ctx.setOutput("count", items.size());
            ctx.setOutput("found", true);
            ctx.setOutput("ok", true);
        }
    }

    /**
     * What breaking the block at a position would drop.
     *
     * <p>Reads the block that is actually there, rather than taking a state, because the answer depends on
     * more than the state: a shulker box drops its contents, a spawner drops nothing, and both of those live
     * in the block entity. {@code mc_get_block_state} is the node for a graph that wanted the state instead.
     *
     * <p>The tool is the whole point. Stone with a pickaxe is cobblestone, stone with a hand is nothing, and
     * a silk-touched or fortune-enchanted tool changes the answer again.
     *
     * <h2>The harvest check is not in the loot table</h2>
     * {@code blocks/stone} has no condition about pickaxes in it — it says "silk touch gives stone,
     * otherwise cobblestone" and nothing more. What stops a bare hand getting cobblestone is a separate test
     * the game makes before it ever rolls the table, in {@code ServerPlayerGameMode.destroyBlock}. So this
     * node has to make that test too: rolling the table alone would confidently report cobblestone for a
     * bare-handed stone break, which is the wrong answer to the question the node's name asks.
     *
     * <p>{@code harvestable} is that test's result, exposed rather than hidden, because an empty list has
     * two meanings — nothing drops here, or you are holding the wrong thing — and only one of them is fixed
     * by finding a better tool.</p>
     */
    @NodeAttribute(name = "mc_block_drops", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BlockDrops extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_drops.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public ItemStack tool = ItemStack.EMPTY;
        @InputPort public Entity entity;
        @OutputPort public List<?> out;
        @OutputPort public int count;
        @OutputPort public BlockState state;
        @OutputPort public boolean harvestable;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            ServerLevel world = serverLevel(ctx);
            BlockPos pos = ctx.getInput("pos", BlockPos.class, BlockPos.ZERO);
            if (world == null || pos == null) {
                ctx.setOutput("out", List.of());
                ctx.setOutput("count", 0);
                ctx.setOutput("state", null);
                ctx.setOutput("harvestable", false);
                ctx.setOutput("ok", false);
                return;
            }

            BlockState at = world.getBlockState(pos);
            ItemStack heldTool = ctx.getInput("tool", ItemStack.class, ItemStack.EMPTY);
            if (heldTool == null) heldTool = ItemStack.EMPTY;
            boolean harvestable = !at.requiresCorrectToolForDrops() || heldTool.isCorrectToolForDrops(at);

            List<ItemStack> drops = harvestable
                    ? Block.getDrops(at, world, pos, world.getBlockEntity(pos),
                            ctx.getInput("entity", Entity.class, null), heldTool)
                    : List.of();
            ctx.setOutput("out", new ArrayList<>(drops));
            ctx.setOutput("count", drops.size());
            ctx.setOutput("state", at);
            ctx.setOutput("harvestable", harvestable);
            ctx.setOutput("ok", true);
        }
    }

    /** The {@code level} input, if it is a server world. Loot tables do not exist anywhere else. */
    @Nullable
    private static ServerLevel serverLevel(EvalContext ctx) {
        Level world = ctx.getInput("level", Level.class, null);
        return world instanceof ServerLevel server ? server : null;
    }

    /** Empty outputs. {@code found} says whether the table existed; the roll failed either way. */
    private static void fail(EvalContext ctx, boolean found) {
        ctx.setOutput("out", List.of());
        ctx.setOutput("count", 0);
        ctx.setOutput("found", found);
        ctx.setOutput("ok", false);
    }
}
