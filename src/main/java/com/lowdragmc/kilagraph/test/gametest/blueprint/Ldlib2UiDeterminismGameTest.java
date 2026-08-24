package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.doc.UIDocNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIElementNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.event.UIEventNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.sync.UIRpcNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.sync.UISyncNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * A UI graph must build the same thing every time it runs.
 *
 * <h2>Why this is a test and not a convention</h2>
 * {@code UISyncManager} identifies a sync value or an RPC on the wire by <b>the order it was
 * registered in</b> — an index into an insertion-ordered map, not a name. The client and the server
 * each run the graph independently and then trust those indices to line up.
 *
 * <p>Which means a graph whose build order varies between runs does not fail loudly. It produces a
 * UI where the burn time decodes into the progress bar's slot: every packet still parses, every
 * number is still a number, and nothing throws. That is the worst class of bug to leave uncovered,
 * so the property is pinned here rather than only written down.</p>
 *
 * <p>Two executors over one graph is the closest single-process stand-in for two sides: separate pull
 * caches, separate node state, the same nodes.</p>
 */
public final class Ldlib2UiDeterminismGameTest {
    private static final String A_STRAIGHT_BUILD_IS_DETERMINISTIC = "ldlib2_ui_determinism_a_straight_build_is_deterministic";
    private static final String A_LOOPED_BUILD_IS_DETERMINISTIC = "ldlib2_ui_determinism_a_looped_build_is_deterministic";

    public static void registerFunctions() {
        KGGameTests.registerFunction(A_STRAIGHT_BUILD_IS_DETERMINISTIC, Ldlib2UiDeterminismGameTest::aStraightBuildIsDeterministic);
        KGGameTests.registerFunction(A_LOOPED_BUILD_IS_DETERMINISTIC, Ldlib2UiDeterminismGameTest::aLoopedBuildIsDeterministic);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                A_STRAIGHT_BUILD_IS_DETERMINISTIC, A_LOOPED_BUILD_IS_DETERMINISTIC
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private Ldlib2UiDeterminismGameTest() {
    }

    /** A straight-line build registers the same things in the same order, run after run. */
    public static void aStraightBuildIsDeterministic(GameTestHelper helper) {
        assertSameRegistrationOrder(helper, "straight build", Ldlib2UiDeterminismGameTest::mixedGraph);
        helper.succeed();
    }

    /** A build that makes its rows in a loop is deterministic too — and really does make N of them. */
    public static void aLoopedBuildIsDeterministic(GameTestHelper helper) {
        assertSameRegistrationOrder(helper, "looped build", Ldlib2UiDeterminismGameTest::loopGraph);

        // The loop must also produce a distinct element per iteration. A constructor that memoised
        // across iterations would give a perfectly deterministic build of exactly one row, so the
        // order check alone would not catch it.
        var g = loopGraph();
        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        UIElement panel = exec.evaluate(g.outputOf("panel.element"), UIElement.class);
        assertEq(helper, "one row per iteration", 3, panel.getChildren().size());
        assertTrue(helper, "and they are distinct objects",
                panel.getChildren().getFirst() != panel.getChildren().get(1));
        helper.succeed();
    }

    /**
     * Runs {@code build} twice on two executors and compares what reached the sync manager.
     *
     * <p>Compared by <em>name and position</em>, not just count: two sync values swapping places
     * keeps the count identical and is exactly the failure this exists to catch.</p>
     */
    private static void assertSameRegistrationOrder(GameTestHelper helper, String label,
                                                    Supplier<KGGraphBuilder> build) {
        List<String> first = registrationOrder(build.get());
        List<String> second = registrationOrder(build.get());
        assertTrue(helper, label + ": something was registered", !first.isEmpty());
        assertEq(helper, label + ": same number registered", first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEq(helper, label + ": entry " + i, first.get(i), second.get(i));
        }
    }

    /**
     * The names of everything registered on the UI's sync manager, in registration order.
     *
     * <p>Read off the element tree rather than out of the manager, because the manager's map is
     * private — but the order is the same one: {@code _setModularUIInternal} registers an element's
     * values as it is mounted, walking the tree depth-first, so a depth-first walk reproduces it.</p>
     */
    private static List<String> registrationOrder(KGGraphBuilder g) {
        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        UIElement root = exec.evaluate(g.outputOf("panel.element"), UIElement.class);

        var names = new ArrayList<String>();
        root.selfAndAllChildren().forEach(element -> {
            for (var value : syncValuesOf(element)) {
                names.add("sync:" + value.syncValueHolder.managedKey.getName());
            }
            names.add("rpcs:" + element.getId() + ":" + rpcCountOf(element));
        });
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<com.lowdragmc.lowdraglib2.gui.sync.SyncValue<?>> syncValuesOf(UIElement element) {
        return (List<com.lowdragmc.lowdraglib2.gui.sync.SyncValue<?>>) readField(element, "syncValues");
    }

    private static int rpcCountOf(UIElement element) {
        return ((List<?>) readField(element, "rpcEvents")).size();
    }

    private static Object readField(UIElement element, String name) {
        try {
            var field = UIElement.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(element);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("UIElement." + name + " is gone — update this test", e);
        }
    }

    // ---- graphs ------------------------------------------------------------------------------

    /**
     * A build with all three kinds of registration on two elements: a sync value, a server event
     * (which registers an RPC behind the scenes), and an explicit RPC.
     */
    private static KGGraphBuilder mixedGraph() {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("panel", UIElementNodes.New.class)
                .add("button", UIElementNodes.New.class)
                .add("attach", UIElementNodes.AddChild.class)
                .add("sync", UISyncNodes.Declare.class)
                .add("serverEvent", UIEventNodes.OnServerEvent.class)
                .add("rpc", UIRpcNodes.Define.class)
                .add("ui", UIDocNodes.Create.class)
                .add("mui", UIDocNodes.ModularCreate.class);
        g.constant("panel.id", "panel")
                .option("button", "type", "button").constant("button.id", "ok")
                .option("sync", "valueType", TypeHandles.INT.getIdentification())
                .constant("sync.name", "burnTime")
                .wire("sync.element", "panel.element")
                .option("serverEvent", "eventType", UIEvents.CLICK)
                .wire("serverEvent.element", "button.element")
                .option("rpc", "argCount", 0)
                .wire("rpc.element", "button.element")
                .wire("attach.parent", "panel.element")
                .wire("attach.child", "button.element")
                .wire("ui.root", "panel.element")
                .wire("mui.ui", "ui.ui")
                .then("entry", "panel", "button", "sync")
                .then("sync.next", "serverEvent")
                .then("serverEvent.next", "rpc")
                .then("rpc.next", "attach", "ui", "mui");
        return g;
    }

    /** Three rows built in a loop, each with its own sync value. */
    private static KGGraphBuilder loopGraph() {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("panel", UIElementNodes.New.class)
                .add("loop", ForNode.class)
                .add("row", UIElementNodes.New.class)
                .add("sync", UISyncNodes.Declare.class)
                .add("attach", UIElementNodes.AddChild.class)
                .add("ui", UIDocNodes.Create.class)
                .add("mui", UIDocNodes.ModularCreate.class);
        g.constant("panel.id", "panel")
                .constant("loop.count", 3)
                .option("row", "type", "label")
                .option("sync", "valueType", TypeHandles.INT.getIdentification())
                .constant("sync.name", "row")
                .wire("sync.element", "row.element")
                .wire("attach.parent", "panel.element")
                .wire("attach.child", "row.element")
                .wire("ui.root", "panel.element")
                .wire("mui.ui", "ui.ui")
                .then("entry", "panel", "loop")
                .then("loop.body", "row", "sync")
                .then("sync.next", "attach")
                .then("loop.completed", "ui", "mui");
        return g;
    }
}
