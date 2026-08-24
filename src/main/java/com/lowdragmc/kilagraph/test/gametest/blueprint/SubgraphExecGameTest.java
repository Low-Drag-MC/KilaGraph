package com.lowdragmc.kilagraph.test.gametest.blueprint;


import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.SpawnFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector2f;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Exec-flow traversal through subgraphs. A subgraph's exec pins come from {@code EXECUTION_FLOW}
 * graph variables: an INPUT exec var → the subgraph node's exec-in pin (its inner get-node's exec
 * output is the entry), an OUTPUT exec var → an exec-out pin (its inner set-node is an exit). The
 * executor enters the inner exec, harvests inner WRITE data vars, and fires only the exec-out pins
 * the inner run actually reached. Covers: straight-through + data out, data in→out, exclusive
 * multi-exit, 3-level nesting, and child-variable isolation.
 */
public final class SubgraphExecGameTest {
    private static final String STRAIGHT_THROUGH = "subgraph_exec_straight_through";
    private static final String DATA_IN_OUT = "subgraph_exec_data_in_out";
    private static final String MULTI_EXIT_TRUE = "subgraph_exec_multi_exit_true";
    private static final String MULTI_EXIT_FALSE = "subgraph_exec_multi_exit_false";
    private static final String NESTED = "subgraph_exec_nested";
    private static final String CHILD_VAR_ISOLATED = "subgraph_exec_child_var_isolated";

    private SubgraphExecGameTest() {}


    public static void registerFunctions() {
        KGGameTests.registerFunction(STRAIGHT_THROUGH, SubgraphExecGameTest::straightThrough);
        KGGameTests.registerFunction(DATA_IN_OUT, SubgraphExecGameTest::dataInOut);
        KGGameTests.registerFunction(MULTI_EXIT_TRUE, SubgraphExecGameTest::multiExitTrue);
        KGGameTests.registerFunction(MULTI_EXIT_FALSE, SubgraphExecGameTest::multiExitFalse);
        KGGameTests.registerFunction(NESTED, SubgraphExecGameTest::nested);
        KGGameTests.registerFunction(CHILD_VAR_ISOLATED, SubgraphExecGameTest::childVarIsolated);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                STRAIGHT_THROUGH, DATA_IN_OUT, MULTI_EXIT_TRUE,
                MULTI_EXIT_FALSE, NESTED, CHILD_VAR_ISOLATED
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static VariableDeclarationModelBase execVar(CustomGraphModelImpl m, String name, VariableKind kind) {
        return (VariableDeclarationModelBase) m.createVariable(name, TypeHandles.EXECUTION_FLOW, null, kind);
    }

    private static VariableDeclarationModelBase dataVar(CustomGraphModelImpl m, String name, Class<?> type,
                                                        Object def, VariableKind kind) {
        return (VariableDeclarationModelBase) m.createVariable(name, type, def, kind);
    }

    private static String pin(VariableDeclarationModelBase v) {
        return v.getUid().toString();
    }

    // ---- 1. exec enters, runs, exits; a WRITE data var is harvested out -------------------------
    public static void straightThrough(GameTestHelper helper) {
        var outer = newGraph();
        var inner = outer.graphModel.createLocalSubgraphInstance();
        if (inner == null) { helper.fail("inner null"); return; }
        outer.graphModel.addLocalSubgraph(inner);

        var execIn = execVar(inner, "execIn", VariableKind.INPUT);
        var execOut = execVar(inner, "execOut", VariableKind.OUTPUT);
        var vOut = dataVar(inner, "vOut", int.class, 0, VariableKind.OUTPUT);
        var execInNode = inner.createVariableNode(execIn, new Vector2f(0, 0), null, null);
        var execOutNode = inner.createVariableNode(execOut, new Vector2f(400, 0), null, null);

        // inner: execIn → SetVar(vOut = 7) → execOut
        var set = addNode(inner.getGraph(), SetVarNode.class);
        setOption(set, "varName", "vOut");
        var add = addNode(inner.getGraph(), AddNode.class);
        setInputConstant(add, "in1", 7f);
        setInputConstant(add, "in2", 0f);
        inner.createWire(set.getInputsById().get("value"), add.getOutputsById().get("out"));
        inner.createWire(set.getInputsById().get("trigger"), execInNode.getOutputPort());
        inner.createWire(execOutNode.getInputPort(), set.getOutputsById().get("next"));

        var sub = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "sub",
                new Vector2f(0, 0), null, n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        sub.defineNode();

        var entry = addNode(outer, EntryNode.class);
        var outerSet = addNode(outer, SetVarNode.class);
        setOption(outerSet, "varName", "reached");
        var outerAdd = addNode(outer, AddNode.class);
        setInputConstant(outerAdd, "in1", 1f);
        setInputConstant(outerAdd, "in2", 0f);
        wire(outer, outerSet.getInputsById().get("value"), outerAdd.getOutputsById().get("out"));
        wire(outer, sub.getInputsById().get(pin(execIn)), entry.getOutputsById().get("next"));
        wire(outer, outerSet.getInputsById().get("trigger"), sub.getOutputsById().get(pin(execOut)));

        var exec = new GraphExecutor(outer);
        exec.executeFrom(entry);

        Object reached = exec.getEnvironment().variables().get("reached");
        if (!(reached instanceof Number rn) || Math.abs(rn.floatValue() - 1f) > 1e-5f) {
            helper.fail("exec-out did not continue outer flow, reached=" + reached);
            return;
        }
        Float harvested = exec.evaluate(sub.getOutputsById().get(pin(vOut)), Float.class);
        assertEq(helper, "subgraph data out harvested", 7f, harvested, 1e-5f);
        helper.succeed();
    }

    // ---- 2. subgraph consumes a seeded data input and produces a data output mid-exec -----------
    public static void dataInOut(GameTestHelper helper) {
        var outer = newGraph();
        var inner = outer.graphModel.createLocalSubgraphInstance();
        if (inner == null) { helper.fail("inner null"); return; }
        outer.graphModel.addLocalSubgraph(inner);

        var execIn = execVar(inner, "execIn", VariableKind.INPUT);
        var execOut = execVar(inner, "execOut", VariableKind.OUTPUT);
        var vIn = dataVar(inner, "vIn", int.class, 0, VariableKind.INPUT);
        var vOut = dataVar(inner, "vOut", int.class, 0, VariableKind.OUTPUT);
        var execInNode = inner.createVariableNode(execIn, new Vector2f(0, 0), null, null);
        var execOutNode = inner.createVariableNode(execOut, new Vector2f(400, 0), null, null);
        var vInNode = inner.createVariableNode(vIn, new Vector2f(0, 100), null, null);

        // inner: execIn → SetVar(vOut = vIn + 5) → execOut
        var add = addNode(inner.getGraph(), AddNode.class);
        inner.createWire(add.getInputsById().get("in1"), vInNode.getOutputPort());
        setInputConstant(add, "in2", 5f);
        var set = addNode(inner.getGraph(), SetVarNode.class);
        setOption(set, "varName", "vOut");
        inner.createWire(set.getInputsById().get("value"), add.getOutputsById().get("out"));
        inner.createWire(set.getInputsById().get("trigger"), execInNode.getOutputPort());
        inner.createWire(execOutNode.getInputPort(), set.getOutputsById().get("next"));

        var sub = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "sub",
                new Vector2f(0, 0), null, n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        sub.defineNode();
        setInputConstant(sub, pin(vIn), 10);  // seed the data input pin

        var entry = addNode(outer, EntryNode.class);
        wire(outer, sub.getInputsById().get(pin(execIn)), entry.getOutputsById().get("next"));

        var exec = new GraphExecutor(outer);
        exec.executeFrom(entry);
        Float harvested = exec.evaluate(sub.getOutputsById().get(pin(vOut)), Float.class);
        assertEq(helper, "vIn(10) + 5 harvested", 15f, harvested, 1e-5f);
        helper.succeed();
    }

    // ---- 3. exclusive multi-exit: Branch picks exactly one of two exec-out pins ------------------
    private static Map<String, Object> runMultiExit(boolean cond) {
        var outer = newGraph();
        var inner = outer.graphModel.createLocalSubgraphInstance();
        outer.graphModel.addLocalSubgraph(inner);

        var execIn = execVar(inner, "execIn", VariableKind.INPUT);
        var execOutA = execVar(inner, "execOutA", VariableKind.OUTPUT);
        var execOutB = execVar(inner, "execOutB", VariableKind.OUTPUT);
        var condVar = dataVar(inner, "cond", boolean.class, false, VariableKind.INPUT);
        var execInNode = inner.createVariableNode(execIn, new Vector2f(0, 0), null, null);
        var aNode = inner.createVariableNode(execOutA, new Vector2f(400, 0), null, null);
        var bNode = inner.createVariableNode(execOutB, new Vector2f(400, 100), null, null);
        var condNode = inner.createVariableNode(condVar, new Vector2f(0, 100), null, null);

        // inner: execIn → Branch(cond) → trueExec=execOutA, falseExec=execOutB
        var branch = addNode(inner.getGraph(), BranchNode.class);
        inner.createWire(branch.getInputsById().get("in"), execInNode.getOutputPort());
        inner.createWire(branch.getInputsById().get("cond"), condNode.getOutputPort());
        inner.createWire(aNode.getInputPort(), branch.getOutputsById().get("trueExec"));
        inner.createWire(bNode.getInputPort(), branch.getOutputsById().get("falseExec"));

        var sub = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "sub",
                new Vector2f(0, 0), null, n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        sub.defineNode();
        setInputConstant(sub, pin(condVar), cond);

        var entry = addNode(outer, EntryNode.class);
        wire(outer, sub.getInputsById().get(pin(execIn)), entry.getOutputsById().get("next"));

        var setA = addNode(outer, SetVarNode.class);
        setOption(setA, "varName", "tookA");
        var addA = addNode(outer, AddNode.class);
        setInputConstant(addA, "in1", 1f);
        setInputConstant(addA, "in2", 0f);
        wire(outer, setA.getInputsById().get("value"), addA.getOutputsById().get("out"));
        wire(outer, setA.getInputsById().get("trigger"), sub.getOutputsById().get(pin(execOutA)));

        var setB = addNode(outer, SetVarNode.class);
        setOption(setB, "varName", "tookB");
        var addB = addNode(outer, AddNode.class);
        setInputConstant(addB, "in1", 1f);
        setInputConstant(addB, "in2", 0f);
        wire(outer, setB.getInputsById().get("value"), addB.getOutputsById().get("out"));
        wire(outer, setB.getInputsById().get("trigger"), sub.getOutputsById().get(pin(execOutB)));

        var exec = new GraphExecutor(outer);
        exec.executeFrom(entry);
        var vars = exec.getEnvironment().variables();
        var result = new HashMap<String, Object>();
        result.put("tookA", vars.get("tookA"));
        result.put("tookB", vars.get("tookB"));
        return result;
    }



    public static void multiExitTrue(GameTestHelper helper) {
        var r = runMultiExit(true);
        assertTrue(helper, "cond=true fires A", r.get("tookA") instanceof Number);
        assertEq(helper, "cond=true does NOT fire B", null, r.get("tookB"));
        helper.succeed();
    }



    public static void multiExitFalse(GameTestHelper helper) {
        var r = runMultiExit(false);
        assertTrue(helper, "cond=false fires B", r.get("tookB") instanceof Number);
        assertEq(helper, "cond=false does NOT fire A", null, r.get("tookA"));
        helper.succeed();
    }

    // ---- 4. three-level exec nesting: outer → sub1 → (inner1) sub2 → (inner2) and back -----------
    public static void nested(GameTestHelper helper) {
        var outer = newGraph();
        var inner1 = outer.graphModel.createLocalSubgraphInstance();
        if (inner1 == null) { helper.fail("inner1 null"); return; }
        outer.graphModel.addLocalSubgraph(inner1);
        var inner2 = inner1.createLocalSubgraphInstance();
        if (inner2 == null) { helper.fail("inner2 null"); return; }
        inner1.addLocalSubgraph(inner2);

        // inner2: execIn2 → execOut2 (pass-through)
        var execIn2 = execVar(inner2, "execIn2", VariableKind.INPUT);
        var execOut2 = execVar(inner2, "execOut2", VariableKind.OUTPUT);
        var in2Node = inner2.createVariableNode(execIn2, new Vector2f(0, 0), null, null);
        var out2Node = inner2.createVariableNode(execOut2, new Vector2f(200, 0), null, null);
        inner2.createWire(out2Node.getInputPort(), in2Node.getOutputPort());

        // inner1: execIn1 → sub2.execIn2 ; sub2.execOut2 → execOut1
        var execIn1 = execVar(inner1, "execIn1", VariableKind.INPUT);
        var execOut1 = execVar(inner1, "execOut1", VariableKind.OUTPUT);
        var in1Node = inner1.createVariableNode(execIn1, new Vector2f(0, 0), null, null);
        var out1Node = inner1.createVariableNode(execOut1, new Vector2f(400, 0), null, null);
        var sub2 = inner1.createNodeWithType(SubgraphNodeModel.class, "sub2",
                new Vector2f(200, 0), null, n -> n.setLocalSubgraph(inner2), SpawnFlags.DEFAULT);
        sub2.defineNode();
        inner1.createWire(sub2.getInputsById().get(pin(execIn2)), in1Node.getOutputPort());
        inner1.createWire(out1Node.getInputPort(), sub2.getOutputsById().get(pin(execOut2)));

        // outer: Entry → sub1.execIn1 ; sub1.execOut1 → SetVar(reachedNested = 1)
        var sub1 = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "sub1",
                new Vector2f(0, 0), null, n -> n.setLocalSubgraph(inner1), SpawnFlags.DEFAULT);
        sub1.defineNode();
        var entry = addNode(outer, EntryNode.class);
        wire(outer, sub1.getInputsById().get(pin(execIn1)), entry.getOutputsById().get("next"));
        var set = addNode(outer, SetVarNode.class);
        setOption(set, "varName", "reachedNested");
        var add = addNode(outer, AddNode.class);
        setInputConstant(add, "in1", 1f);
        setInputConstant(add, "in2", 0f);
        wire(outer, set.getInputsById().get("value"), add.getOutputsById().get("out"));
        wire(outer, set.getInputsById().get("trigger"), sub1.getOutputsById().get(pin(execOut1)));

        var exec = new GraphExecutor(outer);
        exec.executeFrom(entry);
        Object reached = exec.getEnvironment().variables().get("reachedNested");
        if (!(reached instanceof Number n) || Math.abs(n.floatValue() - 1f) > 1e-5f) {
            helper.fail("exec did not propagate through nested subgraphs, reachedNested=" + reached);
            return;
        }
        helper.succeed();
    }

    // ---- 5. a child SetVar stays in the child env; it must not leak to the outer env -------------
    public static void childVarIsolated(GameTestHelper helper) {
        var outer = newGraph();
        var inner = outer.graphModel.createLocalSubgraphInstance();
        if (inner == null) { helper.fail("inner null"); return; }
        outer.graphModel.addLocalSubgraph(inner);

        var execIn = execVar(inner, "execIn", VariableKind.INPUT);
        var execOut = execVar(inner, "execOut", VariableKind.OUTPUT);
        var execInNode = inner.createVariableNode(execIn, new Vector2f(0, 0), null, null);
        var execOutNode = inner.createVariableNode(execOut, new Vector2f(400, 0), null, null);

        // inner: execIn → SetVar(secret = 5) → execOut
        var set = addNode(inner.getGraph(), SetVarNode.class);
        setOption(set, "varName", "secret");
        var add = addNode(inner.getGraph(), AddNode.class);
        setInputConstant(add, "in1", 5f);
        setInputConstant(add, "in2", 0f);
        inner.createWire(set.getInputsById().get("value"), add.getOutputsById().get("out"));
        inner.createWire(set.getInputsById().get("trigger"), execInNode.getOutputPort());
        inner.createWire(execOutNode.getInputPort(), set.getOutputsById().get("next"));

        var sub = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "sub",
                new Vector2f(0, 0), null, n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        sub.defineNode();
        var entry = addNode(outer, EntryNode.class);
        var outerSet = addNode(outer, SetVarNode.class);
        setOption(outerSet, "varName", "outerReached");
        var outerAdd = addNode(outer, AddNode.class);
        setInputConstant(outerAdd, "in1", 1f);
        setInputConstant(outerAdd, "in2", 0f);
        wire(outer, outerSet.getInputsById().get("value"), outerAdd.getOutputsById().get("out"));
        wire(outer, sub.getInputsById().get(pin(execIn)), entry.getOutputsById().get("next"));
        wire(outer, outerSet.getInputsById().get("trigger"), sub.getOutputsById().get(pin(execOut)));

        var exec = new GraphExecutor(outer);
        exec.executeFrom(entry);
        assertEq(helper, "child 'secret' must not leak to outer env", null,
                exec.getEnvironment().variables().get("secret"));
        assertTrue(helper, "outer flow still continued past subgraph",
                exec.getEnvironment().variables().get("outerReached") instanceof Number);
        helper.succeed();
    }
}
