package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.blueprint.nodes.mc.BlockConstNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.BlockPosCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.BlockToItemNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.DirectionAxisNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.DirectionOppositeNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.ItemConstNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.ItemInTagNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.ItemStackCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.ItemToBlockNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.info.BlockPosInfoNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.info.InfoFieldBlock;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.info.ItemStackInfoNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/** Pure-data MC nodes: construct/destructure, conversions, tags. */
public final class McDataGameTest {
    private static final String BLOCK_POS_ROUND_TRIP = "mc_block_pos_round_trip";
    private static final String ITEM_STACK_CREATE_READ = "mc_item_stack_create_read";
    private static final String BLOCK_ITEM_ROUND_TRIP = "mc_block_item_round_trip";
    private static final String DIRECTION_OPS = "mc_direction_ops";
    private static final String ITEM_IN_TAG = "mc_item_in_tag_test";

    private McDataGameTest() {}

    public static void registerFunctions() {
        KGGameTests.registerFunction(BLOCK_POS_ROUND_TRIP, McDataGameTest::blockPosRoundTrip);
        KGGameTests.registerFunction(ITEM_STACK_CREATE_READ, McDataGameTest::itemStackCreateRead);
        KGGameTests.registerFunction(BLOCK_ITEM_ROUND_TRIP, McDataGameTest::blockItemRoundTrip);
        KGGameTests.registerFunction(DIRECTION_OPS, McDataGameTest::directionOps);
        KGGameTests.registerFunction(ITEM_IN_TAG, McDataGameTest::itemInTag);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{BLOCK_POS_ROUND_TRIP, ITEM_STACK_CREATE_READ, BLOCK_ITEM_ROUND_TRIP,
                DIRECTION_OPS, ITEM_IN_TAG}) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    /** BlockPosCreate(3,4,5) → BlockPos info context + x/y/z blocks read it back. */
    public static void blockPosRoundTrip(GameTestHelper helper) {
        var g = newGraph();
        var create = addNode(g, BlockPosCreateNode.class);
        setInputConstant(create, "x", 3);
        setInputConstant(create, "y", 4);
        setInputConstant(create, "z", 5);

        var ctx = addNode(g, BlockPosInfoNode.class);
        wire(g, ctx.getInputsById().get("target"), create.getOutputsById().get("out"));
        var xBlock = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(xBlock, "field", "x");
        var yBlock = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(yBlock, "field", "y");
        var zBlock = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(zBlock, "field", "z");

        var exec = new GraphExecutor(g);
        BlockPos pos = exec.evaluate(create.getOutputsById().get("out"), BlockPos.class);
        assertEq(helper, "BlockPos", new BlockPos(3, 4, 5), pos);
        assertEq(helper, "info.x", 3, ((Number) exec.evaluate(xBlock.getOutputsById().get("value"), Object.class)).intValue());
        assertEq(helper, "info.y", 4, ((Number) exec.evaluate(yBlock.getOutputsById().get("value"), Object.class)).intValue());
        assertEq(helper, "info.z", 5, ((Number) exec.evaluate(zBlock.getOutputsById().get("value"), Object.class)).intValue());
        helper.succeed();
    }

    /** ItemStackCreate(diamond, 5) → ItemStack info context + count/maxStackSize/isEmpty blocks. */
    public static void itemStackCreateRead(GameTestHelper helper) {
        var g = newGraph();
        var create = addNode(g, ItemStackCreateNode.class);
        setInputConstant(create, "item", Items.DIAMOND);
        setInputConstant(create, "count", 5);

        var ctx = addNode(g, ItemStackInfoNode.class);
        wire(g, ctx.getInputsById().get("target"), create.getOutputsById().get("out"));
        var countBlock = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(countBlock, "field", "count");
        var maxBlock = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(maxBlock, "field", "maxStackSize");
        var emptyBlock = addBlock(g, ctx, InfoFieldBlock.class);
        setOption(emptyBlock, "field", "isEmpty");

        var exec = new GraphExecutor(g);
        assertEq(helper, "count", 5, ((Number) exec.evaluate(countBlock.getOutputsById().get("value"), Object.class)).intValue());
        assertEq(helper, "maxStackSize", 64, ((Number) exec.evaluate(maxBlock.getOutputsById().get("value"), Object.class)).intValue());
        assertFalse(helper, "isEmpty", (Boolean) exec.evaluate(emptyBlock.getOutputsById().get("value"), Object.class));
        helper.succeed();
    }

    /** BlockConst(stone) → BlockToItem → ItemToBlock round-trips back to stone. */
    public static void blockItemRoundTrip(GameTestHelper helper) {
        var g = newGraph();
        var blockConst = addNode(g, BlockConstNode.class);
        setOption(blockConst, "value", Blocks.STONE);
        var toItem = addNode(g, BlockToItemNode.class);
        wire(g, toItem.getInputsById().get("in"), blockConst.getOutputsById().get("out"));
        var toBlock = addNode(g, ItemToBlockNode.class);
        wire(g, toBlock.getInputsById().get("in"), toItem.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        Item item = exec.evaluate(toItem.getOutputsById().get("out"), Item.class);
        Block block = exec.evaluate(toBlock.getOutputsById().get("out"), Block.class);
        assertEq(helper, "block→item", Blocks.STONE.asItem(), item);
        assertEq(helper, "item→block", Blocks.STONE, block);
        helper.succeed();
    }

    /** DirectionOpposite(EAST)=WEST; DirectionAxis(UP)="y". */
    public static void directionOps(GameTestHelper helper) {
        var g = newGraph();
        var opp = addNode(g, DirectionOppositeNode.class);
        setInputConstant(opp, "in", Direction.EAST);
        var axis = addNode(g, DirectionAxisNode.class);
        setInputConstant(axis, "in", Direction.UP);

        var exec = new GraphExecutor(g);
        assertEq(helper, "opposite", Direction.WEST, exec.evaluate(opp.getOutputsById().get("out"), Direction.class));
        assertEq(helper, "axis", "y", exec.evaluate(axis.getOutputsById().get("out"), String.class));
        helper.succeed();
    }

    /** ItemConst(oak planks) in tag minecraft:planks → true; diamond → false. */
    public static void itemInTag(GameTestHelper helper) {
        var g = newGraph();
        var planks = addNode(g, ItemConstNode.class);
        setOption(planks, "value", Items.OAK_PLANKS);
        var inTag = addNode(g, ItemInTagNode.class);
        setInputConstant(inTag, "tag", "minecraft:planks");
        wire(g, inTag.getInputsById().get("item"), planks.getOutputsById().get("out"));

        var diamond = addNode(g, ItemConstNode.class);
        setOption(diamond, "value", Items.DIAMOND);
        var notInTag = addNode(g, ItemInTagNode.class);
        setInputConstant(notInTag, "tag", "minecraft:planks");
        wire(g, notInTag.getInputsById().get("item"), diamond.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        assertTrue(helper, "planks in #planks", exec.evaluate(inTag.getOutputsById().get("out"), Boolean.class));
        assertFalse(helper, "diamond not in #planks", exec.evaluate(notInTag.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }
}
