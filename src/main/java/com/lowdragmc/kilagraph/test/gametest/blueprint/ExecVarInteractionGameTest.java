package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector2f;

import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * The interplay the plain node tests don't cover: an execution-flow run mutates a graph/global
 * variable (via {@code SetVar}), and a later data-pull / {@code runOutputs} on the <em>same</em>
 * executor observes that mutation. Verifies exec-flow and the variable store share one environment.
 */
@GameTestHolder(Kilagraph.MODID)
public final class ExecVarInteractionGameTest {
    private static final String EXEC_SET_THEN_DATA_READ = "exec_set_then_data_read";
    private static final String EXEC_SET_THEN_RUN_OUTPUTS = "exec_set_then_run_outputs";

    private ExecVarInteractionGameTest() {}

    /** Entry → SetVar("x", 21); then an INPUT-variable("x") get-node feeds an Add — pulled on the same executor. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void execSetThenDataRead(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var setX = addNode(g, SetVarNode.class);
        setOption(setX, "varName", "x");
        var add21 = addNode(g, AddNode.class);
        setInputConstant(add21, "in1", 21f);
        setInputConstant(add21, "in2", 0f);
        wire(g, setX.getInputsById().get("value"), add21.getOutputsById().get("out"));
        wire(g, setX.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        // Data side: a get-form variable node reads "x" from the env store.
        var xVar = (VariableDeclarationModelBase) g.graphModel.createVariable("x", int.class, 0, VariableKind.INPUT);
        var xNode = g.graphModel.createVariableNode(xVar, new Vector2f(0, 0), null, null);
        var readAdd = addNode(g, AddNode.class);
        setInputConstant(readAdd, "in2", 0f);
        wire(g, readAdd.getInputsById().get("in1"), xNode.getOutputPort());

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);                 // writes x = 21 into the env store
        Float out = exec.evaluate(readAdd.getOutputsById().get("out"), Float.class);
        assertEq(helper, "data read sees exec-written x", 21f, out, 1e-5f);
        helper.succeed();
    }

    /**
     * A variable read goes stale when the flow takes a step — the {@code SetVar} that wrote it, here — and
     * is otherwise memoised until {@code clearCache()}: a store changed with no step in between is not
     * seen by a read that already happened. Unreal's rule, a pure read worked out again for each exec
     * node ({@code ExecSemanticsGameTest.aVariableReadAfterAWriteInTheSameRunSeesIt} is the same inside
     * one run).
     *
     * <p>⚠️ This used to pin the opposite — a read memoised for the whole generation, a later
     * {@code SetVar} not seen — out of care for loop-carried accumulators. They keep their meaning: the
     * read an accumulator's {@code SetVar} makes is worked out inside that {@code SetVar}'s own step, each
     * iteration.
     * {@link #execSetThenDataRead} covers a first read after a write.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aVariableReadGoesStaleWhenTheFlowSteps(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.variable("x", int.class, 0, VariableKind.INPUT);
        b.add("read", AddNode.class).wire("read.in1", "x").constant("read.in2", 0f);
        b.add("entry", EntryNode.class);
        b.add("val", AddNode.class).constant("val.in1", 21f).constant("val.in2", 0f);
        b.add("setX", SetVarNode.class).option("setX", "varName", "x").wire("setX.value", "val");
        b.then("entry", "setX");

        var exec = new GraphExecutor(b.graph());
        var readOut = b.outputOf("read");

        assertEq(helper, "before the write", 0f, orNaN(exec.evaluate(readOut, Float.class)), 1e-5f);
        exec.executeFrom(b.node("entry"));           // x = 21 in the store
        assertEq(helper, "the flow stepped, so the read is fresh", 21f,
                orNaN(exec.evaluate(readOut, Float.class)), 1e-5f);
        exec.getEnvironment().variables().put("x", 5);
        assertEq(helper, "a store changed with no step is not seen by the memo", 21f,
                orNaN(exec.evaluate(readOut, Float.class)), 1e-5f);
        exec.clearCache();
        assertEq(helper, "until clearCache", 5f, orNaN(exec.evaluate(readOut, Float.class)), 1e-5f);
        helper.succeed();
    }

    /**
     * Removing a variable makes it read as absent again — it falls back to its declared default —
     * and so does clearing the whole store.
     *
     * <p>The executor caches the storage a variable node resolved to, so this is the case that
     * cache could get wrong: a store that dropped its entry would leave the node holding storage the
     * store no longer consults, and the node would keep serving the removed value forever. The store
     * therefore marks entries absent in place rather than dropping them, and this is what says so.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void removingAVariableRestoresItsDefault(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.variable("x", int.class, 5, VariableKind.INPUT);
        b.add("read", AddNode.class).wire("read.in1", "x").constant("read.in2", 0f);

        var exec = new GraphExecutor(b.graph());
        var readOut = b.outputOf("read");
        var store = exec.getEnvironment().variables();

        assertEq(helper, "unset reads the declared default", 5f, orNaN(exec.evaluate(readOut, Float.class)), 1e-5f);

        store.put("x", 100);
        exec.clearCache();
        assertEq(helper, "set reads the stored value", 100f, orNaN(exec.evaluate(readOut, Float.class)), 1e-5f);

        store.remove("x");
        exec.clearCache();
        assertEq(helper, "removed falls back to the default", 5f, orNaN(exec.evaluate(readOut, Float.class)), 1e-5f);

        store.put("x", 42);
        exec.clearCache();
        assertEq(helper, "and can be set again", 42f, orNaN(exec.evaluate(readOut, Float.class)), 1e-5f);

        store.clear();
        exec.clearCache();
        assertEq(helper, "clear() has the same effect as remove()", 5f,
                orNaN(exec.evaluate(readOut, Float.class)), 1e-5f);
        helper.succeed();
    }

    private static float orNaN(Float v) {
        return v == null ? Float.NaN : v;
    }

    private static float num(Object o) {
        return o instanceof Number n ? n.floatValue() : Float.NaN;
    }

    /** Entry → SetVar("y", 9); runOutputs() harvests OUTPUT var "y" from the env fallback. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void execSetThenRunOutputs(GameTestHelper helper) {
        var g = newGraph();
        g.graphModel.createVariable("y", int.class, 0, VariableKind.OUTPUT);

        var entry = addNode(g, EntryNode.class);
        var setY = addNode(g, SetVarNode.class);
        setOption(setY, "varName", "y");
        var add9 = addNode(g, AddNode.class);
        setInputConstant(add9, "in1", 9f);
        setInputConstant(add9, "in2", 0f);
        wire(g, setY.getInputsById().get("value"), add9.getOutputsById().get("out"));
        wire(g, setY.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Map<String, Object> results = exec.runOutputs();
        if (!(results.get("y") instanceof Number n)) { helper.fail("y not a number: " + results.get("y")); return; }
        assertEq(helper, "runOutputs sees exec-written y", 9f, n.floatValue(), 1e-5f);
        helper.succeed();
    }
}
