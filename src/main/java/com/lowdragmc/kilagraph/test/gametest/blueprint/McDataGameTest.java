package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.BlockPosCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.BlockToItemNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.tag.ItemInTagNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemStackCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemToBlockNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.BlockPosNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemStackNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/** Pure-data MC nodes: construct/destructure, conversions, tags. */
@GameTestHolder(Kilagraph.MODID)
public final class McDataGameTest {
    private static final String BLOCK_POS_ROUND_TRIP = "mc_block_pos_round_trip";
    private static final String ITEM_STACK_CREATE_READ = "mc_item_stack_create_read";
    private static final String BLOCK_ITEM_ROUND_TRIP = "mc_block_item_round_trip";
    private static final String DIRECTION_OPS = "mc_direction_ops";
    private static final String ITEM_IN_TAG = "mc_item_in_tag_test";

    private McDataGameTest() {}

    /**
     * BlockPosCreate(3,4,5) → mc_block_pos_unpack reads it back.
     *
     * <p>Used to go through {@code info_block_pos} + three Field blocks. A {@code BlockPos} is three
     * integers, so it no longer has a reflective context at all — this is the round trip it always was,
     * against the node that replaced it.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void blockPosRoundTrip(GameTestHelper helper) {
        var g = newGraph();
        var create = addNode(g, BlockPosCreateNode.class);
        setInputConstant(create, "x", 3);
        setInputConstant(create, "y", 4);
        setInputConstant(create, "z", 5);

        var unpack = addNode(g, BlockPosNodes.Unpack.class);
        wire(g, unpack.getInputsById().get("in"), create.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        BlockPos pos = exec.evaluate(create.getOutputsById().get("out"), BlockPos.class);
        assertEq(helper, "BlockPos", new BlockPos(3, 4, 5), pos);
        assertEq(helper, "unpack.x", 3, exec.evaluate(unpack.getOutputsById().get("x"), Integer.class).intValue());
        assertEq(helper, "unpack.y", 4, exec.evaluate(unpack.getOutputsById().get("y"), Integer.class).intValue());
        assertEq(helper, "unpack.z", 5, exec.evaluate(unpack.getOutputsById().get("z"), Integer.class).intValue());
        helper.succeed();
    }

    /** ItemStackCreate(diamond, 5) → mc_item_stack_unpack and mc_item_stack_limits read it back. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemStackCreateRead(GameTestHelper helper) {
        var g = newGraph();
        var create = addNode(g, ItemStackCreateNode.class);
        setInputConstant(create, "item", Items.DIAMOND);
        setInputConstant(create, "count", 5);

        var unpack = addNode(g, ItemStackNodes.Unpack.class);
        wire(g, unpack.getInputsById().get("stack"), create.getOutputsById().get("out"));
        var limits = addNode(g, ItemStackNodes.Limits.class);
        wire(g, limits.getInputsById().get("stack"), create.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        assertEq(helper, "item", Items.DIAMOND, exec.evaluate(unpack.getOutputsById().get("item"), Item.class));
        assertEq(helper, "count", 5, exec.evaluate(unpack.getOutputsById().get("count"), Integer.class).intValue());
        assertFalse(helper, "empty", exec.evaluate(unpack.getOutputsById().get("empty"), Boolean.class));
        assertEq(helper, "maxStackSize", 64,
                exec.evaluate(limits.getOutputsById().get("maxStackSize"), Integer.class).intValue());
        assertTrue(helper, "stackable", exec.evaluate(limits.getOutputsById().get("stackable"), Boolean.class));
        helper.succeed();
    }

    /** stone → BlockToItem → ItemToBlock round-trips back to stone. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void blockItemRoundTrip(GameTestHelper helper) {
        var g = newGraph();
        // The block comes from the port's own constant. A dedicated "block constant" node used to
        // sit here; a plain Block constant node, or this constant, does the same thing.
        var toItem = addNode(g, BlockToItemNode.class);
        setInputConstant(toItem, "in", Blocks.STONE);
        var toBlock = addNode(g, ItemToBlockNode.class);
        wire(g, toBlock.getInputsById().get("in"), toItem.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        Item item = exec.evaluate(toItem.getOutputsById().get("out"), Item.class);
        Block block = exec.evaluate(toBlock.getOutputsById().get("out"), Block.class);
        assertEq(helper, "block→item", Blocks.STONE.asItem(), item);
        assertEq(helper, "item→block", Blocks.STONE, block);
        helper.succeed();
    }


    /** Oak planks are in minecraft:planks; a diamond is not. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemInTag(GameTestHelper helper) {
        var g = newGraph();
        var inTag = addNode(g, ItemInTagNode.class);
        setInputConstant(inTag, "tag", "minecraft:planks");
        setInputConstant(inTag, "item", Items.OAK_PLANKS);

        var notInTag = addNode(g, ItemInTagNode.class);
        setInputConstant(notInTag, "tag", "minecraft:planks");
        setInputConstant(notInTag, "item", Items.DIAMOND);

        var exec = new GraphExecutor(g);
        assertTrue(helper, "planks in #planks", exec.evaluate(inTag.getOutputsById().get("out"), Boolean.class));
        assertFalse(helper, "diamond not in #planks", exec.evaluate(notInTag.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }
}
