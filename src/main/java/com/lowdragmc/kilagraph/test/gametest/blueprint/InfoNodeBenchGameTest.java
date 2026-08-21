package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityDataNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityInfoNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGBench;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;

/**
 * What reading a property through a context costs, against reading it through a standalone node.
 *
 * <p>Both forms exist on purpose and compute identically: {@code mc_entity_block_pos} takes an entity and
 * emits its block position, and so does {@code entity_block_position} inside an {@code info_entity}
 * context. The context form wires the entity once for many properties; the standalone form is one node in
 * the middle of an expression. This measures the difference so the choice can be made on ergonomics
 * rather than on a guess about speed.
 *
 * <h2>Where the difference comes from</h2>
 * A block cannot read its own input — it reads its <em>parent's</em> {@code target}, which means a hash
 * lookup for the port plus {@code pullInputValue}'s by-{@code PortModel} slow path: a
 * {@code prepared.node(m)} map lookup and then a linear {@code inputIndexOf} identity scan. A standalone
 * node's input was resolved to an array index at prepare time. That is the whole of it — there is no
 * reflection left in this mechanism to account for.
 *
 * <h2>History worth keeping</h2>
 * This file used to measure a reflective {@code info_field} block, and those numbers are what argued for
 * replacing it. They are recorded in {@code docs/bench-baseline.md} along with two findings that came out
 * of it and are easy to get wrong again:
 * <ul>
 *   <li>{@code Method.invoke} on a warm monomorphic call site is within <b>1.6 ns</b> of a direct call,
 *       and a {@code LambdaMetafactory} getter is indistinguishable from a hand-written lambda. Reflection
 *       was never the cost here.</li>
 *   <li>A boxing benchmark on a small {@code int} measures {@code Integer.valueOf}'s −128..127 cache, not
 *       boxing. The first version of that measurement reported 0 B/run and was read as escape analysis.</li>
 * </ul>
 *
 * <p>As everywhere in this suite, timing is logged and never asserted — the assertions are on the values.
 */
@GameTestHolder(Kilagraph.MODID)
public final class InfoNodeBenchGameTest {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private InfoNodeBenchGameTest() {
    }

    /** Reading one property: standalone node versus context plus block, same answer. */
    @GameTest(template = "empty", timeoutTicks = 6000)
    @PrefixGameTestTemplate(false)
    public static void perReadCost(GameTestHelper helper) {
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        BlockPos expected = pig.blockPosition();

        // --- standalone node ---
        var direct = newGraph();
        NodeModel standalone = addNode(direct, EntityDataNodes.EntityBlockPos.class);
        setInputConstant(standalone, "entity", pig);
        var directExec = new GraphExecutor(direct);
        PortModel directOut = standalone.getOutputsById().get("out");

        // --- context + property block ---
        var viaContext = newGraph();
        NodeModel context = addNode(viaContext, EntityInfoNode.class);
        setInputConstant(context, "target", pig);
        NodeModel block = addBlock(viaContext, context, EntityInfoBlocks.BlockPosition.class);
        var blockExec = new GraphExecutor(viaContext);
        PortModel blockOut = block.getOutputsById().get("value");

        // Both really do produce the same value — otherwise the comparison is meaningless.
        assertEq(helper, "standalone node result", expected, directExec.evaluate(directOut, BlockPos.class));
        assertEq(helper, "property block result", expected, blockExec.evaluate(blockOut, BlockPos.class));

        var c = KGBench.comparePaired(
                "entity blockPos (standalone node)",
                () -> { directExec.clearCache(); directExec.evaluate(directOut, BlockPos.class); },
                "entity blockPos (context + block)",
                () -> { blockExec.clearCache(); blockExec.evaluate(blockOut, BlockPos.class); },
                4_000, 20_000, 3);
        LOGGER.info("[KGBench] context indirection: {} ns per property read — {}",
                String.format("%.0f", c.deltaNsPerRun()),
                c.conclusive() ? "conclusive" : "inconclusive on this machine");

        KGBench.reportRow(KGBench.measure("entity blockPos (standalone)", 1, 4_000, 20_000,
                () -> { directExec.clearCache(); directExec.evaluate(directOut, BlockPos.class); }));
        KGBench.reportRow(KGBench.measure("entity blockPos (context + block)", 1, 4_000, 20_000,
                () -> { blockExec.clearCache(); blockExec.evaluate(blockOut, BlockPos.class); }));
        helper.succeed();
    }

    /**
     * Reading several properties of one target — and the expectation this shape refuted.
     *
     * <p>The prediction was that the context would pay off here: the standalone form re-pulls the entity
     * for each node, while four blocks share one {@code target} that the value cache serves after the
     * first read. So one property should be the context's worst case and four should be its best.
     *
     * <p><b>It loses anyway</b>, and by more: <b>187 vs 255 ns</b> for four properties (conclusive,
     * spread 68) — about the same ~30 ns per property as reading one. The shared target saves nothing,
     * because the cost is not resolving the value but <em>reaching</em> it: every block pays a
     * {@code getInputsById()} lookup and the by-{@code PortModel} {@code pullInputValue} path
     * independently, and the cache only spares them the upstream evaluation, which for a constant was
     * nearly free to begin with.
     *
     * <p>So the context form is chosen for what it is actually better at — one wire instead of four, and
     * the properties of one object grouped where they belong — and not for a performance story. Kept
     * because the prediction was plausible enough to be worth having on record as wrong.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 6000)
    @PrefixGameTestTemplate(false)
    public static void manyPropertiesOfOneTarget(GameTestHelper helper) {
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));

        var direct = newGraph();
        NodeModel pos = addNode(direct, EntityDataNodes.Position.class);
        NodeModel eye = addNode(direct, EntityDataNodes.EyePosition.class);
        NodeModel look = addNode(direct, EntityDataNodes.LookDirection.class);
        NodeModel box = addNode(direct, EntityDataNodes.BoundingBox.class);
        for (NodeModel n : new NodeModel[]{pos, eye, look, box}) setInputConstant(n, "entity", pig);
        var directExec = new GraphExecutor(direct);
        PortModel[] directOuts = {
                pos.getOutputsById().get("out"), eye.getOutputsById().get("out"),
                look.getOutputsById().get("out"), box.getOutputsById().get("out")};

        var viaContext = newGraph();
        NodeModel context = addNode(viaContext, EntityInfoNode.class);
        setInputConstant(context, "target", pig);
        PortModel[] blockOuts = {
                addBlock(viaContext, context, EntityInfoBlocks.Position.class).getOutputsById().get("value"),
                addBlock(viaContext, context, EntityInfoBlocks.EyePosition.class).getOutputsById().get("value"),
                addBlock(viaContext, context, EntityInfoBlocks.LookDirection.class).getOutputsById().get("value"),
                addBlock(viaContext, context, EntityInfoBlocks.BoundingBox.class).getOutputsById().get("value")};
        var blockExec = new GraphExecutor(viaContext);

        for (int i = 0; i < 4; i++) {
            assertEq(helper, "both forms agree on property " + i,
                    String.valueOf(directExec.evaluate(directOuts[i], Object.class)),
                    String.valueOf(blockExec.evaluate(blockOuts[i], Object.class)));
        }

        var c = KGBench.comparePaired(
                "4 entity properties (standalone nodes)",
                () -> {
                    directExec.clearCache();
                    for (PortModel p : directOuts) directExec.evaluate(p, Object.class);
                },
                "4 entity properties (one context)",
                () -> {
                    blockExec.clearCache();
                    for (PortModel p : blockOuts) blockExec.evaluate(p, Object.class);
                },
                4_000, 10_000, 3);
        LOGGER.info("[KGBench] context over 4 properties: {} ns/run — {}",
                String.format("%.0f", c.deltaNsPerRun()),
                c.conclusive() ? "conclusive" : "inconclusive on this machine");
        helper.succeed();
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
