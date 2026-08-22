package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityInfoNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGBench;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;

/**
 * What a context block costs the graph around it.
 *
 * <h2>What used to be here, and why it is gone</h2>
 * This file measured a property read two ways — a standalone node against the same property as a block
 * inside a context. Those shapes needed a duplicate node to compare against, and the seven duplicate
 * {@code mc_entity_*} nodes were deleted once the context form was settled on, so the comparison has no
 * second side left. Its results are in {@code docs/bench-baseline.md}: <b>34 vs 83 ns</b> for one
 * property, and <b>187 vs 255 ns</b> for four — the context costs about 30 ns per read and, contrary to
 * the prediction made before measuring, does <em>not</em> amortise across properties, because the cost is
 * reaching the parent's {@code target} rather than resolving it.
 *
 * <p>Two earlier findings from the reflective {@code info_field} era are recorded there too, both easy to
 * get wrong again: {@code Method.invoke} on a warm monomorphic call site is within <b>1.6 ns</b> of a
 * direct call, so reflection was never the cost; and a boxing benchmark on a small {@code int} measures
 * {@code Integer.valueOf}'s −128..127 cache rather than boxing.
 *
 * <p>As everywhere in this suite, timing is logged and never asserted — the assertions are on the values.
 */
@GameTestHolder(Kilagraph.MODID)
public final class InfoNodeBenchGameTest {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private InfoNodeBenchGameTest() {
    }

    /**
     * What one context block costs the rest of the graph.
     *
     * <p>The block is left <b>unconnected</b> on purpose: it takes no part in the chain being measured, so
     * any difference is the structural cost of its mere presence rather than the cost of reading it.
     * {@code PreparedGraph.detectCycle} gives up static cycle detection for any graph containing a block
     * node, so every node keeps paying the visiting-stack bookkeeping.
     *
     * <p>This has never been measurable: −3 ns on a 16-node chain, then +11, −118 and −156 on 64-node
     * ones, every one sign-unstable. <b>The chain length is the argument</b> — a genuine per-node cost
     * would have grown roughly fourfold from 16 to 64 nodes and did not. Kept because it was once cited as
     * a design justification before anyone measured it.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 6000)
    @PrefixGameTestTemplate(false)
    public static void wholeGraphCostOfHavingOne(GameTestHelper helper) {
        int chain = 64;

        var plain = KGGraphFixtures.monomorphicChain(chain);
        var plainExec = new GraphExecutor(plain.graph());
        PortModel plainOut = plain.outputOf("u" + (chain - 1));

        var withBlock = KGGraphFixtures.monomorphicChain(chain);
        NodeModel context = addNode(withBlock.graph(), EntityInfoNode.class);
        addBlock(withBlock.graph(), context, EntityInfoBlocks.Identity.class);
        var blockExec = new GraphExecutor(withBlock.graph());
        PortModel blockOut = withBlock.outputOf("u" + (chain - 1));

        assertEq(helper, "both chains compute the same value",
                plainExec.evaluate(plainOut, Float.class), blockExec.evaluate(blockOut, Float.class));

        var c = KGBench.comparePaired(
                "add-chain-64 (no context block in the graph)",
                () -> { plainExec.clearCache(); plainExec.evaluate(plainOut, Float.class); },
                "add-chain-64 (one unconnected context block present)",
                () -> { blockExec.clearCache(); blockExec.evaluate(blockOut, Float.class); },
                4_000, 20_000, 3);
        LOGGER.info("[KGBench] one context block taxes the whole graph by {} ns/run over {} nodes — {}",
                String.format("%.0f", c.deltaNsPerRun()), chain,
                c.conclusive() ? "conclusive" : "inconclusive on this machine");
        helper.succeed();
    }
}
