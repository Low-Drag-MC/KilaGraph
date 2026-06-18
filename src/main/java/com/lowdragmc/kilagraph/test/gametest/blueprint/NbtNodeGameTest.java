package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.BlockEntityNbtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.ItemStackNbtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtHasNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtRemoveNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtSetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtValueType;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.joml.Vector2f;

import java.util.Map;

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
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{ROUND_TRIP_INT, ROUND_TRIP_STRING, HAS_AND_REMOVE, ITEM_STACK, BLOCK_ENTITY}) {
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
        var isn = addNode(g, ItemStackNbtNode.class);
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
}
