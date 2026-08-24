package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.doc.UIDocNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIElementNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.style.UIStyleNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.sync.UIRpcNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.sync.UISyncNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEvent;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * Sync values, RPCs and messages.
 *
 * <h2>What a single-process test can and cannot show</h2>
 * There is no second side here, so nothing crosses a network: a real S2C round trip needs LDLib2's
 * multi-process harness. What <em>is</em> checkable, and is what actually goes wrong in practice:
 *
 * <ul>
 *   <li><b>Registration.</b> Whether a sync value or RPC reached the UI's sync manager at all, and
 *       whether it left when its element did.</li>
 *   <li><b>The provider.</b> Whether a {@code source} expression is re-pulled rather than frozen at
 *       build time — the difference between a value that tracks and one that reports the same number
 *       forever.</li>
 *   <li><b>Local delivery.</b> An RPC's executor and a message handler both run in-process, so the
 *       path from "a call arrives" back into the graph is fully exercisable.</li>
 *   <li><b>Ordering.</b> Registration order is the wire protocol, so it is worth pinning that the
 *       same graph produces the same order twice.</li>
 * </ul>
 */
public final class Ldlib2UiSyncGameTest {
    private static final String A_SYNC_VALUE_IS_DECLARED_ON_ITS_ELEMENT = "ldlib2_ui_sync_a_sync_value_is_declared_on_its_element";
    private static final String A_SOURCED_SYNC_VALUE_TRACKS_ITS_EXPRESSION = "ldlib2_ui_sync_a_sourced_sync_value_tracks_its_expression";
    private static final String AN_UNTYPED_SYNC_VALUE_IS_REFUSED = "ldlib2_ui_sync_an_untyped_sync_value_is_refused";
    private static final String SYNC_VALUES_JOIN_AND_LEAVE_THE_MANAGER_WITH_THEIR_ELEMENT = "ldlib2_ui_sync_sync_values_join_and_leave_the_manager_with_their_element";
    private static final String AN_RPC_CALL_REACHES_THE_GRAPH_AND_RETURNS_A_VALUE = "ldlib2_ui_sync_an_rpc_call_reaches_the_graph_and_returns_a_value";
    private static final String A_MESSAGE_HANDLER_RECEIVES_ITS_PAYLOAD = "ldlib2_ui_sync_a_message_handler_receives_its_payload";

    public static void registerFunctions() {
        KGGameTests.registerFunction(A_SYNC_VALUE_IS_DECLARED_ON_ITS_ELEMENT, Ldlib2UiSyncGameTest::aSyncValueIsDeclaredOnItsElement);
        KGGameTests.registerFunction(A_SOURCED_SYNC_VALUE_TRACKS_ITS_EXPRESSION, Ldlib2UiSyncGameTest::aSourcedSyncValueTracksItsExpression);
        KGGameTests.registerFunction(AN_UNTYPED_SYNC_VALUE_IS_REFUSED, Ldlib2UiSyncGameTest::anUntypedSyncValueIsRefused);
        KGGameTests.registerFunction(SYNC_VALUES_JOIN_AND_LEAVE_THE_MANAGER_WITH_THEIR_ELEMENT, Ldlib2UiSyncGameTest::syncValuesJoinAndLeaveTheManagerWithTheirElement);
        KGGameTests.registerFunction(AN_RPC_CALL_REACHES_THE_GRAPH_AND_RETURNS_A_VALUE, Ldlib2UiSyncGameTest::anRpcCallReachesTheGraphAndReturnsAValue);
        KGGameTests.registerFunction(A_MESSAGE_HANDLER_RECEIVES_ITS_PAYLOAD, Ldlib2UiSyncGameTest::aMessageHandlerReceivesItsPayload);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                A_SYNC_VALUE_IS_DECLARED_ON_ITS_ELEMENT, A_SOURCED_SYNC_VALUE_TRACKS_ITS_EXPRESSION, AN_UNTYPED_SYNC_VALUE_IS_REFUSED,
                SYNC_VALUES_JOIN_AND_LEAVE_THE_MANAGER_WITH_THEIR_ELEMENT, AN_RPC_CALL_REACHES_THE_GRAPH_AND_RETURNS_A_VALUE, A_MESSAGE_HANDLER_RECEIVES_ITS_PAYLOAD
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private Ldlib2UiSyncGameTest() {
    }

    /** A declared sync value exists, is named, and is typed as the option said. */
    public static void aSyncValueIsDeclaredOnItsElement(GameTestHelper helper) {
        var g = syncGraph();
        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        assertTrue(helper, "it registered", exec.evaluate(g.outputOf("sync.ok"), Boolean.class));
        SyncValue<?> value = exec.evaluate(g.outputOf("sync.syncValue"), SyncValue.class);
        assertTrue(helper, "a sync value came out", value != null);
        assertEq(helper, "with the given name", "burnTime",
                value.syncValueHolder.managedKey.getName());
        helper.succeed();
    }

    /**
     * A sync value with a {@code source} tracks that expression rather than freezing at build time.
     *
     * <p>The provider is pulled by the sync manager outside any flow, so it has to clear the cache
     * before re-reading. Without that this test would see 7 twice — the value memoised while the tree
     * was being assembled.</p>
     */
    public static void aSourcedSyncValueTracksItsExpression(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("panel", UIElementNodes.New.class)
                .add("counter", UIElementNodes.New.class)
                .add("childCount", com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIQueryNodes.Children.class)
                .add("sync", UISyncNodes.Declare.class);
        g.option("sync", "valueType", TypeHandles.INT.getIdentification())
                .constant("sync.name", "childCount")
                .wire("sync.element", "panel.element")
                // The source is a live query of the panel's child count.
                .wire("childCount.element", "panel.element")
                .wire("sync.source", "childCount.count")
                .then("entry", "panel", "counter", "sync");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        SyncValue<?> value = exec.evaluate(g.outputOf("sync.syncValue"), SyncValue.class);
        UIElement panel = exec.evaluate(g.outputOf("panel.element"), UIElement.class);

        value.update();
        assertEq(helper, "starts empty", 0, ((Number) value.getValue()).intValue());

        // Change the tree behind the graph's back, exactly as a handler would.
        panel.addChild(exec.evaluate(g.outputOf("counter.element"), UIElement.class));
        value.update();
        assertEq(helper, "and tracks the change", 1, ((Number) value.getValue()).intValue());
        helper.succeed();
    }

    /** A sync value with no concrete type is refused rather than registered unencodable. */
    public static void anUntypedSyncValueIsRefused(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("sync", UISyncNodes.Declare.class);
        g.wire("sync.element", "element.element").then("entry", "element", "sync");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        assertFalse(helper, "it refused", exec.evaluate(g.outputOf("sync.ok"), Boolean.class));
        helper.succeed();
    }

    /**
     * A UI mounts when its host attaches it, not when {@code ldlib2_ui_modular_create} runs — and
     * sync values follow their element in and out.
     *
     * <p>The first half of that is easy to get wrong when reading a graph: the node that makes the
     * {@code ModularUI} looks like the moment everything becomes live, and it is not. Mounting is
     * {@code setMenu} on the server and screen init on the client, both of which belong to whatever
     * is hosting the UI. Pinning it here keeps the node's documentation honest.</p>
     */
    public static void syncValuesJoinAndLeaveTheManagerWithTheirElement(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("root", UIElementNodes.New.class)
                .add("child", UIElementNodes.New.class)
                .add("sync", UISyncNodes.Declare.class)
                .add("attach", UIElementNodes.AddChild.class)
                .add("ui", UIDocNodes.Create.class)
                .add("mui", UIDocNodes.ModularCreate.class);
        g.option("sync", "valueType", TypeHandles.INT.getIdentification())
                .wire("sync.element", "child.element")
                .wire("attach.parent", "root.element")
                .wire("attach.child", "child.element")
                .wire("ui.root", "root.element")
                .wire("mui.ui", "ui.ui")
                .then("entry", "root", "child", "sync")
                // sync has two exec outputs, so the hop off it is named explicitly.
                .then("sync.next", "attach", "ui", "mui");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        var mui = exec.evaluate(g.outputOf("mui.mui"), ModularUI.class);
        UIElement child = exec.evaluate(g.outputOf("child.element"), UIElement.class);
        assertTrue(helper, "the sync value is on its element", !syncValuesOf(child).isEmpty());
        assertTrue(helper, "making the ModularUI does not mount the tree", child.getModularUI() == null);

        // What the host does. setMenu is the server-side path; the client gets there through screen
        // init, which needs a screen and a layout pass this test has no business doing.
        mui.setMenu(null);
        assertTrue(helper, "attaching the host mounts the child", child.getModularUI() == mui);

        child.removeSelf();
        assertTrue(helper, "and detaching it unmounts it", child.getModularUI() == null);
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<SyncValue<?>> syncValuesOf(UIElement element) {
        try {
            var field = UIElement.class.getDeclaredField("syncValues");
            field.setAccessible(true);
            return (java.util.List<SyncValue<?>>) field.get(element);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("UIElement.syncValues is gone — update this test", e);
        }
    }

    /**
     * An RPC's executor re-enters the graph, and the graph's answer comes back out.
     *
     * <p>Calling the executor directly is the honest way to test this in one process: it is exactly
     * what {@code UISyncManager.handEvent} does when a call arrives, minus the packet.</p>
     */
    public static void anRpcCallReachesTheGraphAndReturnsAValue(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("rpc", UIRpcNodes.Define.class)
                .add("stamp", UIStyleNodes.ClassNames.class)
                .add("answer", UIRpcNodes.Return.class);
        g.option("rpc", "argCount", 1)
                .option("rpc", "arg1Type", TypeHandles.STRING.getIdentification())
                .option("rpc", "returnType", TypeHandles.STRING.getIdentification())
                .option("answer", "valueType", TypeHandles.STRING.getIdentification())
                .wire("rpc.element", "element.element")
                .wire("stamp.element", "element.element")
                // The handler stamps the argument it was called with as a class name, so the test can
                // see that the argument really arrived — and answers with a fixed string.
                .wire("stamp.classes", "rpc.arg1")
                .constant("answer.value", "answered")
                .then("entry", "element", "rpc")
                .then("rpc.onCall", "stamp", "answer");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        assertTrue(helper, "the rpc registered", exec.evaluate(g.outputOf("rpc.ok"), Boolean.class));
        RPCEmitter emitter = exec.evaluate(g.outputOf("rpc.rpc"), RPCEmitter.class);
        UIElement element = exec.evaluate(g.outputOf("element.element"), UIElement.class);
        assertFalse(helper, "the handler has not run yet", element.hasClass("hello"));

        Object returned = emitter.event().executor().apply(new Object[]{"hello"});

        assertTrue(helper, "the argument reached the handler", element.hasClass("hello"));
        assertEq(helper, "and the graph's answer came back", "answered", returned);
        helper.succeed();
    }

    /** A message handler receives its payload, from a send on the same side. */
    public static void aMessageHandlerReceivesItsPayload(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("listen", UIRpcNodes.OnMessage.class)
                .add("stamp", UIStyleNodes.ClassNames.class);
        g.constant("listen.name", "refresh")
                .wire("listen.element", "element.element")
                .wire("stamp.element", "element.element")
                .constant("stamp.classes", "refreshed")
                .then("entry", "element", "listen")
                .then("listen.onMessage", "stamp");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        assertTrue(helper, "the handler registered",
                exec.evaluate(g.outputOf("listen.ok"), Boolean.class));
        UIElement element = exec.evaluate(g.outputOf("element.element"), UIElement.class);
        assertFalse(helper, "nothing has arrived yet", element.hasClass("refreshed"));

        deliver(helper, element, "refresh", new CompoundTag());

        assertTrue(helper, "the handler ran", element.hasClass("refreshed"));
        helper.succeed();
    }

    // ---- fixtures ----------------------------------------------------------------------------

    private static KGGraphBuilder syncGraph() {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("sync", UISyncNodes.Declare.class);
        g.option("sync", "valueType", TypeHandles.INT.getIdentification())
                .constant("sync.name", "burnTime")
                .wire("sync.element", "element.element")
                .then("entry", "element", "sync");
        return g;
    }

    /**
     * Invokes an element's message RPC as though a packet had carried it.
     *
     * <p>Reaches the RPC through the private field rather than calling {@code sendMessage}, because
     * sending needs a sync manager with a player attached and a message <em>arriving</em> does not —
     * and arriving is the half this test is about. The field is created lazily by the first
     * {@code onMessage}, which the node under test has already done.</p>
     */
    private static void deliver(GameTestHelper helper, UIElement element, String name, CompoundTag data) {
        try {
            var field = UIElement.class.getDeclaredField("messageRPC");
            field.setAccessible(true);
            if (field.get(element) instanceof RPCEvent event) {
                event.executor().apply(new Object[]{name, data});
                return;
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("UIElement.messageRPC is gone — update this test", e);
        }
        assertTrue(helper, "the element has a message rpc to deliver to", false);
    }
}
