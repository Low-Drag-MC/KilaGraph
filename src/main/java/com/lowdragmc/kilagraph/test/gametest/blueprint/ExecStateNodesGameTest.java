package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.DoNNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.DoOnceNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.FlipFlopNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.MultiGateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.PrintNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ToggleGateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * The stateful flow nodes — DoOnce, DoN, FlipFlop, MultiGate, the toggling Gate — and, under
 * them, {@code ExecContext.enteredPort()}: a node with several exec inputs knows which one the
 * flow came in through. Each input is its own Entry node here, run one at a time on the same
 * executor, so the node's state carries over between runs the way it does between two frames.
 */
@GameTestHolder(Kilagraph.MODID)
public final class ExecStateNodesGameTest {

    private ExecStateNodesGameTest() {}

    /** A Print that records {@code value} in its state as "last" — the one observable an exec chain has here. */
    private static NodeModel print(BlueprintGraph g, float value) {
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", value);
        setInputConstant(add, "in2", 0.0f);
        var p = addNode(g, PrintNode.class);
        wire(g, p.getInputsById().get("value"), add.getOutputsById().get("out"));
        return p;
    }

    /** What the print last saw, then forgotten — so the next run's silence reads as silence. */
    private static Float take(GraphExecutor exec, NodeModel print) {
        Object last = exec.nodeState(print.getUid()).remove("last");
        return last instanceof Number n ? n.floatValue() : null;
    }

    private static boolean is(Float value, float expected) {
        return value != null && Math.abs(value - expected) < 1e-5f;
    }

    /** An Entry whose {@code next} enters {@code node} through {@code inputId}. */
    private static NodeModel entryInto(BlueprintGraph g, NodeModel node, String inputId) {
        var entry = addNode(g, EntryNode.class);
        wire(g, node.getInputsById().get(inputId), entry.getOutputsById().get("next"));
        return entry;
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void doOncePassesOnceUntilReset(GameTestHelper helper) {
        var g = newGraph();
        var once = addNode(g, DoOnceNode.class);
        var in = entryInto(g, once, "in");
        var reset = entryInto(g, once, "reset");
        var p = print(g, 7f);
        wire(g, p.getInputsById().get("trigger"), once.getOutputsById().get("out"));
        var exec = new GraphExecutor(g);

        exec.executeFrom(in);
        if (!is(take(exec, p), 7f)) { helper.fail("the first pass went through"); return; }
        exec.executeFrom(in);
        if (take(exec, p) != null) { helper.fail("the second was swallowed"); return; }
        exec.executeFrom(reset);
        if (take(exec, p) != null) { helper.fail("reset itself fires nothing"); return; }
        exec.executeFrom(in);
        if (!is(take(exec, p), 7f)) { helper.fail("after a reset it passes again"); return; }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void doOnceStartClosedNeedsAResetFirst(GameTestHelper helper) {
        var g = newGraph();
        var once = addNode(g, DoOnceNode.class);
        setOption(once, "startClosed", true);
        var in = entryInto(g, once, "in");
        var reset = entryInto(g, once, "reset");
        var p = print(g, 3f);
        wire(g, p.getInputsById().get("trigger"), once.getOutputsById().get("out"));
        var exec = new GraphExecutor(g);

        exec.executeFrom(in);
        if (take(exec, p) != null) { helper.fail("closed to begin with"); return; }
        exec.executeFrom(reset);
        exec.executeFrom(in);
        if (!is(take(exec, p), 3f)) { helper.fail("open after the reset"); return; }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void doNPassesNTimesAndCountsThem(GameTestHelper helper) {
        var g = newGraph();
        var doN = addNode(g, DoNNode.class);
        setInputConstant(doN, "n", 2);
        var enter = entryInto(g, doN, "enter");
        var reset = entryInto(g, doN, "reset");
        var p = addNode(g, PrintNode.class);
        wire(g, p.getInputsById().get("trigger"), doN.getOutputsById().get("exit"));
        wire(g, p.getInputsById().get("value"), doN.getOutputsById().get("counter"));
        var exec = new GraphExecutor(g);

        exec.executeFrom(enter);
        if (!is(take(exec, p), 1f)) { helper.fail("first pass counts 1"); return; }
        exec.executeFrom(enter);
        if (!is(take(exec, p), 2f)) { helper.fail("second pass counts 2"); return; }
        exec.executeFrom(enter);
        if (take(exec, p) != null) { helper.fail("the third is swallowed"); return; }
        exec.executeFrom(reset);
        exec.executeFrom(enter);
        if (!is(take(exec, p), 1f)) { helper.fail("after a reset the count starts over"); return; }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void flipFlopAlternatesAndSaysWhich(GameTestHelper helper) {
        var g = newGraph();
        var flip = addNode(g, FlipFlopNode.class);
        var in = entryInto(g, flip, "in");
        var pa = print(g, 1f);
        var pb = print(g, 2f);
        wire(g, pa.getInputsById().get("trigger"), flip.getOutputsById().get("a"));
        wire(g, pb.getInputsById().get("trigger"), flip.getOutputsById().get("b"));
        var exec = new GraphExecutor(g);

        exec.executeFrom(in);
        if (!is(take(exec, pa), 1f) || take(exec, pb) != null) { helper.fail("A first"); return; }
        exec.executeFrom(in);
        if (take(exec, pa) != null || !is(take(exec, pb), 2f)) { helper.fail("then B"); return; }
        exec.executeFrom(in);
        if (!is(take(exec, pa), 1f)) { helper.fail("then A again"); return; }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void theToggleGateOpensClosesAndTogglesByWhichPinWasEntered(GameTestHelper helper) {
        var g = newGraph();
        var gate = addNode(g, ToggleGateNode.class);
        var enter = entryInto(g, gate, "enter");
        var open = entryInto(g, gate, "open");
        var close = entryInto(g, gate, "close");
        var toggle = entryInto(g, gate, "toggle");
        var p = print(g, 5f);
        wire(g, p.getInputsById().get("trigger"), gate.getOutputsById().get("exit"));
        var exec = new GraphExecutor(g);

        exec.executeFrom(enter);
        if (!is(take(exec, p), 5f)) { helper.fail("open to begin with"); return; }
        exec.executeFrom(close);
        exec.executeFrom(enter);
        if (take(exec, p) != null) { helper.fail("closed: nothing passes"); return; }
        exec.executeFrom(toggle);
        exec.executeFrom(enter);
        if (!is(take(exec, p), 5f)) { helper.fail("toggled open"); return; }
        exec.executeFrom(toggle);
        exec.executeFrom(enter);
        if (take(exec, p) != null) { helper.fail("toggled closed"); return; }
        exec.executeFrom(open);
        exec.executeFrom(enter);
        if (!is(take(exec, p), 5f)) { helper.fail("opened"); return; }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void multiGateFiresEachOutputOnceInOrderThenNothingUnlessItLoops(GameTestHelper helper) {
        var g = newGraph();
        var multi = addNode(g, MultiGateNode.class);
        setOption(multi, "outputs", 3);
        var in = entryInto(g, multi, "in");
        var reset = entryInto(g, multi, "reset");
        var p1 = print(g, 1f);
        var p2 = print(g, 2f);
        var p3 = print(g, 3f);
        wire(g, p1.getInputsById().get("trigger"), multi.getOutputsById().get("out1"));
        wire(g, p2.getInputsById().get("trigger"), multi.getOutputsById().get("out2"));
        wire(g, p3.getInputsById().get("trigger"), multi.getOutputsById().get("out3"));
        var exec = new GraphExecutor(g);

        exec.executeFrom(in);
        if (!is(take(exec, p1), 1f)) { helper.fail("out1 first"); return; }
        exec.executeFrom(in);
        if (!is(take(exec, p2), 2f)) { helper.fail("out2 second"); return; }
        exec.executeFrom(in);
        if (!is(take(exec, p3), 3f)) { helper.fail("out3 third"); return; }
        exec.executeFrom(in);
        if (take(exec, p1) != null || take(exec, p2) != null || take(exec, p3) != null) {
            helper.fail("every output used and no loop: nothing fires");
            return;
        }
        exec.executeFrom(reset);
        exec.executeFrom(in);
        if (!is(take(exec, p1), 1f)) { helper.fail("after a reset it starts over at out1"); return; }

        // looping: the round starts over by itself
        var g2 = newGraph();
        var looped = addNode(g2, MultiGateNode.class);
        setOption(looped, "outputs", 2);
        setOption(looped, "loop", true);
        var in2 = entryInto(g2, looped, "in");
        var q1 = print(g2, 1f);
        var q2 = print(g2, 2f);
        wire(g2, q1.getInputsById().get("trigger"), looped.getOutputsById().get("out1"));
        wire(g2, q2.getInputsById().get("trigger"), looped.getOutputsById().get("out2"));
        var exec2 = new GraphExecutor(g2);
        exec2.executeFrom(in2);
        exec2.executeFrom(in2);
        take(exec2, q1);
        take(exec2, q2);
        exec2.executeFrom(in2);
        if (!is(take(exec2, q1), 1f)) { helper.fail("looping: the third trigger is out1 again"); return; }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void multiGateStartIndexPicksWhereARoundBegins(GameTestHelper helper) {
        var g = newGraph();
        var multi = addNode(g, MultiGateNode.class);
        setOption(multi, "outputs", 3);
        setInputConstant(multi, "startIndex", 2);
        var in = entryInto(g, multi, "in");
        var p1 = print(g, 1f);
        var p2 = print(g, 2f);
        var p3 = print(g, 3f);
        wire(g, p1.getInputsById().get("trigger"), multi.getOutputsById().get("out1"));
        wire(g, p2.getInputsById().get("trigger"), multi.getOutputsById().get("out2"));
        wire(g, p3.getInputsById().get("trigger"), multi.getOutputsById().get("out3"));
        var exec = new GraphExecutor(g);

        exec.executeFrom(in);
        if (!is(take(exec, p2), 2f)) { helper.fail("a start index of 2 begins at out2"); return; }
        exec.executeFrom(in);
        if (!is(take(exec, p3), 3f)) { helper.fail("then out3"); return; }
        exec.executeFrom(in);
        if (!is(take(exec, p1), 1f)) { helper.fail("then wraps to out1, which was not used yet"); return; }
        helper.succeed();
    }
}
