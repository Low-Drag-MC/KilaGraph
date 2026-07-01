package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.GetBlockEntityNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.GetBlockNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.GetBlockStateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.IsEmptyBlockNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
 * World-query nodes against a real {@link ServerLevel} from the GameTest. The {@code Level} reaches
 * the nodes the framework-supported way: a wire-only graph variable seeded with the live level (the
 * executor itself never knows about the world).
 */
@GameTestHolder(Kilagraph.MODID)
public final class McWorldQueryGameTest {
    private static final String READ_BLOCK = "mc_world_read_block";
    private static final String EMPTY_AND_BE = "mc_world_empty_and_block_entity";

    private McWorldQueryGameTest() {}

    /** Declares a wire-only "level" INPUT variable, returns its get-node output port. */
    private static PortModel levelSource(BlueprintGraph g) {
        var v = (VariableDeclarationModelBase)
                g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
        return g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort();
    }

    private static GraphExecutor execWithLevel(BlueprintGraph g, ServerLevel level) {
        return new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level)));
    }

    /** setBlock(stone) then GetBlock / GetBlockState / IsEmptyBlock at that pos. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void readBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(0, 2, 0));
        level.setBlock(abs, Blocks.STONE.defaultBlockState(), 3);

        var g = newGraph();
        PortModel levelOut = levelSource(g);

        var getBlock = addNode(g, GetBlockNode.class);
        wire(g, getBlock.getInputsById().get("level"), levelOut);
        setInputConstant(getBlock, "pos", abs);

        var getState = addNode(g, GetBlockStateNode.class);
        wire(g, getState.getInputsById().get("level"), levelOut);
        setInputConstant(getState, "pos", abs);

        var isEmpty = addNode(g, IsEmptyBlockNode.class);
        wire(g, isEmpty.getInputsById().get("level"), levelOut);
        setInputConstant(isEmpty, "pos", abs);

        var exec = execWithLevel(g, level);
        assertEq(helper, "GetBlock", Blocks.STONE,
                exec.evaluate(getBlock.getOutputsById().get("out"), net.minecraft.world.level.block.Block.class));
        BlockState state = exec.evaluate(getState.getOutputsById().get("out"), BlockState.class);
        assertTrue(helper, "GetBlockState non-null", state != null);
        assertEq(helper, "state.block", Blocks.STONE, state.getBlock());
        assertFalse(helper, "stone not empty",
                exec.evaluate(isEmpty.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    /** IsEmptyBlock true over air; GetBlockEntity non-null on a chest, null on stone. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void emptyAndBlockEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos air = helper.absolutePos(new BlockPos(0, 6, 0));
        BlockPos chestPos = helper.absolutePos(new BlockPos(1, 2, 0));
        BlockPos stonePos = helper.absolutePos(new BlockPos(2, 2, 0));
        level.setBlock(air, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), 3);

        var g = newGraph();
        PortModel levelOut = levelSource(g);

        var emptyAir = addNode(g, IsEmptyBlockNode.class);
        wire(g, emptyAir.getInputsById().get("level"), levelOut);
        setInputConstant(emptyAir, "pos", air);

        var beChest = addNode(g, GetBlockEntityNode.class);
        wire(g, beChest.getInputsById().get("level"), levelOut);
        setInputConstant(beChest, "pos", chestPos);

        var beStone = addNode(g, GetBlockEntityNode.class);
        wire(g, beStone.getInputsById().get("level"), levelOut);
        setInputConstant(beStone, "pos", stonePos);

        var exec = execWithLevel(g, level);
        assertTrue(helper, "air is empty",
                exec.evaluate(emptyAir.getOutputsById().get("out"), Boolean.class));
        BlockEntity chestBe = exec.evaluate(beChest.getOutputsById().get("out"), BlockEntity.class);
        assertTrue(helper, "chest has block entity", chestBe != null);
        BlockEntity stoneBe = exec.evaluate(beStone.getOutputsById().get("out"), BlockEntity.class);
        assertEq(helper, "stone has no block entity", null, stoneBe);
        helper.succeed();
    }
}
