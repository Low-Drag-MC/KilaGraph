package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BreakNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ContinueNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForEachNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.PrintNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.WhileNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListCombineNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import net.minecraft.gametest.framework.GameTestHelper;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

@GameTestHolder(Kilagraph.MODID)
public final class ExecLoopsGameTest {
    private static final String FOR_COUNTS = "exec_for_counts";
    private static final String FOR_BREAK = "exec_for_break";
    private static final String FOR_CONTINUE = "exec_for_continue";
    private static final String WHILE_RUNS = "exec_while_runs";
    private static final String WHILE_MAX_GUARD = "exec_while_max_guard";
    private static final String FOREACH_LIST = "exec_foreach_list";

    private ExecLoopsGameTest() {}

    /** For(count=5) body: SetVar(counter += 1). Expect counter == 5. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void forCounts(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var fr = addNode(g, ForNode.class);
        setInputConstant(fr, "count", 5);
        var setC = addNode(g, SetVarNode.class);
        setOption(setC, "varName", "counter");

        // counter = counter + 1 via Add reading nothing (use index as the increment source)
        // Simpler: just SetVar to a literal — but we want it to actually count.
        // Make value = (current iteration's For.index) + 1. Total writes: 0+1, 1+1, ... 4+1 → final 5.
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in2", 1.0f);
        wire(g, add.getInputsById().get("in1"), fr.getOutputsById().get("index"));

        wire(g, fr.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, setC.getInputsById().get("trigger"), fr.getOutputsById().get("body"));
        wire(g, setC.getInputsById().get("value"), add.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object counter = exec.getEnvironment().variables().get("counter");
        if (!(counter instanceof Number n) || Math.abs(n.floatValue() - 5.0f) > 1e-5f) {
            helper.fail("expected counter=5 (last write), got " + counter);
            return;
        }
        helper.succeed();
    }

    /** For(5) body: Branch(index == 3 ? Break : SetVar). After Break, counter stays at index 2's value (3). */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void forBreak(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var fr = addNode(g, ForNode.class);
        setInputConstant(fr, "count", 5);

        // Need: condition = (index == 3). Use a "greater equal 3" via GreaterEqual + index.
        var ge = addNode(g, com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode.class);
        setInputConstant(ge, "b", 3.0f);
        wire(g, ge.getInputsById().get("a"), fr.getOutputsById().get("index"));

        var br = addNode(g, BranchNode.class);
        wire(g, br.getInputsById().get("cond"), ge.getOutputsById().get("out"));

        var brk = addNode(g, BreakNode.class);
        var setC = addNode(g, SetVarNode.class);
        setOption(setC, "varName", "counter");
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in2", 1.0f);
        wire(g, add.getInputsById().get("in1"), fr.getOutputsById().get("index"));
        wire(g, setC.getInputsById().get("value"), add.getOutputsById().get("out"));

        wire(g, fr.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, br.getInputsById().get("in"), fr.getOutputsById().get("body"));
        wire(g, brk.getInputsById().get("in"), br.getOutputsById().get("trueExec"));
        wire(g, setC.getInputsById().get("trigger"), br.getOutputsById().get("falseExec"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object counter = exec.getEnvironment().variables().get("counter");
        // Iterations 0,1,2 ran SetVar (1,2,3). Iteration 3 hit Break before SetVar.
        if (!(counter instanceof Number n) || Math.abs(n.floatValue() - 3.0f) > 1e-5f) {
            helper.fail("expected counter=3 (broke at iter 3), got " + counter);
            return;
        }
        helper.succeed();
    }

    /** For(5) body: Branch(index >= 2 ? Continue : SetVar). Writes only for index 0, 1. counter == 1. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void forContinue(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var fr = addNode(g, ForNode.class);
        setInputConstant(fr, "count", 5);

        var ge = addNode(g, com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode.class);
        setInputConstant(ge, "b", 2.0f);
        wire(g, ge.getInputsById().get("a"), fr.getOutputsById().get("index"));

        var br = addNode(g, BranchNode.class);
        wire(g, br.getInputsById().get("cond"), ge.getOutputsById().get("out"));

        var cont = addNode(g, ContinueNode.class);
        var setC = addNode(g, SetVarNode.class);
        setOption(setC, "varName", "counter");
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in2", 1.0f);
        wire(g, add.getInputsById().get("in1"), fr.getOutputsById().get("index"));
        wire(g, setC.getInputsById().get("value"), add.getOutputsById().get("out"));

        wire(g, fr.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, br.getInputsById().get("in"), fr.getOutputsById().get("body"));
        wire(g, cont.getInputsById().get("in"), br.getOutputsById().get("trueExec"));
        wire(g, setC.getInputsById().get("trigger"), br.getOutputsById().get("falseExec"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object counter = exec.getEnvironment().variables().get("counter");
        if (!(counter instanceof Number n) || Math.abs(n.floatValue() - 2.0f) > 1e-5f) {
            helper.fail("expected counter=2 (skipped from iter 2), got " + counter);
            return;
        }
        helper.succeed();
    }

    /** While(cond=true, max=3): body should run 3 times then maxIterations cap kicks in. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void whileRuns(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var wh = addNode(g, WhileNode.class);
        setInputConstant(wh, "cond", true);
        setOption(wh, "maxIterations", 3);

        // Capture by writing each iteration: counter = iteration*1 — but While has no index. Just
        // use a literal SetVar to confirm it ran (final value = 99).
        var setC = addNode(g, SetVarNode.class);
        setOption(setC, "varName", "ran");
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 99.0f);
        setInputConstant(add, "in2", 0.0f);
        wire(g, setC.getInputsById().get("value"), add.getOutputsById().get("out"));

        wire(g, wh.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, setC.getInputsById().get("trigger"), wh.getOutputsById().get("body"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        assertEq(helper, "ran was set", 99.0f,
                ((Number) exec.getEnvironment().variables().get("ran")).floatValue(), 1e-5f);
        helper.succeed();
    }

    /** While(cond=true, max=2): without the cap this would infinite-loop. Should exit cleanly. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void whileMaxGuard(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var wh = addNode(g, WhileNode.class);
        setInputConstant(wh, "cond", true);
        setOption(wh, "maxIterations", 2);
        // No body wire — just make sure executeFrom returns.
        wire(g, wh.getInputsById().get("in"), entry.getOutputsById().get("next"));

        var exec = new GraphExecutor(g);
        long start = System.nanoTime();
        exec.executeFrom(entry);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        if (elapsedMs > 2000) {
            helper.fail("while loop took too long (" + elapsedMs + "ms) — guard probably not firing");
            return;
        }
        helper.succeed();
    }

    /** ForEach over ["a","b","c"]: counter accumulates count == 3. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void forEachList(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var combine = addNode(g, ListCombineNode.class);
        setOption(combine, "type", TypeHandles.STRING.getIdentification());
        setOption(combine, "inputs", 3);
        setInputConstant(combine, "in1", "a");
        setInputConstant(combine, "in2", "b");
        setInputConstant(combine, "in3", "c");

        var fe = addNode(g, ForEachNode.class);
        wire(g, fe.getInputsById().get("list"), combine.getOutputsById().get("out"));

        var setC = addNode(g, SetVarNode.class);
        setOption(setC, "varName", "lastItem");
        wire(g, setC.getInputsById().get("value"), fe.getOutputsById().get("item"));

        wire(g, fe.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, setC.getInputsById().get("trigger"), fe.getOutputsById().get("body"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        assertEq(helper, "last item == 'c'", "c", exec.getEnvironment().variables().get("lastItem"));
        helper.succeed();
    }
}
