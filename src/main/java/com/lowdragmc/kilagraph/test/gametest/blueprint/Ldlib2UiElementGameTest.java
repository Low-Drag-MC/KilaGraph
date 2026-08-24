package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.doc.UIDocNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIElementNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIQueryNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIValueNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.ui.UIPropertyRegistry;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * Building, querying and filling in a UI tree.
 *
 * <p>The two assertions worth reading here are the property ones. {@code ldlib2_ui_set_property} is
 * the one node in the set that reaches an element by <em>name</em> rather than by a typed API, so it
 * is the one that could quietly do the wrong thing: write a field while skipping the behaviour that
 * is supposed to come with it, or resolve a name against the wrong class. Both are pinned below by
 * observing a side effect, not by reading the field back.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class Ldlib2UiElementGameTest {

    private Ldlib2UiElementGameTest() {
    }

    /** The type option picks out of LDLib2's registry — the same table an xml tag resolves through. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void elementNewBuildsTheRegisteredType(GameTestHelper helper) {
        assertTrue(helper, "button", built("button") instanceof Button);
        assertTrue(helper, "label", built("label") instanceof Label);
        assertTrue(helper, "progress-bar", built("progress-bar") instanceof ProgressBar);
        // An unknown type degrades to a plain container rather than failing the whole build.
        UIElement unknown = built("no_such_element_type");
        assertTrue(helper, "unknown type still builds something", unknown != null);
        assertEq(helper, "and it is a plain element", UIElement.class, unknown.getClass());
        helper.succeed();
    }

    /** Children go in, come back in order, and a cycle is refused. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void childrenAreAddedInOrderAndCyclesAreRefused(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("panel", UIElementNodes.New.class)
                .add("first", UIElementNodes.New.class)
                .add("second", UIElementNodes.New.class)
                .add("addFirst", UIElementNodes.AddChild.class)
                .add("addSecond", UIElementNodes.AddChild.class)
                .add("cycle", UIElementNodes.AddChild.class);
        g.option("first", "type", "button")
                .option("second", "type", "label")
                .wire("addFirst.parent", "panel.element")
                .wire("addFirst.child", "first.element")
                .wire("addSecond.parent", "panel.element")
                .wire("addSecond.child", "second.element")
                // Try to make the panel a child of its own child.
                .wire("cycle.parent", "first.element")
                .wire("cycle.child", "panel.element")
                .then("entry", "panel", "first", "second", "addFirst", "addSecond", "cycle");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        UIElement panel = exec.evaluate(g.outputOf("panel.element"), UIElement.class);
        assertEq(helper, "two children", 2, panel.getChildren().size());
        assertTrue(helper, "in wiring order", panel.getChildren().getFirst() instanceof Button);
        assertFalse(helper, "the cycle was refused",
                exec.evaluate(g.outputOf("cycle.ok"), Boolean.class));
        helper.succeed();
    }

    /** Selectors find by id, by class and by type, from a tree built out of xml. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void selectorsFindByIdClassAndType(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("parse", UIDocNodes.ParseXml.class)
                .add("byId", UIQueryNodes.SelectId.class)
                .add("byClass", UIQueryNodes.Select.class)
                .add("byType", UIQueryNodes.Select.class)
                .add("byRegex", UIQueryNodes.SelectRegex.class);
        g.constant("parse.xml", "<button id=\"row_0\" class=\"row\"/>"
                        + "<button id=\"row_1\" class=\"row\"/>"
                        + "<label id=\"caption\"/>")
                .wire("byId.root", "parse.root").constant("byId.id", "caption")
                .wire("byClass.root", "parse.root").constant("byClass.selector", ".row")
                .wire("byType.root", "parse.root").constant("byType.selector", "label")
                .wire("byRegex.root", "parse.root").constant("byRegex.regex", "row_\\d+")
                .then("entry", "parse");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        assertEq(helper, "#caption", 1, (int) exec.evaluate(g.outputOf("byId.count"), Integer.class));
        assertEq(helper, ".row", 2, (int) exec.evaluate(g.outputOf("byClass.count"), Integer.class));
        assertEq(helper, "label", 1, (int) exec.evaluate(g.outputOf("byType.count"), Integer.class));
        assertEq(helper, "row_\\d+", 2, (int) exec.evaluate(g.outputOf("byRegex.count"), Integer.class));
        assertTrue(helper, "first is the label",
                exec.evaluate(g.outputOf("byId.first"), UIElement.class) instanceof Label);
        helper.succeed();
    }

    /**
     * {@code set_property} writes through the field's annotated setter, not around it.
     *
     * <p>Proven by a side effect rather than by reading the field back, because reading it back
     * cannot tell the two apart: {@code Toggle.setOn} notifies its listeners, and writing the field
     * directly does not. A listener that never fires is exactly the failure a graph would experience
     * as "the toggle changed but nothing reacted".</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void setPropertyGoesThroughTheAnnotatedSetter(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("toggle", UIElementNodes.New.class)
                .add("set", UIValueNodes.SetProperty.class);
        g.option("toggle", "type", "toggle")
                .option("set", "elementType", "toggle")
                .option("set", "property", "isOn")
                .wire("set.element", "toggle.element")
                // Deliberately not chained to the toggle: the two halves are run separately so a
                // listener can be attached in between, which is the only way to see that the write
                // went through the setter rather than around it.
                .then("entry", "toggle");
        g.constant("set.value", true);

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("toggle"));
        Toggle toggle = (Toggle) exec.evaluate(g.outputOf("toggle.element"), UIElement.class);
        var notified = new AtomicInteger();
        toggle.registerValueListener(v -> notified.incrementAndGet());

        exec.executeFrom(g.node("set"));

        assertTrue(helper, "the write landed", toggle.getValue());
        assertTrue(helper, "and the setter's listeners fired", notified.get() > 0);
        assertTrue(helper, "the node reported success",
                exec.evaluate(g.outputOf("set.ok"), Boolean.class));
        helper.succeed();
    }

    /** A property name that does not exist on the element fails cleanly instead of throwing. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void anUnknownPropertyReportsFailure(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("label", UIElementNodes.New.class)
                .add("set", UIValueNodes.SetProperty.class);
        g.option("label", "type", "label")
                .option("set", "elementType", "label")
                .option("set", "property", "aPropertyThatDoesNotExist")
                .wire("set.element", "label.element")
                .then("entry", "label", "set");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        assertFalse(helper, "it refused", exec.evaluate(g.outputOf("set.ok"), Boolean.class));
        helper.succeed();
    }

    /**
     * The property registry offers what LDLib2's Inspector offers, and nothing from the style layer.
     *
     * <p>Style groups are {@code subConfigurable} fields, and letting one through here would give the
     * graph a second, competing way to set a background — the thing the LSS-only rule exists to
     * prevent.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void thePropertyRegistryExcludesStyleGroups(GameTestHelper helper) {
        var progress = UIPropertyRegistry.propertiesOfRegistered("progress-bar");
        assertTrue(helper, "value is offered", progress.containsKey("value"));
        assertTrue(helper, "minValue is offered", progress.containsKey("minValue"));
        assertTrue(helper, "maxValue is offered", progress.containsKey("maxValue"));
        assertFalse(helper, "the style group is not", progress.containsKey("progressBarStyle"));
        // Inherited UIElement properties are there too, and sort above the subclass's own.
        assertTrue(helper, "inherited id", progress.containsKey("id"));
        assertEq(helper, "the value field is a float", float.class,
                progress.get("value").type());
        helper.succeed();
    }

    /** Text reaches a Label, a Button's caption and a TextField alike. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void setTextCoversEveryCaption(GameTestHelper helper) {
        for (String type : List.of("label", "text", "button", "text-field")) {
            var g = KGGraphBuilder.blueprint()
                    .add("entry", EntryNode.class)
                    .add("element", UIElementNodes.New.class)
                    .add("set", UIValueNodes.SetText.class)
                    .add("get", UIValueNodes.GetText.class);
            g.option("element", "type", type)
                    .wire("set.element", "element.element")
                    .wire("get.element", "element.element")
                    .constant("set.text", Component.literal("hello"))
                    .then("entry", "element", "set");

            var exec = new GraphExecutor(g.graph());
            exec.executeFrom(g.node("entry"));
            assertTrue(helper, type + ": set_text reported success",
                    exec.evaluate(g.outputOf("set.ok"), Boolean.class));
            assertEq(helper, type + ": read back", "hello",
                    exec.evaluate(g.outputOf("get.string"), String.class));
        }
        helper.succeed();
    }

    /** {@code get_value} / {@code set_value} go through the element's own value interface. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void valueNodesUseTheElementsOwnValue(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("toggle", UIElementNodes.New.class)
                .add("set", UIValueNodes.SetValue.class)
                .add("get", UIValueNodes.GetValue.class);
        g.option("toggle", "type", "toggle")
                .option("set", "valueType", TypeHandles.BOOL.getIdentification())
                .option("get", "valueType", TypeHandles.BOOL.getIdentification())
                .wire("set.element", "toggle.element")
                .wire("get.element", "toggle.element")
                .then("entry", "toggle", "set");
        g.constant("set.value", true);

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        assertTrue(helper, "the write landed", exec.evaluate(g.outputOf("get.value"), Boolean.class));
        assertTrue(helper, "and the element carries a value",
                exec.evaluate(g.outputOf("get.ok"), Boolean.class));
        helper.succeed();
    }

    /** An element with no value at all reports so rather than pretending. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aPlainElementHasNoValue(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("plain", UIElementNodes.New.class)
                .add("get", UIValueNodes.GetValue.class);
        g.wire("get.element", "plain.element").then("entry", "plain");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        assertFalse(helper, "no value to read", exec.evaluate(g.outputOf("get.ok"), Boolean.class));
        helper.succeed();
    }

    /** {@code clear_children} keeps a Button's own caption unless told otherwise. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void clearChildrenKeepsInternalChildrenByDefault(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("button", UIElementNodes.New.class)
                .add("clear", UIElementNodes.ClearChildren.class);
        g.option("button", "type", "button")
                .wire("clear.element", "button.element")
                .then("entry", "button", "clear");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        Button button = (Button) exec.evaluate(g.outputOf("button.element"), UIElement.class);
        assertTrue(helper, "the caption survived", button.hasChild(button.text));
        helper.succeed();
    }

    // ---- fixtures ----------------------------------------------------------------------------

    /** Runs {@code ldlib2_ui_element_new} with a type and returns what it built. */
    private static UIElement built(String type) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class);
        g.option("element", "type", type).then("entry", "element");
        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        return exec.evaluate(g.outputOf("element.element"), UIElement.class);
    }
}
