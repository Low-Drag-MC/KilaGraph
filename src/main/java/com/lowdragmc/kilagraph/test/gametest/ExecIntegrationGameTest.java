package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ContinueNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ModuloNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
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
 * The Phase 2 canary: For + Branch + Continue + SetVar + variable-read all wired together.
 *
 * <pre>
 * Entry → For(count=5)
 *           body: Modulo(index, 2) → LessEqual(_, 0) → Branch
 *                   trueExec (even): Add(sumEven, index) → SetVar(sumEven)
 *                   falseExec (odd): Continue
 *         completed: (assertion on env)
 * </pre>
 *
 * Expected: sumEven = 0 + 2 + 4 = 6.
 */
public final class ExecIntegrationGameTest {
    private static final String SUM_OF_EVENS = "exec_integration_sum_of_evens";

    private ExecIntegrationGameTest() {}

    static void registerFunctions() {
        KGGameTests.registerFunction(SUM_OF_EVENS, ExecIntegrationGameTest::sumOfEvens);
    }

    static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var d = KGGameTests.defaultTestData(environment, "empty");
        KGGameTests.registerFunctionTest(event, SUM_OF_EVENS, KGGameTests.functionKey(SUM_OF_EVENS), d);
    }

    public static void sumOfEvens(GameTestHelper helper) {
        var g = newGraph();
        // INPUT-kind variable → ModifierFlags.READ → variable node has OUTPUT port (get-form).
        // SetVar writes by name to env.variables() — the modifier doesn't gate writes.
        var sumVar = (VariableDeclarationModelBase)
                g.graphModel.createVariable("sumEven", int.class, 0, VariableKind.INPUT);
        var sumGet = g.graphModel.createVariableNode(sumVar, new Vector2f(0, 0), null, null);

        var entry = addNode(g, EntryNode.class);
        var fr = addNode(g, ForNode.class);
        setInputConstant(fr, "count", 5);

        // cond: index % 2 <= 0 → true iff index is even
        var mod = addNode(g, ModuloNode.class);
        setInputConstant(mod, "b", 2.0f);
        wire(g, mod.getInputsById().get("a"), fr.getOutputsById().get("index"));

        var isEven = addNode(g, LessEqualNode.class);
        setInputConstant(isEven, "b", 0.0f);
        wire(g, isEven.getInputsById().get("a"), mod.getOutputsById().get("out"));

        var br = addNode(g, BranchNode.class);
        wire(g, br.getInputsById().get("cond"), isEven.getOutputsById().get("out"));

        // trueExec branch: sumEven = sumEven + index
        var add = addNode(g, AddNode.class);
        wire(g, add.getInputsById().get("in1"), sumGet.getOutputPort());
        wire(g, add.getInputsById().get("in2"), fr.getOutputsById().get("index"));

        var setSum = addNode(g, SetVarNode.class);
        setOption(setSum, "varName", "sumEven");
        wire(g, setSum.getInputsById().get("value"), add.getOutputsById().get("out"));

        // falseExec branch: Continue
        var cont = addNode(g, ContinueNode.class);

        // Exec wiring
        wire(g, fr.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, br.getInputsById().get("in"), fr.getOutputsById().get("body"));
        wire(g, setSum.getInputsById().get("trigger"), br.getOutputsById().get("trueExec"));
        wire(g, cont.getInputsById().get("in"), br.getOutputsById().get("falseExec"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);

        Object sum = exec.getEnvironment().variables().get("sumEven");
        if (!(sum instanceof Number n) || Math.abs(n.floatValue() - 6.0f) > 1e-5f) {
            helper.fail("expected sumEven = 0 + 2 + 4 = 6, got " + sum);
            return;
        }
        helper.succeed();
    }
}
