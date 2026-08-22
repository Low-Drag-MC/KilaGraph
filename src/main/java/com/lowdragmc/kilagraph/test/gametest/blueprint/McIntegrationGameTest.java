package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForEachNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.BlockActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.EntityActionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.WorldEffectNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.block.BlockStateNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityDataNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.BlockPosCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.BlockPosNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemStackCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemStackNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.text.TextNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.GetBlockStateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.WorldQueryNodes;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector2f;

import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Whole graphs running against a live {@link ServerLevel}.
 *
 * <p>Every other MC test in this suite drives one node at a time with constant inputs. That finds a node
 * that computes the wrong value; it cannot find the things that only go wrong when the pieces are
 * assembled — a loop whose body reads a stale cached value, an action that never re-resolves its position,
 * a list of entities that goes flat after the first iteration. These tests build the graph a user would
 * build and then assert on <b>the world</b>, not on a port.
 *
 * <h2>The one worth reading first</h2>
 * {@link #buildsAColumn} exists because of a specific failure mode. The executor memoises data values, so
 * a loop body whose position depends on the loop index is only correct if the cache is invalidated per
 * iteration. If it were not, all five blocks would land on the same spot and every per-node test would
 * still pass. That is the class of bug integration coverage is for.
 */
@GameTestHolder(Kilagraph.MODID)
public final class McIntegrationGameTest {

    private McIntegrationGameTest() {
    }

    /**
     * A For loop building a column, with the height computed from the loop index.
     *
     * <p>Entry → For(5) → body → Set Block, where the position comes from
     * {@code index + baseY} through Add and Block Pos Create. Five distinct blocks, five distinct
     * positions, one graph.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void buildsAColumn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(0, 2, 0));
        for (int i = 0; i < 5; i++) {
            level.setBlock(base.above(i), Blocks.AIR.defaultBlockState(), 3);
        }

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var loop = addNode(g, ForNode.class);
        setInputConstant(loop, "count", 5);

        // y = index + base.getY()
        var add = addNode(g, AddNode.class);
        wire(g, add.getInputsById().get("in1"), loop.getOutputsById().get("index"));
        setInputConstant(add, "in2", base.getY());

        var pos = addNode(g, BlockPosCreateNode.class);
        setInputConstant(pos, "x", base.getX());
        setInputConstant(pos, "z", base.getZ());
        wire(g, pos.getInputsById().get("y"), add.getOutputsById().get("out"));

        var set = addNode(g, BlockActionNodes.SetBlock.class);
        wire(g, set.getInputsById().get("pos"), pos.getOutputsById().get("out"));
        setInputConstant(set, "state", Blocks.GOLD_BLOCK.defaultBlockState());

        var levelVar = declareLevel(g);
        wire(g, set.getInputsById().get("level"), levelVar);
        wire(g, loop.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, set.getInputsById().get("trigger"), loop.getOutputsById().get("body"));

        run(g, level, entry);

        // Every one of the five, at its own height — this is the assertion that a stale cache breaks.
        for (int i = 0; i < 5; i++) {
            assertEq(helper, "column block at +" + i, Blocks.GOLD_BLOCK,
                    level.getBlockState(base.above(i)).getBlock());
        }
        assertTrue(helper, "and nothing above the column",
                level.getBlockState(base.above(5)).isAir());
        helper.succeed();
    }

    /**
     * A world query feeding a For Each that acts on some items and not others.
     *
     * <p>Entities In Box → For Each → Is Type(pig) → Branch → Damage. Two entities go in, one comes out
     * hurt. This is the shape almost every real blueprint has, and it exercises the loop item flowing into
     * two separate nodes in the body.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void damagesOnlyTheMatchingEntities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingEntity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        LivingEntity cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        float pigFull = pig.getHealth();
        float cowFull = cow.getHealth();

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var levelVar = declareLevel(g);

        var inBox = addNode(g, WorldQueryNodes.EntitiesInBox.class);
        wire(g, inBox.getInputsById().get("level"), levelVar);
        setInputConstant(inBox, "box", new AABB(pig.blockPosition()).inflate(16));

        var each = addNode(g, ForEachNode.class);
        wire(g, each.getInputsById().get("list"), inBox.getOutputsById().get("out"));

        var isPig = addNode(g, EntityDataNodes.IsType.class);
        wire(g, isPig.getInputsById().get("entity"), each.getOutputsById().get("item"));
        setInputConstant(isPig, "type", EntityType.PIG);

        var branch = addNode(g, BranchNode.class);
        wire(g, branch.getInputsById().get("cond"), isPig.getOutputsById().get("out"));

        var damage = addNode(g, EntityActionNodes.DamageEntity.class);
        wire(g, damage.getInputsById().get("entity"), each.getOutputsById().get("item"));
        setInputConstant(damage, "amount", 4f);

        wire(g, each.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, branch.getInputsById().get("in"), each.getOutputsById().get("body"));
        wire(g, damage.getInputsById().get("trigger"), branch.getOutputsById().get("trueExec"));

        run(g, level, entry);

        assertTrue(helper, "the pig was hurt, was " + pigFull + " now " + pig.getHealth(),
                pig.getHealth() < pigFull);
        assertEq(helper, "the cow was not touched", cowFull, cow.getHealth(), 0.01f);
        helper.succeed();
    }

    /**
     * Reading the world, deciding, and writing back — in one pass over a region.
     *
     * <p>Block Pos Between → For Each → Get Block State → Flags → Branch on {@code air} → Set Block. The
     * region is seeded half stone and half air, and only the air is filled. This is the read-modify-write
     * loop, and it is the one where a cached block state would produce a visibly wrong result: the first
     * position's answer applied to all four.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void fillsOnlyTheAirInARegion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos max = min.offset(3, 0, 0);
        // stone, air, stone, air
        level.setBlock(min, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(min.offset(1, 0, 0), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(min.offset(2, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(min.offset(3, 0, 0), Blocks.AIR.defaultBlockState(), 3);

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var levelVar = declareLevel(g);

        var between = addNode(g, BlockPosNodes.Between.class);
        setInputConstant(between, "min", min);
        setInputConstant(between, "max", max);

        var each = addNode(g, ForEachNode.class);
        wire(g, each.getInputsById().get("list"), between.getOutputsById().get("out"));

        var getState = addNode(g, GetBlockStateNode.class);
        wire(g, getState.getInputsById().get("level"), levelVar);
        wire(g, getState.getInputsById().get("pos"), each.getOutputsById().get("item"));

        var flags = addNode(g, BlockStateNodes.Flags.class);
        wire(g, flags.getInputsById().get("in"), getState.getOutputsById().get("out"));

        var branch = addNode(g, BranchNode.class);
        wire(g, branch.getInputsById().get("cond"), flags.getOutputsById().get("air"));

        var set = addNode(g, BlockActionNodes.SetBlock.class);
        wire(g, set.getInputsById().get("level"), levelVar);
        wire(g, set.getInputsById().get("pos"), each.getOutputsById().get("item"));
        setInputConstant(set, "state", Blocks.GOLD_BLOCK.defaultBlockState());

        wire(g, each.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, branch.getInputsById().get("in"), each.getOutputsById().get("body"));
        wire(g, set.getInputsById().get("trigger"), branch.getOutputsById().get("trueExec"));

        run(g, level, entry);

        assertEq(helper, "stone at 0 untouched", Blocks.STONE, level.getBlockState(min).getBlock());
        assertEq(helper, "air at 1 filled", Blocks.GOLD_BLOCK,
                level.getBlockState(min.offset(1, 0, 0)).getBlock());
        assertEq(helper, "stone at 2 untouched", Blocks.STONE,
                level.getBlockState(min.offset(2, 0, 0)).getBlock());
        assertEq(helper, "air at 3 filled", Blocks.GOLD_BLOCK,
                level.getBlockState(min.offset(3, 0, 0)).getBlock());
        helper.succeed();
    }

    /**
     * A data component written by the graph, surviving into a world entity.
     *
     * <p>Item Stack Create → Set Custom Name → Drop Item, then the dropped entity is read back out of the
     * world and asked what it is holding. The component has to survive the copy the action makes and the
     * {@code ItemEntity}'s own handling of the stack, which no per-node test covers.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void itemComponentsSurviveIntoTheWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));

        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var levelVar = declareLevel(g);

        var stack = addNode(g, ItemStackCreateNode.class);
        setInputConstant(stack, "item", Items.DIAMOND);
        setInputConstant(stack, "count", 3);

        var text = addNode(g, TextNodes.Literal.class);
        setInputConstant(text, "text", "Graph Diamond");

        var named = addNode(g, ItemStackNodes.SetCustomName.class);
        wire(g, named.getInputsById().get("stack"), stack.getOutputsById().get("out"));
        wire(g, named.getInputsById().get("name"), text.getOutputsById().get("out"));

        var drop = addNode(g, WorldEffectNodes.DropItem.class);
        wire(g, drop.getInputsById().get("level"), levelVar);
        setInputConstant(drop, "pos", at);
        wire(g, drop.getInputsById().get("stack"), named.getOutputsById().get("out"));

        wire(g, drop.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        var exec = run(g, level, entry);

        assertTrue(helper, "the drop succeeded",
                exec.evaluate(drop.getOutputsById().get("ok"), Boolean.class));
        Entity dropped = exec.evaluate(drop.getOutputsById().get("entity"), Entity.class);
        assertTrue(helper, "and produced an item entity", dropped instanceof ItemEntity);

        var inWorld = ((ItemEntity) dropped).getItem();
        assertEq(helper, "holding diamonds", Items.DIAMOND, inWorld.getItem());
        assertEq(helper, "three of them", 3, inWorld.getCount());
        assertEq(helper, "with the custom name the graph gave it", "Graph Diamond",
                inWorld.getHoverName().getString());

        // The entity really is in the world, not just constructed.
        assertFalse(helper, "and it is not removed", dropped.isRemoved());
        assertTrue(helper, "and the world can find it",
                level.getEntities(null, new AABB(at).inflate(2)).contains(dropped));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** Declares the {@code level} INPUT variable and returns its node's output port, ready to wire. */
    private static PortModel declareLevel(BlueprintGraph g) {
        var v = (VariableDeclarationModelBase)
                g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
        return g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort();
    }

    /** Runs the flow from {@code entry} with the level seeded on the environment. */
    private static GraphExecutor run(BlueprintGraph g, ServerLevel level, NodeModel entry) {
        var exec = new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
        exec.executeFrom(entry);
        return exec;
    }
}
