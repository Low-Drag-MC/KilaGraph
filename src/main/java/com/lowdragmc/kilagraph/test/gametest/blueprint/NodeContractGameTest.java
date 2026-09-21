package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.container.FluidContainerNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.loot.LootNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIElementInfoBlocks;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIElementInfoNode;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.sync.UIRpcNodes;
import com.lowdragmc.kilagraph.graph.core.IThreadSafeNode;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.ArrayList;
import java.util.List;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;

/**
 * What a node <em>declares</em> about itself, as opposed to what it computes: the order its ports
 * appear in, and whether a host may run it off the game thread.
 *
 * <p>Both are invisible to every other test in this suite. A node evaluates identically whichever
 * order its pins are drawn in, and {@link IThreadSafeNode#isThreadSafe()} is read by the host rather
 * than by the executor — so the only thing that can notice either going wrong is a test that asks.
 */
public final class NodeContractGameTest {
    private static final String PORT_ORDER = "node_contract_declared_ports_come_first";
    private static final String UNSAFE_DECLARED = "node_contract_world_writers_declare_unsafe";
    private static final String SAFE_BY_DEFAULT = "node_contract_ordinary_nodes_stay_safe";

    /**
     * Package prefixes whose every node reaches something the game thread owns, and so must answer
     * {@code false}. Enumerated by package rather than by class so that a node added to one of them
     * later is covered without this list being touched.
     */
    private static final List<String> UNSAFE_PACKAGES = List.of(
            "com.lowdragmc.kilagraph.blueprint.nodes.mc.action.",
            "com.lowdragmc.kilagraph.blueprint.nodes.ui.");

    /** The individual nodes that write while looking like queries. @see IThreadSafeNode */
    private static final List<Class<? extends Node>> UNSAFE_ONE_OFFS = List.of(
            FluidContainerNodes.Fill.class, FluidContainerNodes.Drain.class,
            LootNodes.Roll.class, LootNodes.BlockDrops.class);

    public static void registerFunctions() {
        KGGameTests.registerFunction(PORT_ORDER, NodeContractGameTest::declaredPortsComeBeforeDynamicOnes);
        KGGameTests.registerFunction(UNSAFE_DECLARED, NodeContractGameTest::worldWritingNodesDeclareThemselvesUnsafe);
        KGGameTests.registerFunction(SAFE_BY_DEFAULT, NodeContractGameTest::ordinaryNodesStayThreadSafe);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var data = KGGameTests.defaultTestData(environment);
        for (String p : new String[]{PORT_ORDER, UNSAFE_DECLARED, SAFE_BY_DEFAULT}) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), data);
        }
    }

    private NodeContractGameTest() {
    }

    /**
     * A node's own {@code @…Port} fields sit above the ports it defines in
     * {@code onDefineDynamicPorts}, and exec pins sit above data pins.
     *
     * <p>This is an invariant, not a mechanism: a port takes its place in the display order at the
     * moment its builder is <em>built</em>, so the order depends on when each one happens to be. It
     * currently holds two ways over — {@code NodeMetadata.applyPort} builds eagerly, and LDLib2's
     * {@code finish()} would drain the rest in creation order anyway — and the case that would break
     * it is a dynamic hook that builds a port of its own, jumping the whole declared block. Asking
     * for the invariant rather than for either mechanism is what would catch that.</p>
     */
    public static void declaredPortsComeBeforeDynamicOnes(GameTestHelper helper) {
        var g = newGraph();
        NodeModel send = addNode(g, UIRpcNodes.Send.class);
        setOption(send, "argCount", 2);

        List<String> inputs = ids(send.getInputsByDisplayOrder());
        assertTrue(helper, "the rpc call has its argument pins, got " + inputs,
                inputs.contains("arg1") && inputs.contains("arg2"));
        assertBefore(helper, inputs, "trigger", "arg1");
        assertBefore(helper, inputs, "rpc", "arg1");
        assertBefore(helper, inputs, "arg1", "arg2");

        List<String> outputs = ids(send.getOutputsByDisplayOrder());
        assertBefore(helper, outputs, "next", "result");
        assertBefore(helper, outputs, "onReturn", "result");
        assertBefore(helper, outputs, "ok", "result");

        // The exec-before-data rule applyPorts() exists for still holds on top of it.
        NodeModel setVar = addNode(g, SetVarNode.class);
        assertBefore(helper, ids(setVar.getInputsByDisplayOrder()), "trigger", "value");
        helper.succeed();
    }

    public static void worldWritingNodesDeclareThemselvesUnsafe(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        for (Class<? extends Node> cls : BlueprintGraph.NODE_REGISTRY.getNodeClasses()) {
            boolean shouldBeUnsafe = UNSAFE_ONE_OFFS.contains(cls)
                    || UNSAFE_PACKAGES.stream().anyMatch(p -> cls.getName().startsWith(p));
            if (!shouldBeUnsafe) continue;
            if (threadSafe(failures, cls) != Boolean.FALSE) {
                failures.add(cls.getName() + " must declare isThreadSafe() = false");
            }
        }
        // The two info shapes that cannot inherit UINode, checked by class since they are not in the
        // registry under a package rule that would reach them.
        for (Class<? extends Node> cls : List.of(UIElementInfoNode.class,
                UIElementInfoBlocks.Identity.class)) {
            if (threadSafe(failures, cls) != Boolean.FALSE) {
                failures.add(cls.getName() + " must declare isThreadSafe() = false");
            }
        }
        if (!failures.isEmpty()) {
            helper.fail(failures.size() + " unmarked: " + String.join(" | ", failures));
            return;
        }
        assertFalse(helper, "the UI base itself", new UIRpcNodes.Send().isThreadSafe());
        helper.succeed();
    }

    /** The default is yes, and the arithmetic half of the library must not have drifted off it. */
    public static void ordinaryNodesStayThreadSafe(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        for (Class<? extends Node> cls : BlueprintGraph.NODE_REGISTRY.getNodeClasses()) {
            NodeAttribute attribute = cls.getAnnotation(NodeAttribute.class);
            if (attribute == null) continue;
            String group = attribute.group();
            if (!group.equals("math") && !group.equals("vector") && !group.equals("quaternion")
                    && !group.equals("logic") && !group.equals("compare") && !group.equals("string")) {
                continue;
            }
            if (threadSafe(failures, cls) != Boolean.TRUE) {
                failures.add(attribute.name() + " is pure arithmetic and should be thread-safe");
            }
        }
        if (!failures.isEmpty()) {
            helper.fail(failures.size() + " wrongly marked: " + String.join(" | ", failures));
            return;
        }
        assertTrue(helper, "a plain math node", new AddNode().isThreadSafe());
        helper.succeed();
    }

    /**
     * A node class's answer, or null when it does not implement the interface at all — which for a
     * node reached by one of the rules above is itself the failure.
     */
    private static Boolean threadSafe(List<String> failures, Class<? extends Node> cls) {
        try {
            Node node = cls.getDeclaredConstructor().newInstance();
            return node instanceof IThreadSafeNode safe ? safe.isThreadSafe() : null;
        } catch (ReflectiveOperationException e) {
            failures.add(cls.getName() + " could not be instantiated: " + e);
            return null;
        }
    }

    private static List<String> ids(List<PortModel> ports) {
        List<String> out = new ArrayList<>(ports.size());
        for (PortModel p : ports) out.add(p.getPortId());
        return out;
    }

    private static void assertBefore(GameTestHelper helper, List<String> order, String first, String second) {
        int a = order.indexOf(first);
        int b = order.indexOf(second);
        if (a < 0 || b < 0) {
            helper.fail("expected both " + first + " and " + second + " in " + order);
            return;
        }
        if (a > b) {
            helper.fail(first + " should come before " + second + ", order was " + order);
        }
    }
}
