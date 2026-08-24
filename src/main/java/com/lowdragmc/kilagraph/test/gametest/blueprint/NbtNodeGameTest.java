package com.lowdragmc.kilagraph.test.gametest.blueprint;


import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.BlockEntityNbtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemStackNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtHasNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtPathGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtPathSetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtRemoveNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtSetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtValueType;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector2f;

import java.util.Map;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * NBT compound get/set/has/remove round-trips, plus reading NBT off a live {@link ItemStack} and
 * {@link BlockEntity}. Compound ports are wire-only, so a value source always starts at NbtCreate
 * (or a seeded wire-only variable for the MC-object cases).
 */
public final class NbtNodeGameTest {
    private static final String PATH_READS_NESTED_VALUES = "nbt_node_path_reads_nested_values";
    private static final String PATH_WRITES_AND_CREATES_PARENTS = "nbt_node_path_writes_and_creates_parents";
    private static final String ROUND_TRIP_INT = "nbt_round_trip_int";
    private static final String ROUND_TRIP_STRING = "nbt_round_trip_string";
    private static final String HAS_AND_REMOVE = "nbt_has_and_remove";
    private static final String ITEM_STACK = "nbt_item_stack";
    private static final String BLOCK_ENTITY = "nbt_block_entity";

    private NbtNodeGameTest() {}


    public static void registerFunctions() {
        KGGameTests.registerFunction(ROUND_TRIP_INT, NbtNodeGameTest::roundTripInt);
        KGGameTests.registerFunction(ROUND_TRIP_STRING, NbtNodeGameTest::roundTripString);
        KGGameTests.registerFunction(HAS_AND_REMOVE, NbtNodeGameTest::hasAndRemove);
        KGGameTests.registerFunction(ITEM_STACK, NbtNodeGameTest::itemStack);
        KGGameTests.registerFunction(BLOCK_ENTITY, NbtNodeGameTest::blockEntity);
        KGGameTests.registerFunction(PATH_READS_NESTED_VALUES, NbtNodeGameTest::pathReadsNestedValues);
        KGGameTests.registerFunction(PATH_WRITES_AND_CREATES_PARENTS, NbtNodeGameTest::pathWritesAndCreatesParents);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                ROUND_TRIP_INT, ROUND_TRIP_STRING, HAS_AND_REMOVE,
                ITEM_STACK, BLOCK_ENTITY, PATH_READS_NESTED_VALUES,
                PATH_WRITES_AND_CREATES_PARENTS
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    /** A wire-only variable get-node output, seeded from the env at execution time. */
    private static PortModel source(BlueprintGraph g, String name, TypeHandle type) {
        var v = (VariableDeclarationModelBase) g.graphModel.createVariable(name, type, null, VariableKind.INPUT);
        return g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort();
    }



    public static void roundTripInt(GameTestHelper helper) {
        var g = newGraph();
        var create = addNode(g, NbtCreateNode.class);
        var set = addNode(g, NbtSetNode.class);
        setOption(set, "valueType", NbtValueType.INT);
        setInputConstant(set, "key", "count");
        setInputConstant(set, "value", 42);
        wire(g, set.getInputsById().get("tag"), create.getOutputsById().get("out"));

        var get = addNode(g, NbtGetNode.class);
        setOption(get, "valueType", NbtValueType.INT);
        setInputConstant(get, "key", "count");
        wire(g, get.getInputsById().get("tag"), set.getOutputsById().get("out"));

        Integer v = new GraphExecutor(g).evaluate(get.getOutputsById().get("out"), Integer.class);
        assertEq(helper, "nbt int round-trip", 42, (int) v);
        helper.succeed();
    }



    public static void roundTripString(GameTestHelper helper) {
        var g = newGraph();
        var create = addNode(g, NbtCreateNode.class);
        var set = addNode(g, NbtSetNode.class);
        setOption(set, "valueType", NbtValueType.STRING);
        setInputConstant(set, "key", "name");
        setInputConstant(set, "value", "steve");
        wire(g, set.getInputsById().get("tag"), create.getOutputsById().get("out"));

        var get = addNode(g, NbtGetNode.class);
        setOption(get, "valueType", NbtValueType.STRING);
        setInputConstant(get, "key", "name");
        wire(g, get.getInputsById().get("tag"), set.getOutputsById().get("out"));

        String v = new GraphExecutor(g).evaluate(get.getOutputsById().get("out"), String.class);
        assertEq(helper, "nbt string round-trip", "steve", v);
        helper.succeed();
    }



    public static void hasAndRemove(GameTestHelper helper) {
        var g = newGraph();
        var create = addNode(g, NbtCreateNode.class);
        var set = addNode(g, NbtSetNode.class);
        setOption(set, "valueType", NbtValueType.INT);
        setInputConstant(set, "key", "foo");
        setInputConstant(set, "value", 1);
        wire(g, set.getInputsById().get("tag"), create.getOutputsById().get("out"));

        var has = addNode(g, NbtHasNode.class);
        setInputConstant(has, "key", "foo");
        wire(g, has.getInputsById().get("tag"), set.getOutputsById().get("out"));

        var hasMissing = addNode(g, NbtHasNode.class);
        setInputConstant(hasMissing, "key", "bar");
        wire(g, hasMissing.getInputsById().get("tag"), set.getOutputsById().get("out"));

        var remove = addNode(g, NbtRemoveNode.class);
        setInputConstant(remove, "key", "foo");
        wire(g, remove.getInputsById().get("tag"), set.getOutputsById().get("out"));

        var hasAfter = addNode(g, NbtHasNode.class);
        setInputConstant(hasAfter, "key", "foo");
        wire(g, hasAfter.getInputsById().get("tag"), remove.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        // Evaluate the pre-removal reads before the removal mutates the (shared) tag instance.
        assertTrue(helper, "has foo", exec.evaluate(has.getOutputsById().get("out"), Boolean.class));
        assertFalse(helper, "missing bar", exec.evaluate(hasMissing.getOutputsById().get("out"), Boolean.class));
        assertFalse(helper, "foo gone after remove", exec.evaluate(hasAfter.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }



    public static void itemStack(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        CompoundTag custom = new CompoundTag();
        custom.putInt("foo", 5);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));

        var g = newGraph();
        PortModel stackOut = source(g, "stack", TypeHandles.ITEM_STACK);
        var isn = addNode(g, ItemStackNodes.GetCustomData.class);
        wire(g, isn.getInputsById().get("stack"), stackOut);

        var get = addNode(g, NbtGetNode.class);
        setOption(get, "valueType", NbtValueType.INT);
        setInputConstant(get, "key", "foo");
        wire(g, get.getInputsById().get("tag"), isn.getOutputsById().get("out"));

        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("stack", stack)));
        Integer v = exec.evaluate(get.getOutputsById().get("out"), Integer.class);
        assertEq(helper, "itemstack custom_data foo", 5, (int) v);
        helper.succeed();
    }



    public static void blockEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chestPos = helper.absolutePos(new BlockPos(1, 2, 0));
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(chestPos);
        assertTrue(helper, "chest block entity present", be != null);

        var g = newGraph();
        PortModel beOut = source(g, "be", KGTypeHandles.BLOCK_ENTITY);
        var ben = addNode(g, BlockEntityNbtNode.class);
        wire(g, ben.getInputsById().get("blockEntity"), beOut);

        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("be", be)));
        CompoundTag tag = exec.evaluate(ben.getOutputsById().get("out"), CompoundTag.class);
        assertTrue(helper, "block entity nbt non-null", tag != null);
        helper.succeed();
    }

    // ---- paths ---------------------------------------------------------------------------------

    /**
     * {@code mc_nbt_path_get} against nested data the key-based node cannot reach at all.
     *
     * <p>The filter case ({@code Inventory[{id:"minecraft:stone"}]}) is the one that matters most: it can
     * only pass if the game's own path parser is doing the work, so it pins the node to {@code /data get}
     * syntax rather than to a homemade dotted split that would happen to handle the easy cases.</p>
     */
    public static void pathReadsNestedValues(GameTestHelper helper) {
        CompoundTag root = sample();

        assertEq(helper, "a nested string", "Excalibur",
                pathGet(helper, root, "display.Name", NbtValueType.STRING, String.class));
        assertEq(helper, "an element of a list", "minecraft:diamond",
                pathGet(helper, root, "Inventory[0].id", NbtValueType.STRING, String.class));
        assertEq(helper, "a number inside a list element", 7,
                pathGet(helper, root, "Inventory[1].Count", NbtValueType.INT, Integer.class).intValue());
        assertEq(helper, "a value picked out by a filter", 7,
                pathGet(helper, root, "Inventory[{id:\"minecraft:stone\"}].Count",
                        NbtValueType.INT, Integer.class).intValue());

        // NBT stores whichever width was written; reading an int as a double has to work.
        assertEq(helper, "an int read as a double", 42f,
                pathGet(helper, root, "Level", NbtValueType.DOUBLE, Double.class).floatValue(), 1e-6f);

        // A wildcard matches every element: out is the first, count says how many there were.
        var wildcard = pathNode(root, "Inventory[].id", NbtValueType.STRING);
        assertEq(helper, "the wildcard matched both slots", 2, eval(wildcard, "count", Integer.class).intValue());
        assertEq(helper, "and out is the first", "minecraft:diamond", eval(wildcard, "out", String.class));

        // A valid path that matches nothing: ok, but not found, and the type's zero on out.
        var absent = pathNode(root, "nope.nope", NbtValueType.STRING);
        assertTrue(helper, "the path itself parsed", eval(absent, "ok", Boolean.class));
        assertFalse(helper, "but matched nothing", eval(absent, "found", Boolean.class));
        assertEq(helper, "with a zero count", 0, eval(absent, "count", Integer.class).intValue());
        assertEq(helper, "and an empty string", "", eval(absent, "out", String.class));

        // Unparseable and empty paths are reported, not thrown — the path is typed by a player.
        //
        // Both malformed cases are here because the game throws two different things for them: "a..b" is
        // the declared CommandSyntaxException, while a path cut off mid-token reads past the end of the
        // string and throws StringIndexOutOfBoundsException. Only the first would be caught by a narrow
        // handler, and the truncated one is what a half-typed field actually contains.
        var truncated = pathNode(root, "Inventory[", NbtValueType.STRING);
        assertFalse(helper, "a path cut off mid-token is refused", eval(truncated, "ok", Boolean.class));
        assertFalse(helper, "and finds nothing", eval(truncated, "found", Boolean.class));

        var malformed = pathNode(root, "a..b", NbtValueType.STRING);
        assertFalse(helper, "an empty path element is refused", eval(malformed, "ok", Boolean.class));

        assertFalse(helper, "an empty path is refused too",
                eval(pathNode(root, "", NbtValueType.STRING), "ok", Boolean.class));
        helper.succeed();
    }

    /** {@code mc_nbt_path_set}, including the creation of missing parents and the aliasing it documents. */
    public static void pathWritesAndCreatesParents(GameTestHelper helper) {
        // Missing compounds are created on the way down, the way /data modify set does it.
        CompoundTag fresh = new CompoundTag();
        var deep = pathSet(fresh, "a.b.c", NbtValueType.STRING, "hi");
        assertTrue(helper, "writing a deep path worked", eval(deep, "ok", Boolean.class));
        assertEq(helper, "one value written", 1, eval(deep, "count", Integer.class).intValue());
        CompoundTag out = eval(deep, "out", CompoundTag.class);
        assertEq(helper, "and the parents were created", "hi",
                out.getCompoundOrEmpty("a").getCompoundOrEmpty("b").getStringOr("c", ""));
        assertTrue(helper, "the tag is mutated in place, not copied", out == fresh);

        // Overwriting an existing value.
        CompoundTag root = sample();
        var over = pathSet(root, "Level", NbtValueType.INT, 7);
        assertEq(helper, "one value overwritten", 1, eval(over, "count", Integer.class).intValue());
        assertEq(helper, "and it changed", 7, eval(over, "out", CompoundTag.class).getIntOr("Level", 0));

        // A wildcard writes every match at once, which is what count is for.
        CompoundTag many = sample();
        var all = pathSet(many, "Inventory[].Count", NbtValueType.INT, 1);
        assertEq(helper, "both slots written", 2, eval(all, "count", Integer.class).intValue());
        CompoundTag written = eval(all, "out", CompoundTag.class);
        assertEq(helper, "first slot", 1, written.getListOrEmpty("Inventory").getCompoundOrEmpty(0).getIntOr("Count", 0));
        assertEq(helper, "second slot", 1, written.getListOrEmpty("Inventory").getCompoundOrEmpty(1).getIntOr("Count", 0));

        // A list slot that does not exist cannot be created: nothing written, and it says so.
        CompoundTag untouched = sample();
        var outOfRange = pathSet(untouched, "Inventory[9].Count", NbtValueType.INT, 5);
        assertFalse(helper, "an out-of-range slot is refused", eval(outOfRange, "ok", Boolean.class));
        assertEq(helper, "having written nothing", 0, eval(outOfRange, "count", Integer.class).intValue());
        assertEq(helper, "and the list is unchanged", 2,
                eval(outOfRange, "out", CompoundTag.class).getListOrEmpty("Inventory").size());

        var broken = pathSet(sample(), "Inventory[", NbtValueType.INT, 5);
        assertFalse(helper, "a malformed path is refused", eval(broken, "ok", Boolean.class));
        assertEq(helper, "and writes nothing", 0, eval(broken, "count", Integer.class).intValue());
        helper.succeed();
    }

    // ---- path helpers --------------------------------------------------------------------------

    /** A tag with a nested compound, a list of compounds and a plain number. */
    private static CompoundTag sample() {
        CompoundTag root = new CompoundTag();
        CompoundTag display = new CompoundTag();
        display.putString("Name", "Excalibur");
        root.put("display", display);

        ListTag inventory = new ListTag();
        inventory.add(slot("minecraft:diamond", 3));
        inventory.add(slot("minecraft:stone", 7));
        root.put("Inventory", inventory);

        root.putInt("Level", 42);
        return root;
    }

    private static CompoundTag slot(String id, int count) {
        CompoundTag slot = new CompoundTag();
        slot.putString("id", id);
        slot.putInt("Count", count);
        return slot;
    }

    /** One node in its own graph, carried with the executor so its outputs can be read. */
    private record Probe(GraphExecutor exec, NodeModel node) {
    }

    private static <T> T eval(Probe probe, String output, Class<T> type) {
        return probe.exec().evaluate(probe.node().getOutputsById().get(output), type);
    }

    /** {@code mc_nbt_path_get} reading {@code path} out of {@code tag}. */
    private static Probe pathNode(CompoundTag tag, String path, NbtValueType type) {
        var g = newGraph();
        var n = addNode(g, NbtPathGetNode.class);
        setOption(n, "valueType", type);
        setInputConstant(n, "path", path);
        wire(g, n.getInputsById().get("tag"), source(g, "tag", KGTypeHandles.NBT_COMPOUND));
        return new Probe(new GraphExecutor(g, EvaluationEnvironment.with(Map.of("tag", tag))), n);
    }

    /** {@code mc_nbt_path_get}'s {@code out}, asserting on the way that the path matched at all. */
    private static <T> T pathGet(GameTestHelper helper, CompoundTag tag, String path,
                                 NbtValueType type, Class<T> as) {
        var probe = pathNode(tag, path, type);
        assertTrue(helper, path + " parsed", eval(probe, "ok", Boolean.class));
        assertTrue(helper, path + " matched something", eval(probe, "found", Boolean.class));
        return eval(probe, "out", as);
    }

    /** {@code mc_nbt_path_set} writing {@code value} at {@code path}. */
    private static Probe pathSet(CompoundTag tag, String path, NbtValueType type, Object value) {
        var g = newGraph();
        var n = addNode(g, NbtPathSetNode.class);
        setOption(n, "valueType", type);
        setInputConstant(n, "path", path);
        setInputConstant(n, "value", value);
        wire(g, n.getInputsById().get("tag"), source(g, "tag", KGTypeHandles.NBT_COMPOUND));
        return new Probe(new GraphExecutor(g, EvaluationEnvironment.with(Map.of("tag", tag))), n);
    }
}
