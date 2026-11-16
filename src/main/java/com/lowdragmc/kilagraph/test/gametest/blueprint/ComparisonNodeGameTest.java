package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.ComparedType;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.NotEqualsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.EnumNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.EqualsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.EnumTypes;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addRegisteredNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.valueSource;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

@GameTestHolder(Kilagraph.MODID)
public final class ComparisonNodeGameTest {
    private static final String GT = "cmp_greater_than";
    private static final String GE = "cmp_greater_equal";
    private static final String LT = "cmp_less_than";
    private static final String LE = "cmp_less_equal";
    private static final String NEQ = "cmp_not_equals";

    private ComparisonNodeGameTest() {}

    /** Reusable: runs a 2-arg comparison node with the given a/b and asserts the out. */
    private static void cmpCase(GameTestHelper helper, Class<? extends Node> nodeClass,
                                String label, float a, float b, boolean expected) {
        var g = newGraph();
        NodeModel n = addNode(g, nodeClass);
        setInputConstant(n, "a", a);
        setInputConstant(n, "b", b);
        Boolean actual = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class);
        assertEq(helper, label + " " + a + "?" + b, expected, actual);
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void greaterThan(GameTestHelper helper) {
        cmpCase(helper, GreaterThanNode.class, ">", 5f, 3f, true);
        cmpCase(helper, GreaterThanNode.class, ">", 3f, 5f, false);
        cmpCase(helper, GreaterThanNode.class, ">", 5f, 5f, false);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void greaterEqual(GameTestHelper helper) {
        cmpCase(helper, GreaterEqualNode.class, ">=", 5f, 3f, true);
        cmpCase(helper, GreaterEqualNode.class, ">=", 3f, 5f, false);
        cmpCase(helper, GreaterEqualNode.class, ">=", 5f, 5f, true);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void lessThan(GameTestHelper helper) {
        cmpCase(helper, LessThanNode.class, "<", 3f, 5f, true);
        cmpCase(helper, LessThanNode.class, "<", 5f, 3f, false);
        cmpCase(helper, LessThanNode.class, "<", 5f, 5f, false);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void lessEqual(GameTestHelper helper) {
        cmpCase(helper, LessEqualNode.class, "<=", 3f, 5f, true);
        cmpCase(helper, LessEqualNode.class, "<=", 5f, 3f, false);
        cmpCase(helper, LessEqualNode.class, "<=", 5f, 5f, true);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void notEquals(GameTestHelper helper) {
        // Two AddNodes producing different Float values: 1.0 vs 2.0 → not equal
        var g = newGraph();
        var a = addNode(g, AddNode.class);
        var b = addNode(g, AddNode.class);
        setInputConstant(a, "in1", 1.0f); setInputConstant(a, "in2", 0.0f);
        setInputConstant(b, "in1", 2.0f); setInputConstant(b, "in2", 0.0f);
        var n = addNode(g, NotEqualsNode.class);
        wire(g, n.getInputsById().get("a"), a.getOutputsById().get("out"));
        wire(g, n.getInputsById().get("b"), b.getOutputsById().get("out"));
        assertEq(helper, "1f != 2f", Boolean.TRUE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));

        // Same value → equal
        var g2 = newGraph();
        var a2 = addNode(g2, AddNode.class);
        var b2 = addNode(g2, AddNode.class);
        setInputConstant(a2, "in1", 1.0f); setInputConstant(a2, "in2", 0.0f);
        setInputConstant(b2, "in1", 1.0f); setInputConstant(b2, "in2", 0.0f);
        var n2 = addNode(g2, NotEqualsNode.class);
        wire(g2, n2.getInputsById().get("a"), a2.getOutputsById().get("out"));
        wire(g2, n2.getInputsById().get("b"), b2.getOutputsById().get("out"));
        assertEq(helper, "1f != 1f", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));

        helper.succeed();
    }

    /**
     * A number equals a number of the same value, whatever wrapper it arrived in.
     *
     * <p>This used to be {@code Objects.equals}, which asks the wrapper: {@code Long.equals} demands a
     * {@code Long} on the other side, so a 5 produced by one node and a 5 produced by another compared
     * <em>unequal</em> whenever the two nodes happened to publish different numeric types. Nothing in
     * the editor shows which type a wire carries, so the only way to find out was for a graph to
     * misbehave.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void notEqualsComparesNumbersByValue(GameTestHelper helper) {
        record Case(String label, Class<?> ta, Object a, Class<?> tb, Object b, boolean differ) {}
        var cases = new Case[]{
                new Case("long 5 vs int 5", long.class, 5L, int.class, 5, false),
                new Case("int 5 vs float 5.0", int.class, 5, float.class, 5f, false),
                new Case("long 5 vs double 5.0", long.class, 5L, double.class, 5d, false),
                new Case("float 5.0 vs double 5.0", float.class, 5f, double.class, 5d, false),
                new Case("long 5 vs int 6", long.class, 5L, int.class, 6, true),
                new Case("int 5 vs float 5.5", int.class, 5, float.class, 5.5f, true),
                // A long past 2^53 and the double nearest it are different numbers, and comparing
                // through double would have called them equal.
                new Case("2^53+1 vs the double below it", long.class, 9007199254740993L,
                        double.class, 9007199254740992d, true),
        };
        for (Case c : cases) {
            var g = newGraph();
            var n = addNode(g, NotEqualsNode.class);
            wire(g, n.getInputsById().get("a"), valueSource(g.graphModel, "a", c.ta(), c.a()));
            wire(g, n.getInputsById().get("b"), valueSource(g.graphModel, "b", c.tb(), c.b()));
            assertEq(helper, c.label(), c.differ(),
                    new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));
        }
        helper.succeed();
    }

    /** Non-numbers keep {@code Objects.equals}, nulls included. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void notEqualsStillComparesNonNumbersByEquals(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, NotEqualsNode.class);
        wire(g, n.getInputsById().get("a"), valueSource(g.graphModel, "a", String.class, "x"));
        wire(g, n.getInputsById().get("b"), valueSource(g.graphModel, "b", String.class, "x"));
        assertEq(helper, "\"x\" != \"x\"", Boolean.FALSE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var n2 = addNode(g2, NotEqualsNode.class);
        wire(g2, n2.getInputsById().get("a"), valueSource(g2.graphModel, "a", String.class, "x"));
        wire(g2, n2.getInputsById().get("b"), valueSource(g2.graphModel, "b", String.class, "y"));
        assertEq(helper, "\"x\" != \"y\"", Boolean.TRUE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));

        // Both unwired: two nulls are equal, as they always were.
        var g3 = newGraph();
        var n3 = addNode(g3, NotEqualsNode.class);
        assertEq(helper, "null != null", Boolean.FALSE,
                new GraphExecutor(g3).evaluate(n3.getOutputsById().get("out"), Boolean.class));

        // One side a number, the other not — not equal, and not an exception either.
        var g4 = newGraph();
        var n4 = addNode(g4, NotEqualsNode.class);
        wire(g4, n4.getInputsById().get("a"), valueSource(g4.graphModel, "a", int.class, 5));
        wire(g4, n4.getInputsById().get("b"), valueSource(g4.graphModel, "b", String.class, "5"));
        assertEq(helper, "5 != \"5\"", Boolean.TRUE,
                new GraphExecutor(g4).evaluate(n4.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    /**
     * With no type picked, Equals and Not Equals declare their inputs exactly as they did before the type option
     * existed, and still take any two values.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void comparisonsWithNoTypeKeepTheirPorts(GameTestHelper helper) {
        var g = newGraph();
        var eq = addNode(g, EqualsNode.class);
        var neq = addNode(g, NotEqualsNode.class);
        assertEq(helper, "Equals in1", TypeHandles.OBJECT, eq.getInputsById().get("in1").getDataTypeHandle());
        assertEq(helper, "Equals in2", TypeHandles.OBJECT, eq.getInputsById().get("in2").getDataTypeHandle());
        assertEq(helper, "Not Equals a", TypeHandles.UNKNOWN, neq.getInputsById().get("a").getDataTypeHandle());
        assertEq(helper, "Not Equals b", TypeHandles.UNKNOWN, neq.getInputsById().get("b").getDataTypeHandle());

        wire(g, eq.getInputsById().get("in1"), valueSource(g.graphModel, "s", String.class, "x"));
        wire(g, eq.getInputsById().get("in2"), valueSource(g.graphModel, "n", int.class, 5));
        assertEq(helper, "\"x\" == 5", Boolean.FALSE,
                new GraphExecutor(g).evaluate(eq.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    /**
     * A type picked makes every input that type, and an unwired one holds a value of it: an enum nobody gave a
     * default starts at its first value, as its dropdown shows, rather than handing on null.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void comparisonsWithATypeCompareAgainstAValueOfIt(GameTestHelper helper) {
        String hand = InteractionHand.class.getName();
        var g = newGraph();
        var eq = addNode(g, EqualsNode.class);
        setOption(eq, ComparedType.OPTION, hand);
        assertEq(helper, "Equals in1 typed", hand,
                eq.getInputsById().get("in1").getDataTypeHandle().getIdentification());
        assertEq(helper, "Equals in2 typed", hand,
                eq.getInputsById().get("in2").getDataTypeHandle().getIdentification());
        wire(g, eq.getInputsById().get("in1"),
                valueSource(g.graphModel, "h", InteractionHand.class, InteractionHand.MAIN_HAND));
        assertEq(helper, "main hand == the unwired default", Boolean.TRUE,
                new GraphExecutor(g).evaluate(eq.getOutputsById().get("out"), Boolean.class));
        setInputConstant(eq, "in2", InteractionHand.OFF_HAND);
        assertEq(helper, "main hand == off hand", Boolean.FALSE,
                new GraphExecutor(g).evaluate(eq.getOutputsById().get("out"), Boolean.class));

        // the executor's own path for Not Equals reads the same two inputs
        var g2 = newGraph();
        var neq = addNode(g2, NotEqualsNode.class);
        setOption(neq, ComparedType.OPTION, hand);
        assertEq(helper, "Not Equals b typed", hand,
                neq.getInputsById().get("b").getDataTypeHandle().getIdentification());
        wire(g2, neq.getInputsById().get("a"),
                valueSource(g2.graphModel, "h", InteractionHand.class, InteractionHand.OFF_HAND));
        assertEq(helper, "off hand != the unwired default", Boolean.TRUE,
                new GraphExecutor(g2).evaluate(neq.getOutputsById().get("out"), Boolean.class));
        setInputConstant(neq, "b", InteractionHand.OFF_HAND);
        assertEq(helper, "off hand != off hand", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(neq.getOutputsById().get("out"), Boolean.class));

        // and back to any type: the ports as they were
        setOption(eq, ComparedType.OPTION, TypeHandles.UNKNOWN.getIdentification());
        assertEq(helper, "Equals in2 back to any", TypeHandles.OBJECT,
                eq.getInputsById().get("in2").getDataTypeHandle());

        // the first value was these ports' own: the type still has no default, so no other node's unwired port of it
        // changes from null
        assertEq(helper, "the type left without a default", null,
                KGTypeHandles.handleFor(InteractionHand.class).getDefaultValue());
        helper.succeed();
    }

    /**
     * The Enum node: an enum picked, then one of its values, which comes out typed as the enum — its first until another
     * is picked, and a new enum's first, not the old one's value, when the enum is changed.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void theEnumNodeGivesTheValuePicked(GameTestHelper helper) {
        var g = newGraph();
        var node = addNode(g, EnumNode.class);
        assertTrue(helper, "no value to pick before an enum is",
                node.getNodeOptions().stream().noneMatch(o -> o.id.equals(EnumNode.VALUE)));

        setOption(node, EnumNode.TYPE, InteractionHand.class.getName());
        assertEq(helper, "out typed as the enum", InteractionHand.class.getName(),
                node.getOutputsById().get("out").getDataTypeHandle().getIdentification());
        assertEq(helper, "its first value to begin with", InteractionHand.MAIN_HAND,
                new GraphExecutor(g).evaluate(node.getOutputsById().get("out"), Object.class));
        setOption(node, EnumNode.VALUE, InteractionHand.OFF_HAND);
        assertEq(helper, "the value picked", InteractionHand.OFF_HAND,
                new GraphExecutor(g).evaluate(node.getOutputsById().get("out"), Object.class));

        setOption(node, EnumNode.TYPE, Difficulty.class.getName());
        assertEq(helper, "another enum: its first value", Difficulty.values()[0],
                new GraphExecutor(g).evaluate(node.getOutputsById().get("out"), Object.class));
        assertEq(helper, "and no default given to the type", null,
                KGTypeHandles.handleFor(Difficulty.class).getDefaultValue());
        helper.succeed();
    }

    /** The Enum node keeps its enum and value through a save, and offers the graph's enum types to pick from. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void theEnumNodeKeepsItsPickThroughASave(GameTestHelper helper) {
        var g = newGraph();
        var node = addRegisteredNode(g, EnumNode.class);
        assertTrue(helper, "the graph's enums are offered: Direction",
                EnumTypes.of(((ICustomNodeModel) node).getNode()).contains(TypeHandles.DIRECTION));
        setOption(node, EnumNode.TYPE, InteractionHand.class.getName());
        setOption(node, EnumNode.VALUE, InteractionHand.OFF_HAND);
        var uid = node.getUid();

        var provider = Platform.getFrozenRegistry();
        g.graphModel.deserializeNBT(provider, g.graphModel.serializeNBT(provider));

        var back = (NodeModel) g.graphModel.getModel(uid);
        assertEq(helper, "still the enum", InteractionHand.class.getName(),
                back.getOutputsById().get("out").getDataTypeHandle().getIdentification());
        assertEq(helper, "still off hand", InteractionHand.OFF_HAND,
                new GraphExecutor(g).evaluate(back.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }

    /**
     * A graph saved before the type option existed has no value for it: it loads as any type, its ports and wires as
     * they were, and compares the same.
     *
     * <p>Every input is wired: an untyped pin has no editor to type a value into, and a constant put on one by code is
     * not kept by a save, before the option as after.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aComparisonSavedBeforeTheTypeOptionLoadsAsAny(GameTestHelper helper) {
        var g = newGraph();
        // registered, not orphaned: an orphan is not saved
        var eq = addRegisteredNode(g, EqualsNode.class);
        var neq = addRegisteredNode(g, NotEqualsNode.class);
        wire(g, eq.getInputsById().get("in1"), valueSource(g.graphModel, "a", String.class, "x"));
        wire(g, eq.getInputsById().get("in2"), valueSource(g.graphModel, "b", String.class, "x"));
        wire(g, neq.getInputsById().get("a"), valueSource(g.graphModel, "c", int.class, 5));
        wire(g, neq.getInputsById().get("b"), valueSource(g.graphModel, "d", int.class, 6));
        var eqUid = eq.getUid();
        var neqUid = neq.getUid();

        var provider = Platform.getFrozenRegistry();
        CompoundTag saved = g.graphModel.serializeNBT(provider);
        int stripped = stripOption(saved, "option_" + ComparedType.OPTION);
        assertEq(helper, "the type option found in both saved nodes", 2, stripped);
        g.graphModel.deserializeNBT(provider, saved);

        var eq2 = (NodeModel) g.graphModel.getModel(eqUid);
        var neq2 = (NodeModel) g.graphModel.getModel(neqUid);
        assertEq(helper, "Equals in2 after loading", TypeHandles.OBJECT,
                eq2.getInputsById().get("in2").getDataTypeHandle());
        assertEq(helper, "Not Equals b after loading", TypeHandles.UNKNOWN,
                neq2.getInputsById().get("b").getDataTypeHandle());
        assertEq(helper, "\"x\" == \"x\" after loading", Boolean.TRUE,
                new GraphExecutor(g).evaluate(eq2.getOutputsById().get("out"), Boolean.class));
        assertEq(helper, "5 != 6 after loading", Boolean.TRUE,
                new GraphExecutor(g).evaluate(neq2.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    /** A type picked and a value chosen for the unwired input are both kept by a save. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aTypedComparisonKeepsItsTypeAndValueThroughASave(GameTestHelper helper) {
        String hand = InteractionHand.class.getName();
        var g = newGraph();
        var eq = addRegisteredNode(g, EqualsNode.class);
        setOption(eq, ComparedType.OPTION, hand);
        wire(g, eq.getInputsById().get("in1"),
                valueSource(g.graphModel, "h", InteractionHand.class, InteractionHand.OFF_HAND));
        setInputConstant(eq, "in2", InteractionHand.OFF_HAND);
        var uid = eq.getUid();

        var provider = Platform.getFrozenRegistry();
        g.graphModel.deserializeNBT(provider, g.graphModel.serializeNBT(provider));

        var back = (NodeModel) g.graphModel.getModel(uid);
        assertEq(helper, "in2 still typed", hand, back.getInputsById().get("in2").getDataTypeHandle().getIdentification());
        assertEq(helper, "in2 still off hand", InteractionHand.OFF_HAND,
                back.getInputConstantsById().get("in2").getValue());
        assertEq(helper, "off hand == off hand after loading", Boolean.TRUE,
                new GraphExecutor(g).evaluate(back.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    /** Removes {@code key} from every node's saved input constants under {@code tag}; how many it removed. */
    private static int stripOption(Tag tag, String key) {
        int removed = 0;
        if (tag instanceof CompoundTag compound) {
            if (compound.get("inputConstants") instanceof CompoundTag constants && constants.contains(key)) {
                constants.remove(key);
                removed++;
            }
            for (String child : compound.getAllKeys()) {
                removed += stripOption(compound.get(child), key);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                removed += stripOption(child, key);
            }
        }
        return removed;
    }
}
