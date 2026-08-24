package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.doc.UIDocNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIElementNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIValueNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.event.UIDragNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.event.UIEventNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.style.UIStyleNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import net.minecraft.gametest.framework.GameTestHelper;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * The deferred exec output: a graph that runs once to build a UI, and again — at the same node —
 * every time the player clicks.
 *
 * <p>This is the mechanism the whole {@code ldlib2_ui_event} / {@code ldlib2_ui_sync} half rests on,
 * and it is the only place in KilaGraph where the executor is re-entered from outside a flow. Each
 * test here pins one property that would fail silently rather than loudly if it broke:</p>
 *
 * <ul>
 *   <li>the handler runs <em>at all</em>, and only when the event fires;</li>
 *   <li>it runs on the element that was clicked, not a rebuilt copy of it — the failure mode a
 *       pure-data {@code element_new} would have caused, where everything looks wired and nothing
 *       happens;</li>
 *   <li>what the handler reads is current rather than memoised from build time;</li>
 *   <li>the registration pass and the dispatch pass take different exec outputs.</li>
 * </ul>
 *
 * <h2>What runs where</h2>
 * A game test runs on the server thread, so {@code LDLib2.isServer()} is true and everything visual
 * is a no-op. That is fine for these: event listeners, dispatch, ids, classes and the tree itself all
 * behave identically on both sides. Rendered layout and the style cascade are not testable here and
 * are not tested here.
 */
public final class Ldlib2UiEventGameTest {
    private static final String HANDLER_RUNS_ONLY_WHEN_THE_EVENT_FIRES = "ldlib2_ui_event_handler_runs_only_when_the_event_fires";
    private static final String THE_HANDLER_ACTS_ON_THE_CLICKED_INSTANCE = "ldlib2_ui_event_the_handler_acts_on_the_clicked_instance";
    private static final String THE_HANDLER_SEES_CURRENT_VALUES_NOT_BUILD_TIME_ONES = "ldlib2_ui_event_the_handler_sees_current_values_not_build_time_ones";
    private static final String REGISTRATION_AND_DISPATCH_TAKE_DIFFERENT_OUTPUTS = "ldlib2_ui_event_registration_and_dispatch_take_different_outputs";
    private static final String ONE_EVENT_CAUSES_EXACTLY_ONE_DISPATCH = "ldlib2_ui_event_one_event_causes_exactly_one_dispatch";
    private static final String A_DRAG_CARRIES_ITS_PAYLOAD_TO_THE_DROP_HANDLER = "ldlib2_ui_event_a_drag_carries_its_payload_to_the_drop_handler";
    private static final String A_DRAG_ON_AN_UNMOUNTED_ELEMENT_REFUSES = "ldlib2_ui_event_a_drag_on_an_unmounted_element_refuses";

    public static void registerFunctions() {
        KGGameTests.registerFunction(HANDLER_RUNS_ONLY_WHEN_THE_EVENT_FIRES, Ldlib2UiEventGameTest::handlerRunsOnlyWhenTheEventFires);
        KGGameTests.registerFunction(THE_HANDLER_ACTS_ON_THE_CLICKED_INSTANCE, Ldlib2UiEventGameTest::theHandlerActsOnTheClickedInstance);
        KGGameTests.registerFunction(THE_HANDLER_SEES_CURRENT_VALUES_NOT_BUILD_TIME_ONES, Ldlib2UiEventGameTest::theHandlerSeesCurrentValuesNotBuildTimeOnes);
        KGGameTests.registerFunction(REGISTRATION_AND_DISPATCH_TAKE_DIFFERENT_OUTPUTS, Ldlib2UiEventGameTest::registrationAndDispatchTakeDifferentOutputs);
        KGGameTests.registerFunction(ONE_EVENT_CAUSES_EXACTLY_ONE_DISPATCH, Ldlib2UiEventGameTest::oneEventCausesExactlyOneDispatch);
        KGGameTests.registerFunction(A_DRAG_CARRIES_ITS_PAYLOAD_TO_THE_DROP_HANDLER, Ldlib2UiEventGameTest::aDragCarriesItsPayloadToTheDropHandler);
        KGGameTests.registerFunction(A_DRAG_ON_AN_UNMOUNTED_ELEMENT_REFUSES, Ldlib2UiEventGameTest::aDragOnAnUnmountedElementRefuses);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                HANDLER_RUNS_ONLY_WHEN_THE_EVENT_FIRES, THE_HANDLER_ACTS_ON_THE_CLICKED_INSTANCE, THE_HANDLER_SEES_CURRENT_VALUES_NOT_BUILD_TIME_ONES,
                REGISTRATION_AND_DISPATCH_TAKE_DIFFERENT_OUTPUTS, ONE_EVENT_CAUSES_EXACTLY_ONE_DISPATCH, A_DRAG_CARRIES_ITS_PAYLOAD_TO_THE_DROP_HANDLER,
                A_DRAG_ON_AN_UNMOUNTED_ELEMENT_REFUSES
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private Ldlib2UiEventGameTest() {
    }

    /**
     * <pre>
     * entry → newButton → onClick ──next──▶ (build ends)
     *                        ╰───onEvent──▶ addClass("clicked")
     * </pre>
     *
     * <p>Building must not run the handler; clicking must.</p>
     */
    public static void handlerRunsOnlyWhenTheEventFires(GameTestHelper helper) {
        var g = clickGraph();
        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        UIElement button = built(g, exec);
        assertTrue(helper, "the button was built", button instanceof Button);
        assertFalse(helper, "the handler has not run yet", button.hasClass("clicked"));

        click(button);
        assertTrue(helper, "the handler ran on click", button.hasClass("clicked"));

        helper.succeed();
    }

    /**
     * The handler must act on the element the event reached — the same instance the listener was
     * attached to.
     *
     * <p>The way this breaks is worth stating, because it is invisible from the graph: a dispatch
     * clears the pull cache before re-entering, so a constructor node that recomputed on pull would
     * build a <em>second</em> button, and the handler would style that one while the player stared at
     * the first. Everything would be wired correctly and nothing would happen.</p>
     */
    public static void theHandlerActsOnTheClickedInstance(GameTestHelper helper) {
        var g = clickGraph();
        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        UIElement button = built(g, exec);
        click(button);

        // Pull the constructor again, exactly as a handler would: still the same object.
        UIElement pulledAfterwards = built(g, exec);
        assertTrue(helper, "the constructor node keeps its identity across a dispatch",
                button == pulledAfterwards);
        assertTrue(helper, "the class landed on the element that was clicked",
                button.hasClass("clicked"));

        helper.succeed();
    }

    /**
     * The handler reads the UI as it is <em>now</em>.
     *
     * <pre>
     * entry → newButton → setText("before") → onClick ──onEvent──▶ getText → (asserted after)
     * </pre>
     *
     * <p>The text is changed from outside the graph between the build and the click. If the dispatch
     * did not clear the pull cache, {@code getText} would still answer {@code "before"} — the value
     * memoised while the tree was being assembled.</p>
     */
    public static void theHandlerSeesCurrentValuesNotBuildTimeOnes(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("newButton", UIElementNodes.New.class)
                .add("onClick", UIEventNodes.OnEvent.class)
                .add("readText", UIValueNodes.GetText.class)
                .add("stamp", UIStyleNodes.ClassNames.class);
        g.option("newButton", "type", "button")
                .constant("newButton.id", "ok")
                .option("onClick", "eventType", UIEvents.CLICK)
                .wire("onClick.element", "newButton.element")
                .wire("readText.element", "newButton.element")
                .wire("stamp.element", "newButton.element")
                // The handler stamps whatever the button's caption says at the moment it fires.
                .wire("stamp.classes", "readText.string")
                .then("entry", "newButton", "onClick")
                .then("onClick.onEvent", "stamp");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        UIElement button = built(g, exec);
        ((Button) button).setText("before");
        click(button);
        assertTrue(helper, "the handler read the caption at click time",
                button.hasClass("before"));

        // Change it behind the graph's back, then click again.
        ((Button) button).setText("after");
        click(button);
        assertTrue(helper, "a second dispatch re-read the caption", button.hasClass("after"));

        helper.succeed();
    }

    /**
     * Registering must not fire the handler's chain, and firing must not re-register.
     *
     * <p>Both directions matter. A node that flowed {@code onEvent} during registration would style
     * the button before anyone touched it; one that flowed {@code next} again during a dispatch would
     * re-run the rest of the build on every click. The two classes distinguish the cases, which a
     * single flag could not.</p>
     */
    public static void registrationAndDispatchTakeDifferentOutputs(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("newButton", UIElementNodes.New.class)
                .add("onClick", UIEventNodes.OnEvent.class)
                .add("afterBuild", UIStyleNodes.ClassNames.class)
                .add("onEventStamp", UIStyleNodes.ClassNames.class);
        g.option("newButton", "type", "button")
                .option("onClick", "eventType", UIEvents.CLICK)
                .wire("onClick.element", "newButton.element")
                .wire("afterBuild.element", "newButton.element")
                .wire("onEventStamp.element", "newButton.element")
                .constant("afterBuild.classes", "built")
                .constant("onEventStamp.classes", "clicked")
                .then("entry", "newButton", "onClick")
                // Both hops off onClick are named: it has two exec outputs, and the bare form
                // refuses to guess which — which is the whole point of the node.
                .then("onClick.next", "afterBuild")
                .then("onClick.onEvent", "onEventStamp");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        UIElement button = built(g, exec);
        assertTrue(helper, "next ran during the build", button.hasClass("built"));
        assertFalse(helper, "onEvent did not run during the build", button.hasClass("clicked"));

        // The build path is idempotent, so "it ran again" cannot be seen by looking at the class it
        // sets. What can be seen is the reverse: remove it first, then check the dispatch did not
        // put it back.
        button.removeClass("built");
        click(button);
        assertTrue(helper, "onEvent ran on click", button.hasClass("clicked"));
        assertFalse(helper, "the build path did not run again", button.hasClass("built"));

        helper.succeed();
    }

    /** A click dispatched an odd number of times must leave a toggled class on, not off. */
    public static void oneEventCausesExactlyOneDispatch(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("newButton", UIElementNodes.New.class)
                .add("onClick", UIEventNodes.OnEvent.class)
                .add("toggle", UIStyleNodes.ClassNames.class);
        g.option("newButton", "type", "button")
                .option("onClick", "eventType", UIEvents.CLICK)
                .option("toggle", "op", UIStyleNodes.ClassOp.TOGGLE)
                .wire("onClick.element", "newButton.element")
                .wire("toggle.element", "newButton.element")
                .constant("toggle.classes", "on")
                .then("entry", "newButton", "onClick")
                .then("onClick.onEvent", "toggle");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        UIElement button = built(g, exec);

        click(button);
        assertTrue(helper, "one click toggled once", button.hasClass("on"));
        click(button);
        assertFalse(helper, "two clicks toggled twice", button.hasClass("on"));
        click(button);
        assertTrue(helper, "three clicks toggled three times", button.hasClass("on"));

        helper.succeed();
    }

    /**
     * A drag started by the graph is readable by the graph that receives it.
     *
     * <p>The two halves are tested together because neither is useful alone: starting a drag nobody
     * can inspect, or inspecting a drag nobody can start, are both dead ends. The payload deliberately
     * survives untyped — that is what lets a UI drag whatever it means by "an item".</p>
     */
    public static void aDragCarriesItsPayloadToTheDropHandler(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("root", UIElementNodes.New.class)
                .add("ui", UIDocNodes.Create.class)
                .add("mui", UIDocNodes.ModularCreate.class)
                .add("begin", UIDragNodes.StartDrag.class)
                .add("info", UIDragNodes.DragInfo.class);
        g.variable("payload", String.class, "gold_ingot", VariableKind.INPUT)
                .wire("ui.root", "root.element")
                .wire("mui.ui", "ui.ui")
                .wire("begin.element", "root.element")
                .wire("begin.payload", "payload")
                .wire("info.element", "root.element")
                .then("entry", "root", "ui", "mui");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        // The host mounts the tree; without that there is no DragHandler to start a drag on.
        exec.evaluate(g.outputOf("mui.mui"), ModularUI.class).setMenu(null);

        assertFalse(helper, "nothing is being dragged yet",
                exec.evaluate(g.outputOf("info.dragging"), Boolean.class));

        exec.executeFrom(g.node("begin"));
        assertTrue(helper, "the drag started", exec.evaluate(g.outputOf("begin.ok"), Boolean.class));

        exec.clearCache();
        assertTrue(helper, "a drag is in progress",
                exec.evaluate(g.outputOf("info.dragging"), Boolean.class));
        assertEq(helper, "and the payload survived", "gold_ingot",
                exec.evaluate(g.outputOf("info.payload"), String.class));
        assertTrue(helper, "the source is the element it started from",
                exec.evaluate(g.outputOf("info.source"), UIElement.class)
                        == exec.evaluate(g.outputOf("root.element"), UIElement.class));
        helper.succeed();
    }

    /** Starting a drag on an unmounted element refuses rather than throwing. */
    public static void aDragOnAnUnmountedElementRefuses(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("begin", UIDragNodes.StartDrag.class);
        g.wire("begin.element", "element.element").then("entry", "element", "begin");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        assertFalse(helper, "it refused", exec.evaluate(g.outputOf("begin.ok"), Boolean.class));
        helper.succeed();
    }

    // ---- fixtures ----------------------------------------------------------------------------

    /** entry → newButton(button) → onClick, whose onEvent adds the class {@code clicked}. */
    private static KGGraphBuilder clickGraph() {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("newButton", UIElementNodes.New.class)
                .add("onClick", UIEventNodes.OnEvent.class)
                .add("stamp", UIStyleNodes.ClassNames.class);
        g.option("newButton", "type", "button")
                .constant("newButton.id", "ok")
                .option("onClick", "eventType", UIEvents.CLICK)
                .wire("onClick.element", "newButton.element")
                .wire("stamp.element", "newButton.element")
                .constant("stamp.classes", "clicked")
                .then("entry", "newButton", "onClick")
                .then("onClick.onEvent", "stamp");
        return g;
    }

    /** The element the constructor node produced, read the way a downstream node would. */
    private static UIElement built(KGGraphBuilder g, GraphExecutor exec) {
        return exec.evaluate(g.outputOf("newButton.element"), UIElement.class);
    }

    /**
     * Sends a real click through LDLib2's dispatcher.
     *
     * <p>Not a direct call into the listener: the point of the test is that the graph is reachable
     * from the path a mouse press actually takes, capture and bubble phases included.</p>
     */
    private static void click(UIElement target) {
        UIEvent event = UIEvent.create(UIEvents.CLICK);
        event.target = target;
        event.button = 0;
        UIEventDispatcher.dispatchEvent(event, true, true, false);
    }
}
