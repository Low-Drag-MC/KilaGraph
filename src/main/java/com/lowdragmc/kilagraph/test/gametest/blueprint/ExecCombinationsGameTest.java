package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BreakNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SequenceNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.WhileNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.joml.Vector2f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Multi-node combination / interaction tests. The per-node suites pin individual node behaviour;
 * these pin the <em>compositions</em> that have historically hidden bugs:
 * <ul>
 *   <li>Nested loops with an accumulator that survives both clearCache layers.</li>
 *   <li>{@code Break} scope: inner loop only; and {@code Break} unwinding through a {@code Sequence}
 *       to the enclosing loop (abandoning later Sequence outputs).</li>
 *   <li>{@code While} terminating on a condition that the body mutates (not on the iteration cap).</li>
 *   <li>A {@code Sequence} of loops running each loop to completion in order.</li>
 * </ul>
 */
public final class ExecCombinationsGameTest {
    private static final String NESTED_ACCUM = "exec_nested_loop_accumulates";
    private static final String BREAK_INNER_SCOPED = "exec_break_inner_loop_scoped";
    private static final String BREAK_IN_SEQUENCE = "exec_break_in_sequence_unwinds_to_loop";
    private static final String WHILE_TERMINATES = "exec_while_terminates_on_condition";
    private static final String SEQUENCE_OF_LOOPS = "exec_sequence_of_loops";

    private ExecCombinationsGameTest() {}

    public static void registerFunctions() {
        KGGameTests.registerFunction(NESTED_ACCUM, ExecCombinationsGameTest::nestedLoopAccumulates);
        KGGameTests.registerFunction(BREAK_INNER_SCOPED, ExecCombinationsGameTest::breakInInnerLoopScoped);
        KGGameTests.registerFunction(BREAK_IN_SEQUENCE, ExecCombinationsGameTest::breakInSequenceUnwindsToLoop);
        KGGameTests.registerFunction(WHILE_TERMINATES, ExecCombinationsGameTest::whileTerminatesOnCondition);
        KGGameTests.registerFunction(SEQUENCE_OF_LOOPS, ExecCombinationsGameTest::sequenceOfLoops);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{NESTED_ACCUM, BREAK_INNER_SCOPED, BREAK_IN_SEQUENCE, WHILE_TERMINATES, SEQUENCE_OF_LOOPS}) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    // ---- shared builders -------------------------------------------------------------------

    /** Fresh int INPUT variable (default 0). SetVar writes it by name; get-nodes read it. */
    private static VariableDeclarationModelBase intVar(BlueprintGraph g, String name) {
        return (VariableDeclarationModelBase) g.graphModel.createVariable(name, int.class, 0, VariableKind.INPUT);
    }

    /** A get-form variable node's output port (the "read" side). */
    private static PortModel varGet(BlueprintGraph g, VariableDeclarationModelBase v) {
        return g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort();
    }

    /**
     * Builds {@code name = name + delta} as a get → Add → SetVar chain and returns the SetVar node.
     * Wire the returned node's {@code trigger} into the exec point that should perform the increment.
     * Re-reads the variable each evaluation, so it accumulates correctly across loop iterations
     * (each iteration's clearCache re-pulls the current value).
     */
    private static NodeModel incrementer(BlueprintGraph g, VariableDeclarationModelBase v, String name, float delta) {
        var add = addNode(g, AddNode.class);
        wire(g, add.getInputsById().get("in1"), varGet(g, v));
        setInputConstant(add, "in2", delta);
        var set = addNode(g, SetVarNode.class);
        setOption(set, "varName", name);
        wire(g, set.getInputsById().get("value"), add.getOutputsById().get("out"));
        return set;
    }

    private static void expectVar(GameTestHelper helper, GraphExecutor exec, String name, float expected) {
        Object actual = exec.getEnvironment().variables().get(name);
        if (!(actual instanceof Number n) || Math.abs(n.floatValue() - expected) > 1e-5f) {
            helper.fail("expected " + name + "=" + expected + ", got " + actual);
            return;
        }
        helper.succeed();
    }

    // ---- tests -----------------------------------------------------------------------------

    /** For(3) {@code ×} For(2), body increments counter. 3×2 = 6 body executions. */
    public static void nestedLoopAccumulates(GameTestHelper helper) {
        var g = newGraph();
        var counter = intVar(g, "counter");
        var entry = addNode(g, EntryNode.class);
        var outer = addNode(g, ForNode.class);
        setInputConstant(outer, "count", 3);
        var inner = addNode(g, ForNode.class);
        setInputConstant(inner, "count", 2);
        var inc = incrementer(g, counter, "counter", 1f);

        wire(g, outer.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, inner.getInputsById().get("in"), outer.getOutputsById().get("body"));
        wire(g, inc.getInputsById().get("trigger"), inner.getOutputsById().get("body"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        expectVar(helper, exec, "counter", 6f);
    }

    /**
     * For(3) outer, For(5) inner with Branch(innerIndex {@code >=} 2 ? Break : increment). The Break
     * ends only the inner loop (2 increments per outer pass: indices 0,1), so the outer still runs
     * all 3 passes → counter = 3 × 2 = 6. If Break leaked to the outer loop, counter would be 2.
     */
    public static void breakInInnerLoopScoped(GameTestHelper helper) {
        var g = newGraph();
        var counter = intVar(g, "counter");
        var entry = addNode(g, EntryNode.class);
        var outer = addNode(g, ForNode.class);
        setInputConstant(outer, "count", 3);
        var inner = addNode(g, ForNode.class);
        setInputConstant(inner, "count", 5);

        var ge = addNode(g, GreaterEqualNode.class);
        setInputConstant(ge, "b", 2.0f);
        wire(g, ge.getInputsById().get("a"), inner.getOutputsById().get("index"));
        var br = addNode(g, BranchNode.class);
        wire(g, br.getInputsById().get("cond"), ge.getOutputsById().get("out"));
        var brk = addNode(g, BreakNode.class);
        var inc = incrementer(g, counter, "counter", 1f);

        wire(g, outer.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, inner.getInputsById().get("in"), outer.getOutputsById().get("body"));
        wire(g, br.getInputsById().get("in"), inner.getOutputsById().get("body"));
        wire(g, brk.getInputsById().get("in"), br.getOutputsById().get("trueExec"));
        wire(g, inc.getInputsById().get("trigger"), br.getOutputsById().get("falseExec"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        expectVar(helper, exec, "counter", 6f);
    }

    /**
     * For(3), body = Sequence(2): out1 = Branch(index {@code >=} 1 ? Break : incA), out2 = incB.
     * <ul>
     *   <li>iter 0 (index 0): out1 false → incA (a=1); out2 → incB (b=1).</li>
     *   <li>iter 1 (index 1): out1 true → Break. The Break unwinds <em>through</em> the Sequence
     *       (so out2/incB is abandoned this iter) up to the For, which ends the loop.</li>
     * </ul>
     * Final a=1, b=1. A Break that didn't propagate through Sequence (or didn't reach the loop) would
     * leave b=2 or loop further.
     */
    public static void breakInSequenceUnwindsToLoop(GameTestHelper helper) {
        var g = newGraph();
        var a = intVar(g, "a");
        var b = intVar(g, "b");
        var entry = addNode(g, EntryNode.class);
        var fr = addNode(g, ForNode.class);
        setInputConstant(fr, "count", 3);
        var seq = addNode(g, SequenceNode.class);
        setOption(seq, "outputs", 2);

        var ge = addNode(g, GreaterEqualNode.class);
        setInputConstant(ge, "b", 1.0f);
        wire(g, ge.getInputsById().get("a"), fr.getOutputsById().get("index"));
        var br = addNode(g, BranchNode.class);
        wire(g, br.getInputsById().get("cond"), ge.getOutputsById().get("out"));
        var brk = addNode(g, BreakNode.class);
        var incA = incrementer(g, a, "a", 1f);
        var incB = incrementer(g, b, "b", 1f);

        wire(g, fr.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, seq.getInputsById().get("in"), fr.getOutputsById().get("body"));
        wire(g, br.getInputsById().get("in"), seq.getOutputsById().get("out1"));
        wire(g, brk.getInputsById().get("in"), br.getOutputsById().get("trueExec"));
        wire(g, incA.getInputsById().get("trigger"), br.getOutputsById().get("falseExec"));
        wire(g, incB.getInputsById().get("trigger"), seq.getOutputsById().get("out2"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object av = exec.getEnvironment().variables().get("a");
        Object bv = exec.getEnvironment().variables().get("b");
        boolean aOk = av instanceof Number an && Math.abs(an.floatValue() - 1f) < 1e-5f;
        boolean bOk = bv instanceof Number bn && Math.abs(bn.floatValue() - 1f) < 1e-5f;
        if (!aOk || !bOk) {
            helper.fail("expected a=1 (incA ran iter0 only) and b=1 (incB abandoned by Break in iter1), got a=" + av + ", b=" + bv);
            return;
        }
        helper.succeed();
    }

    /**
     * While(cond = counter {@code <} 3), body increments counter, maxIterations=1000. The loop must
     * stop because the re-pulled condition goes false (counter reaches 3), not because of the cap.
     */
    public static void whileTerminatesOnCondition(GameTestHelper helper) {
        var g = newGraph();
        var counter = intVar(g, "counter");
        var entry = addNode(g, EntryNode.class);
        var wh = addNode(g, WhileNode.class);
        setOption(wh, "maxIterations", 1000);

        var lt = addNode(g, LessThanNode.class);
        wire(g, lt.getInputsById().get("a"), varGet(g, counter));
        setInputConstant(lt, "b", 3.0f);
        wire(g, wh.getInputsById().get("cond"), lt.getOutputsById().get("out"));

        var inc = incrementer(g, counter, "counter", 1f);

        wire(g, wh.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, inc.getInputsById().get("trigger"), wh.getOutputsById().get("body"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        expectVar(helper, exec, "counter", 3f);
    }

    /**
     * Sequence(2): out1 = For(2) incrementing counter, out2 = For(3) incrementing counter. out1's
     * loop runs fully before out2's → counter = 2 + 3 = 5.
     */
    public static void sequenceOfLoops(GameTestHelper helper) {
        var g = newGraph();
        var counter = intVar(g, "counter");
        var entry = addNode(g, EntryNode.class);
        var seq = addNode(g, SequenceNode.class);
        setOption(seq, "outputs", 2);
        var forA = addNode(g, ForNode.class);
        setInputConstant(forA, "count", 2);
        var forB = addNode(g, ForNode.class);
        setInputConstant(forB, "count", 3);
        var incA = incrementer(g, counter, "counter", 1f);
        var incB = incrementer(g, counter, "counter", 1f);

        wire(g, seq.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, forA.getInputsById().get("in"), seq.getOutputsById().get("out1"));
        wire(g, forB.getInputsById().get("in"), seq.getOutputsById().get("out2"));
        wire(g, incA.getInputsById().get("trigger"), forA.getOutputsById().get("body"));
        wire(g, incB.getInputsById().get("trigger"), forB.getOutputsById().get("body"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        expectVar(helper, exec, "counter", 5f);
    }
}
