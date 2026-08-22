package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.BlockEntityActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.ContainerActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.EntityInteractionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.container.ContainerNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.container.FluidContainerNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.BlockEntityNbtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.EntityNbtNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector2f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * The container nodes, against real chests and a real player inventory.
 *
 * <p>These tests are integration-shaped by nature: an inventory is <em>resolved</em> by one node and
 * <em>used</em> by another, so every case wires a resolver into a reader or an action. That is also how a
 * graph will use them, so testing the pair together is testing the thing that ships.
 *
 * <p>Assertions read the chest back through vanilla's own {@code Container} interface rather than through
 * the capability the nodes used. If both sides went through {@code IItemHandler}, a bug in how this mod
 * wraps the inventory would agree with itself and the test would pass.
 */
@GameTestHolder(Kilagraph.MODID)
public final class McContainerGameTest {

    private McContainerGameTest() {
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void resolvesAChestAndReadsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chest = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        container(level, chest).setItem(2, new ItemStack(Items.DIAMOND, 5));

        var g = newGraph();
        var resolve = resolver(g, chest);
        var size = addNode(g, ContainerNodes.Size.class);
        wire(g, size.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        var get = addNode(g, ContainerNodes.Get.class);
        wire(g, get.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        setInputConstant(get, "slot", 2);
        var count = addNode(g, ContainerNodes.Count.class);
        wire(g, count.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        setInputConstant(count, "item", Items.DIAMOND);
        var find = addNode(g, ContainerNodes.Find.class);
        wire(g, find.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        setInputConstant(find, "item", Items.DIAMOND);

        var exec = executor(g, level);
        assertTrue(helper, "the chest resolved",
                exec.evaluate(resolve.getOutputsById().get("found"), Boolean.class));
        assertEq(helper, "a chest has 27 slots", 27,
                exec.evaluate(size.getOutputsById().get("slots"), Integer.class).intValue());
        assertEq(helper, "slot 2 holds diamonds", Items.DIAMOND,
                exec.evaluate(get.getOutputsById().get("out"), ItemStack.class).getItem());
        assertEq(helper, "five of them", 5,
                exec.evaluate(count.getOutputsById().get("count"), Integer.class).intValue());
        assertEq(helper, "found at slot 2", 2,
                exec.evaluate(find.getOutputsById().get("slot"), Integer.class).intValue());
        assertEq(helper, "and the first empty slot is 0", 0,
                exec.evaluate(find.getOutputsById().get("firstEmpty"), Integer.class).intValue());
        helper.succeed();
    }

    /** A block that is not a container resolves to nothing, and the readers degrade rather than throw. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aStoneBlockIsNotAContainer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);

        var g = newGraph();
        var resolve = resolver(g, stone);
        var size = addNode(g, ContainerNodes.Size.class);
        wire(g, size.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        var get = addNode(g, ContainerNodes.Get.class);
        wire(g, get.getInputsById().get("container"), resolve.getOutputsById().get("out"));

        var exec = executor(g, level);
        assertFalse(helper, "stone has no inventory",
                exec.evaluate(resolve.getOutputsById().get("found"), Boolean.class));
        assertEq(helper, "which reads as zero slots", 0,
                exec.evaluate(size.getOutputsById().get("slots"), Integer.class).intValue());
        assertTrue(helper, "and an empty stack",
                exec.evaluate(get.getOutputsById().get("out"), ItemStack.class).isEmpty());
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void insertsExtractsAndSets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chest = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);

        // --- insert ---
        var insert = runAction(level, chest, ContainerActionNodes.Insert.class,
                "stack", new ItemStack(Items.DIAMOND, 8));
        assertTrue(helper, "insert reported success", insert.ok());
        assertEq(helper, "and moved all eight", 8, insert.get("inserted", Integer.class).intValue());
        assertTrue(helper, "with nothing left over", insert.get("remainder", ItemStack.class).isEmpty());
        // Read back through vanilla, not through the capability the node used.
        assertEq(helper, "the chest really holds them", 8, container(level, chest).getItem(0).getCount());

        // --- simulate changes nothing ---
        var sim = runAction(level, chest, ContainerActionNodes.Insert.class,
                "stack", new ItemStack(Items.DIAMOND, 4), "simulate", true);
        assertTrue(helper, "a simulated insert reports what would happen", sim.ok());
        assertEq(helper, "but the chest is untouched", 8, container(level, chest).getItem(0).getCount());

        // --- extract ---
        var extract = runAction(level, chest, ContainerActionNodes.Extract.class, "slot", 0, "amount", 3);
        assertTrue(helper, "extract reported success", extract.ok());
        assertEq(helper, "and returned three", 3, extract.get("out", ItemStack.class).getCount());
        assertEq(helper, "leaving five behind", 5, container(level, chest).getItem(0).getCount());

        // --- set overwrites ---
        var set = runAction(level, chest, ContainerActionNodes.Set.class,
                "slot", 0, "stack", new ItemStack(Items.EMERALD, 2));
        assertTrue(helper, "set reported success", set.ok());
        assertEq(helper, "and the slot was overwritten", Items.EMERALD,
                container(level, chest).getItem(0).getItem());
        assertEq(helper, "with the given count", 2, container(level, chest).getItem(0).getCount());

        // --- failure paths ---
        assertFalse(helper, "extracting from an empty slot reports false",
                runAction(level, chest, ContainerActionNodes.Extract.class, "slot", 5, "amount", 1).ok());
        assertFalse(helper, "extracting from an out-of-range slot reports false",
                runAction(level, chest, ContainerActionNodes.Extract.class, "slot", 999, "amount", 1).ok());
        assertFalse(helper, "inserting nothing reports false",
                runAction(level, chest, ContainerActionNodes.Insert.class, "stack", ItemStack.EMPTY).ok());
        helper.succeed();
    }

    /** A full chest takes what it can and hands back the rest instead of losing it. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aFullChestReportsTheRemainder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chest = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        Container inv = container(level, chest);
        // Fill every slot with a stack that cannot take more.
        for (int i = 0; i < inv.getContainerSize(); i++) {
            inv.setItem(i, new ItemStack(Items.STONE, 64));
        }

        var insert = runAction(level, chest, ContainerActionNodes.Insert.class,
                "stack", new ItemStack(Items.DIAMOND, 4));
        assertFalse(helper, "nothing could be inserted", insert.ok());
        assertEq(helper, "and all four came back", 4,
                insert.get("remainder", ItemStack.class).getCount());
        assertEq(helper, "with nothing moved", 0, insert.get("inserted", Integer.class).intValue());
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aPlayerInventoryIsAContainer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var resolve = addNode(g, ContainerNodes.PlayerContainer.class);
        setInputConstant(resolve, "player", player);
        var size = addNode(g, ContainerNodes.Size.class);
        wire(g, size.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        var insert = addNode(g, ContainerActionNodes.Insert.class);
        wire(g, insert.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        setInputConstant(insert, "stack", new ItemStack(Items.GOLD_INGOT, 7));
        wire(g, insert.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        var exec = executor(g, level);
        exec.executeFrom(entry);

        assertTrue(helper, "the inventory resolved",
                exec.evaluate(resolve.getOutputsById().get("found"), Boolean.class));
        assertTrue(helper, "and has the inventory's slots, got "
                        + exec.evaluate(size.getOutputsById().get("slots"), Integer.class),
                exec.evaluate(size.getOutputsById().get("slots"), Integer.class) >= 36);
        assertTrue(helper, "the insert succeeded",
                exec.evaluate(insert.getOutputsById().get("ok"), Boolean.class));
        assertTrue(helper, "and the player really has the gold",
                player.getInventory().contains(new ItemStack(Items.GOLD_INGOT)));
        helper.succeed();
    }

    /**
     * Block entity NBT written by a graph survives a re-read.
     *
     * <p>The chest's contents are read as NBT from one chest and written into another, then the second
     * chest is asked what it holds — through vanilla, not through the tag. This is the test that fails if
     * {@code setChanged} or the block update is skipped, which is the whole risk in that node.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void blockEntityNbtRoundTrips(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos to = helper.absolutePos(new BlockPos(3, 2, 1));
        level.setBlock(from, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(to, Blocks.CHEST.defaultBlockState(), 3);
        container(level, from).setItem(4, new ItemStack(Items.REDSTONE, 11));

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var levelVar = declareLevel(g);

        var read = addNode(g, BlockEntityNbtNode.class);
        setInputConstant(read, "blockEntity", level.getBlockEntity(from));

        var write = addNode(g, BlockEntityActionNodes.SetNbt.class);
        wire(g, write.getInputsById().get("level"), levelVar);
        setInputConstant(write, "pos", to);
        wire(g, write.getInputsById().get("nbt"), read.getOutputsById().get("out"));
        wire(g, write.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        var exec = executor(g, level);
        exec.executeFrom(entry);

        assertTrue(helper, "the write reported success",
                exec.evaluate(write.getOutputsById().get("ok"), Boolean.class));
        var copied = container(level, to).getItem(4);
        assertEq(helper, "the second chest holds the copied item", Items.REDSTONE, copied.getItem());
        assertEq(helper, "with the same count", 11, copied.getCount());

        // Writing to a position with no block entity is refused, not a crash.
        BlockPos stone = helper.absolutePos(new BlockPos(5, 2, 1));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        var g2 = newGraph();
        var entry2 = addNode(g2, EntryNode.class);
        var write2 = addNode(g2, BlockEntityActionNodes.SetNbt.class);
        wire(g2, write2.getInputsById().get("level"), declareLevel(g2));
        setInputConstant(write2, "pos", stone);
        setInputConstant(write2, "nbt", new CompoundTag());
        wire(g2, write2.getInputsById().get("trigger"), entry2.getOutputsById().get("next"));
        var exec2 = executor(g2, level);
        exec2.executeFrom(entry2);
        assertFalse(helper, "a block with no block entity is refused",
                exec2.evaluate(write2.getOutputsById().get("ok"), Boolean.class));
        helper.succeed();
    }

    /**
     * Fluid tanks, against a cauldron.
     *
     * <p>A cauldron turns out to be a real fluid handler: NeoForge registers the capability for one, so
     * vanilla does have a tank to test against after all. The assertions are relational rather than
     * absolute — that draining yields water, that the cauldron empties, that filling it back fills it —
     * because a cauldron's exact capacity and level granularity are NeoForge's business and asserting
     * them would be testing NeoForge.
     *
     * <h2>The cauldron is filled to level 3 on purpose</h2>
     * {@code WATER_CAULDRON.defaultBlockState()} is one level of three, and a one-third-full cauldron is
     * <b>not drainable at all</b>: the wrapper can only move whole increments of
     * {@code totalAmount / gcd(maxLevel, totalAmount)}, which for 3 levels and 1000 mB is 1000 — a whole
     * bucket. So a graph pointed at a partly-filled cauldron gets nothing and a correct {@code ok = false},
     * which looks like a broken node until you know this. Recorded here because it cost a debugging round.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void readsAndDrainsACauldron(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cauldron = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3), 3);

        var g = newGraph();
        var resolve = addNode(g, FluidContainerNodes.BlockFluidContainer.class);
        wire(g, resolve.getInputsById().get("level"), declareLevel(g));
        setInputConstant(resolve, "pos", cauldron);
        var tanks = addNode(g, FluidContainerNodes.Tanks.class);
        wire(g, tanks.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        var get = addNode(g, FluidContainerNodes.Get.class);
        wire(g, get.getInputsById().get("container"), resolve.getOutputsById().get("out"));

        var exec = executor(g, level);
        assertTrue(helper, "a water cauldron is a fluid handler",
                exec.evaluate(resolve.getOutputsById().get("found"), Boolean.class));
        assertTrue(helper, "with at least one tank",
                exec.evaluate(tanks.getOutputsById().get("tanks"), Integer.class) >= 1);
        assertFalse(helper, "which is not empty",
                exec.evaluate(get.getOutputsById().get("empty"), Boolean.class));
        assertEq(helper, "and holds water", Fluids.WATER,
                exec.evaluate(get.getOutputsById().get("out"),
                        FluidStack.class).getFluid());
        assertTrue(helper, "with a capacity",
                exec.evaluate(get.getOutputsById().get("capacity"), Integer.class) > 0);

        // --- simulate never changes the world ---
        // Whether a cauldron's wrapper reports anything drainable under SIMULATE is NeoForge's business
        // and is not asserted; what is asserted is the property this node is responsible for, which is
        // that passing simulate cannot modify anything. The "simulate reports what would happen" case is
        // covered on a chest in insertsExtractsAndSets, where the handler is one that implements it.
        runFluid(level, cauldron, FluidContainerNodes.Drain.class, "amount", 1000, "simulate", true);
        assertEq(helper, "the cauldron still has its water after a simulated drain",
                Blocks.WATER_CAULDRON, level.getBlockState(cauldron).getBlock());

        // --- a real drain empties it ---
        var drain = runFluid(level, cauldron, FluidContainerNodes.Drain.class, "amount", 1000);
        assertTrue(helper, "the drain reported success", drain.ok());
        assertEq(helper, "and yielded water", Fluids.WATER,
                drain.get("out", FluidStack.class).getFluid());
        assertEq(helper, "and the cauldron is empty now", Blocks.CAULDRON,
                level.getBlockState(cauldron).getBlock());

        // --- and filling it puts the water back ---
        var fill = runFluid(level, cauldron, FluidContainerNodes.Fill.class,
                "fluid", new FluidStack(
                        Fluids.WATER, 1000));
        assertTrue(helper, "the fill reported success", fill.ok());
        assertTrue(helper, "and moved something", fill.get("filled", Integer.class) > 0);
        assertEq(helper, "and the cauldron holds water again", Blocks.WATER_CAULDRON,
                level.getBlockState(cauldron).getBlock());
        helper.succeed();
    }

    /** A block with no tank degrades to zero tanks and refusals, rather than crashing. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void fluidTanksDegradeOnBlocksThatHaveNone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);

        var g = newGraph();
        var resolve = addNode(g, FluidContainerNodes.BlockFluidContainer.class);
        wire(g, resolve.getInputsById().get("level"), declareLevel(g));
        setInputConstant(resolve, "pos", stone);
        var tanks = addNode(g, FluidContainerNodes.Tanks.class);
        wire(g, tanks.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        var get = addNode(g, FluidContainerNodes.Get.class);
        wire(g, get.getInputsById().get("container"), resolve.getOutputsById().get("out"));

        var exec = executor(g, level);
        assertFalse(helper, "stone is not a fluid handler",
                exec.evaluate(resolve.getOutputsById().get("found"), Boolean.class));
        assertEq(helper, "so it reports zero tanks", 0,
                exec.evaluate(tanks.getOutputsById().get("tanks"), Integer.class).intValue());
        assertTrue(helper, "and an empty reading",
                exec.evaluate(get.getOutputsById().get("empty"), Boolean.class));
        assertEq(helper, "with no capacity", 0,
                exec.evaluate(get.getOutputsById().get("capacity"), Integer.class).intValue());

        var fill = runFluid(level, stone, FluidContainerNodes.Fill.class,
                "fluid", new FluidStack(
                        Fluids.WATER, 1000));
        assertFalse(helper, "filling a non-tank is refused", fill.ok());
        assertEq(helper, "with nothing moved", 0, fill.get("filled", Integer.class).intValue());

        var drain = runFluid(level, stone, FluidContainerNodes.Drain.class, "amount", 1000);
        assertFalse(helper, "draining a non-tank is refused", drain.ok());
        assertTrue(helper, "and returns nothing",
                drain.get("out", FluidStack.class).isEmpty());
        helper.succeed();
    }

    /**
     * Entity NBT round-trips, and writing it does not move the entity.
     *
     * <p>The position guard is the part worth testing: {@code saveWithoutId} includes the coordinates, so
     * a naive load would teleport the entity to wherever the tag was captured as a side effect of setting
     * an unrelated field. Here the tag is taken from a pig at one place and written to a pig at another,
     * and the second pig must stay put.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void entityNbtRoundTripsWithoutTeleporting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var donor = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        var target = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 4));
        donor.setCustomName(Component.literal("Donor"));
        double targetX = target.getX();
        double targetZ = target.getZ();

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var read = addNode(g, EntityNbtNode.class);
        setInputConstant(read, "entity", donor);
        var write = addNode(g,
                EntityInteractionNodes.SetNbt.class);
        setInputConstant(write, "entity", target);
        wire(g, write.getInputsById().get("nbt"), read.getOutputsById().get("out"));
        wire(g, write.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        var exec = executor(g, level);
        exec.executeFrom(entry);

        assertTrue(helper, "the write reported success",
                exec.evaluate(write.getOutputsById().get("ok"), Boolean.class));
        assertEq(helper, "the state was copied", "Donor", target.getCustomName().getString());
        // The whole point: state moved, the entity did not.
        assertEq(helper, "and the target did not move in x", (float) targetX, (float) target.getX(), 0.01f);
        assertEq(helper, "nor in z", (float) targetZ, (float) target.getZ(), 0.01f);
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** Entry → resolve the fluid tank at {@code pos} → run {@code action} against it. */
    private static Result runFluid(ServerLevel level, BlockPos pos,
                                   Class<? extends Node> action, Object... inputs) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var resolve = addNode(g, FluidContainerNodes.BlockFluidContainer.class);
        wire(g, resolve.getInputsById().get("level"), declareLevel(g));
        setInputConstant(resolve, "pos", pos);
        var node = addNode(g, action);
        wire(g, node.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(node, (String) inputs[i], inputs[i + 1]);
        }
        wire(g, node.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        var exec = executor(g, level);
        exec.executeFrom(entry);
        return new Result(exec, node);
    }


    /** The chest's vanilla inventory, for asserting independently of the capability the nodes use. */
    private static Container container(ServerLevel level, BlockPos pos) {
        return (Container) level.getBlockEntity(pos);
    }

    private static PortModel declareLevel(BlueprintGraph g) {
        var v = (VariableDeclarationModelBase)
                g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
        return g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort();
    }

    /** A {@code mc_block_container} resolving the block at {@code pos}, with the level wired in. */
    private static NodeModel resolver(BlueprintGraph g, BlockPos pos) {
        var resolve = addNode(g, ContainerNodes.BlockContainer.class);
        wire(g, resolve.getInputsById().get("level"), declareLevel(g));
        setInputConstant(resolve, "pos", pos);
        setInputConstant(resolve, "side", Direction.UP);
        return resolve;
    }

    private static GraphExecutor executor(BlueprintGraph g, ServerLevel level) {
        return new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
    }

    private record Result(GraphExecutor exec, NodeModel node) {
        boolean ok() {
            return get("ok", Boolean.class);
        }

        <T> T get(String output, Class<T> type) {
            return exec.evaluate(node.getOutputsById().get(output), type);
        }
    }

    /** Entry → resolve the container at {@code pos} → run {@code action} against it. */
    private static Result runAction(ServerLevel level, BlockPos pos,
                                    Class<? extends Node> action, Object... inputs) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var resolve = resolver(g, pos);
        var node = addNode(g, action);
        wire(g, node.getInputsById().get("container"), resolve.getOutputsById().get("out"));
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(node, (String) inputs[i], inputs[i + 1]);
        }
        wire(g, node.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        var exec = executor(g, level);
        exec.executeFrom(entry);
        return new Result(exec, node);
    }
}
