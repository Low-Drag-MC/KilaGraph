package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.PrintNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * {@link GraphExecutor#retainExecOutputs}: what an exec node published from {@code execute()}
 * survives {@link GraphExecutor#clearCache()} while the switch is on — on both value lanes — reads
 * as the latest publication, does not shield a pure node's memo, is not brought back once
 * invalidated, and is released by the first clear with the switch off. The default is the
 * behaviour the executor always had, and the first test pins it.
 */
@GameTestHolder(Kilagraph.MODID)
public final class ExecOutputRetentionGameTest {

    private ExecOutputRetentionGameTest() {}

    /**
     * An exec node that publishes a number and a reference from {@code execute()}: the number is
     * how many times it has run. {@code evaluate()} publishes markers, so a read that fell through
     * to it — the slot not being computed — is told apart from a retained value.
     */
    public static final class PublisherNode extends AnnotatedNode {
        @ExecInputPort public ExecutionFlow in;
        @OutputPort public float number;
        @OutputPort public String text;

        @Override
        public void execute(ExecContext ctx) {
            Object merged = ctx.state().merge("runs", 1, (a, b) -> (Integer) a + (Integer) b);
            int runs = merged instanceof Integer i ? i : 0;
            ctx.setOutput("number", (float) runs);
            ctx.setOutput("text", "run" + runs);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("number", -1.0f);
            ctx.setOutput("text", "fell-through");
        }
    }

    /**
     * As {@link PublisherNode}, but publishing nothing for {@code text}: an event whose {@code sender} is
     * null. A read of {@code text} falls through to {@code evaluate()}, whose markers must not reach
     * {@code number}.
     */
    public static final class HalfPublisherNode extends AnnotatedNode {
        @ExecInputPort public ExecutionFlow in;
        @ExecOutputPort public ExecutionFlow next;
        @OutputPort public float number;
        @OutputPort public String text;

        @Override
        public void execute(ExecContext ctx) {
            ctx.setOutput("number", 7.0f);
            ctx.setOutput("text", null);
            ctx.flow("next");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("number", -1.0f);
            ctx.setOutput("text", "fell-through");
        }
    }

    /** A pure node that counts its evaluations — the memo a clear must still drop. */
    public static final class CountingNode extends AnnotatedNode {
        @OutputPort public float evaluations;
        int count;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("evaluations", (float) ++count);
        }
    }

    /** {@code publish} runs the publisher; {@code read} prints its two outputs and the counter. */
    private record Fixture(GraphExecutor exec, NodeModel publisher, NodeModel publish, NodeModel readEntry,
                           NodeModel number, NodeModel text, NodeModel counted) {

        static Fixture build() {
            BlueprintGraph g = newGraph();
            var publisher = addNode(g, PublisherNode.class);
            var publish = addNode(g, EntryNode.class);
            wire(g, publisher.getInputsById().get("in"), publish.getOutputsById().get("next"));

            var read = addNode(g, EntryNode.class);
            var number = addNode(g, PrintNode.class);
            wire(g, number.getInputsById().get("value"), publisher.getOutputsById().get("number"));
            wire(g, number.getInputsById().get("trigger"), read.getOutputsById().get("next"));
            var text = addNode(g, PrintNode.class);
            wire(g, text.getInputsById().get("value"), publisher.getOutputsById().get("text"));
            wire(g, text.getInputsById().get("trigger"), number.getOutputsById().get("next"));
            var counter = addNode(g, CountingNode.class);
            var counted = addNode(g, PrintNode.class);
            wire(g, counted.getInputsById().get("value"), counter.getOutputsById().get("evaluations"));
            wire(g, counted.getInputsById().get("trigger"), text.getOutputsById().get("next"));
            return new Fixture(new GraphExecutor(g), publisher, publish, read, number, text, counted);
        }

        /** Runs {@code read} and answers "number/text/evaluations" as the prints saw them. */
        String read() {
            exec.executeFrom(readEntry);
            return take(number) + "/" + take(text) + "/" + take(counted);
        }

        private String take(NodeModel print) {
            Object last = exec.nodeState(print.getUid()).remove("last");
            return last instanceof Number n ? Integer.toString(n.intValue()) : String.valueOf(last);
        }
    }

    private static boolean expect(GameTestHelper helper, String what, String expected, String actual) {
        if (!expected.equals(actual)) {
            helper.fail(what + ": expected " + expected + " but read " + actual);
            return false;
        }
        return true;
    }

    /** The default: a clear drops what the exec node published, and a read falls through to evaluate(). */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void byDefaultAClearDropsWhatAnExecNodePublished(GameTestHelper helper) {
        Fixture f = Fixture.build();
        f.exec.executeFrom(f.publish);
        if (!expect(helper, "in the same run the publication is read", "1/run1/1", f.read())) return;
        f.exec.clearCache();
        if (!expect(helper, "after a clear both lanes fell through", "-1/fell-through/2", f.read())) return;
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void retainedPublicationsSurviveClearsOnBothLanesUntilTheSwitchIsOff(GameTestHelper helper) {
        Fixture f = Fixture.build();
        f.exec.executeFrom(f.publish);
        f.exec.retainExecOutputs(true);
        f.exec.clearCache();
        if (!expect(helper, "retained across a clear, number and reference alike", "1/run1/1", f.read())) return;
        f.exec.clearCache();
        if (!expect(helper, "and across the next, while the pure node's memo was dropped each time",
                "1/run1/2", f.read())) return;
        if (f.exec.retainedExecOutputs() != 2) {
            helper.fail("two publications are being retained, once each: " + f.exec.retainedExecOutputs());
            return;
        }
        f.exec.retainExecOutputs(false);
        f.exec.clearCache();
        if (!expect(helper, "the first clear with the switch off releases them", "-1/fell-through/3", f.read())) return;
        if (f.exec.retainedExecOutputs() != 0) {
            helper.fail("and forgets them: " + f.exec.retainedExecOutputs() + " still logged");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aRepublicationWhileRetainingIsWhatIsReadAfterwards(GameTestHelper helper) {
        Fixture f = Fixture.build();
        f.exec.retainExecOutputs(true);
        f.exec.executeFrom(f.publish);
        f.exec.clearCache();
        f.exec.executeFrom(f.publish);   // the entry fired again while the first chain was waiting
        f.exec.clearCache();
        if (!expect(helper, "the latest publication is what a later read sees", "2/run2/1", f.read())) return;
        f.exec.clearCache();
        if (!expect(helper, "and it stays the latest", "2/run2/2", f.read())) return;
        helper.succeed();
    }

    /**
     * <pre>
     * Entry → HalfPublisher → Print(text) → Print(number) → Print(number)
     * </pre>
     * Reading the output it published nothing for falls through to its {@code evaluate()} — and what it did
     * publish stays the run's answer: not overwritten by that evaluation, and not gone stale at the next step.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void readingWhatAnExecNodeDidNotPublishLeavesWhatItDid(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var publisher = addNode(g, HalfPublisherNode.class);
        wire(g, publisher.getInputsById().get("in"), entry.getOutputsById().get("next"));
        var text = addNode(g, PrintNode.class);
        wire(g, text.getInputsById().get("value"), publisher.getOutputsById().get("text"));
        wire(g, text.getInputsById().get("trigger"), publisher.getOutputsById().get("next"));
        var first = addNode(g, PrintNode.class);
        wire(g, first.getInputsById().get("value"), publisher.getOutputsById().get("number"));
        wire(g, first.getInputsById().get("trigger"), text.getOutputsById().get("next"));
        var second = addNode(g, PrintNode.class);
        wire(g, second.getInputsById().get("value"), publisher.getOutputsById().get("number"));
        wire(g, second.getInputsById().get("trigger"), first.getOutputsById().get("next"));
        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        String read = exec.nodeState(text.getUid()).get("last") + "/" + exec.nodeState(first.getUid()).get("last")
                + "/" + exec.nodeState(second.getUid()).get("last");
        if (!expect(helper, "the unpublished one fell through, the published one stayed", "fell-through/7.0/7.0",
                read)) return;
        helper.succeed();
    }

    /**
     * <pre>
     * Entry → HalfPublisher → For(3) body → SetVar(acc ← acc + HalfPublisher.number)
     * </pre>
     * What an exec node published stays readable in a loop body, retention off: nothing is cleared between
     * iterations. It used to be — every iteration fell through to {@code evaluate()}'s -1.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aPublicationStaysReadableThroughALoop(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.variable("acc", float.class, 0f, VariableKind.INPUT);
        b.add("entry", EntryNode.class);
        b.add("publisher", HalfPublisherNode.class);
        b.add("loop", ForNode.class).constant("loop.count", 3);
        b.add("sum", AddNode.class).wire("sum.in1", "acc").wire("sum.in2", "publisher.number");
        b.add("setAcc", SetVarNode.class).option("setAcc", "varName", "acc").wire("setAcc.value", "sum");
        b.wire("publisher.in", "entry");
        b.wire("loop.in", "publisher.next");
        b.wire("setAcc.trigger", "loop.body");

        var exec = new GraphExecutor(b.graph());
        exec.executeFrom(b.node("entry"));
        Object acc = exec.getEnvironment().variables().get("acc");
        if (!expect(helper, "three iterations each read the publication", "21",
                acc instanceof Number n ? Integer.toString(Math.round(n.floatValue())) : String.valueOf(acc))) return;
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void anInvalidatedPublicationIsNotBroughtBackByRetention(GameTestHelper helper) {
        Fixture f = Fixture.build();
        f.exec.retainExecOutputs(true);
        f.exec.executeFrom(f.publish);
        f.exec.invalidateNodeOutputs(f.publisher);
        f.exec.clearCache();
        if (!expect(helper, "invalidated before the clear, it fell through", "-1/fell-through/1", f.read())) return;
        helper.succeed();
    }
}
