package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.doc.UIDocNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.element.UIElementNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.style.UIStyleNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.style.UIStylesheetNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleOrigin;
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
 * The style pipelines, and the order they beat each other in.
 *
 * <h2>What is and is not testable here</h2>
 * A game test runs on the server thread, where LDLib2's style <em>engine</em> does not exist. What
 * does exist, and works identically on both sides, is everything these nodes actually touch: the
 * element's style bag, the class set, and the stylesheet parser. So the cascade is testable — which
 * matters, because the cascade is the part a graph author has to reason about — while the rendered
 * result is not, and is not asserted.
 *
 * <p>{@code ldlib2_ui_apply_stylesheet} is what lets the stylesheet half be covered at all: it is the
 * match-and-apply step the engine would have done, performed explicitly.</p>
 */
public final class Ldlib2UiStyleGameTest {
    private static final String HIGHER_ORIGINS_WIN = "ldlib2_ui_style_higher_origins_win";
    private static final String REMOVING_ONE_LAYER_REVEALS_THE_ONE_BELOW = "ldlib2_ui_style_removing_one_layer_reveals_the_one_below";
    private static final String AN_LSS_BLOCK_MATCHES_THE_DECLARATION_PARSER = "ldlib2_ui_style_an_lss_block_matches_the_declaration_parser";
    private static final String AN_UNKNOWN_DECLARATION_DOES_NOT_FAIL_THE_BLOCK = "ldlib2_ui_style_an_unknown_declaration_does_not_fail_the_block";
    private static final String AN_INTEGER_WIRED_INTO_A_COLOUR_IS_FORMATTED_AS_HEX = "ldlib2_ui_style_an_integer_wired_into_a_colour_is_formatted_as_hex";
    private static final String CLASS_OPERATIONS_BEHAVE = "ldlib2_ui_style_class_operations_behave";
    private static final String APPLY_STYLESHEET_MATCHES_SELECTORS = "ldlib2_ui_style_apply_stylesheet_matches_selectors";
    private static final String A_LOCAL_STYLESHEET_IS_ATTACHED_AND_DETACHED = "ldlib2_ui_style_a_local_stylesheet_is_attached_and_detached";

    public static void registerFunctions() {
        KGGameTests.registerFunction(HIGHER_ORIGINS_WIN, Ldlib2UiStyleGameTest::higherOriginsWin);
        KGGameTests.registerFunction(REMOVING_ONE_LAYER_REVEALS_THE_ONE_BELOW, Ldlib2UiStyleGameTest::removingOneLayerRevealsTheOneBelow);
        KGGameTests.registerFunction(AN_LSS_BLOCK_MATCHES_THE_DECLARATION_PARSER, Ldlib2UiStyleGameTest::anLssBlockMatchesTheDeclarationParser);
        KGGameTests.registerFunction(AN_UNKNOWN_DECLARATION_DOES_NOT_FAIL_THE_BLOCK, Ldlib2UiStyleGameTest::anUnknownDeclarationDoesNotFailTheBlock);
        KGGameTests.registerFunction(AN_INTEGER_WIRED_INTO_A_COLOUR_IS_FORMATTED_AS_HEX, Ldlib2UiStyleGameTest::anIntegerWiredIntoAColourIsFormattedAsHex);
        KGGameTests.registerFunction(CLASS_OPERATIONS_BEHAVE, Ldlib2UiStyleGameTest::classOperationsBehave);
        KGGameTests.registerFunction(APPLY_STYLESHEET_MATCHES_SELECTORS, Ldlib2UiStyleGameTest::applyStylesheetMatchesSelectors);
        KGGameTests.registerFunction(A_LOCAL_STYLESHEET_IS_ATTACHED_AND_DETACHED, Ldlib2UiStyleGameTest::aLocalStylesheetIsAttachedAndDetached);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                HIGHER_ORIGINS_WIN, REMOVING_ONE_LAYER_REVEALS_THE_ONE_BELOW, AN_LSS_BLOCK_MATCHES_THE_DECLARATION_PARSER,
                AN_UNKNOWN_DECLARATION_DOES_NOT_FAIL_THE_BLOCK, AN_INTEGER_WIRED_INTO_A_COLOUR_IS_FORMATTED_AS_HEX, CLASS_OPERATIONS_BEHAVE,
                APPLY_STYLESHEET_MATCHES_SELECTORS, A_LOCAL_STYLESHEET_IS_ATTACHED_AND_DETACHED
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private Ldlib2UiStyleGameTest() {
    }

    /**
     * The origin ordering: stylesheet loses to inline, inline loses to important.
     *
     * <p>This is the whole reason the origin is an option rather than a constant. A graph that sets a
     * hover colour inline must beat the sheet; a graph that forces a colour must beat both.</p>
     */
    public static void higherOriginsWin(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("sheetLayer", UIStyleNodes.LssSet.class)
                .add("inlineLayer", UIStyleNodes.LssSet.class)
                .add("importantLayer", UIStyleNodes.LssSet.class);
        for (String node : new String[]{"sheetLayer", "inlineLayer", "importantLayer"}) {
            g.option(node, "property", "color").wire(node + ".element", "element.element");
        }
        g.option("sheetLayer", "origin", StyleOrigin.STYLESHEET)
                .option("inlineLayer", "origin", StyleOrigin.INLINE)
                .option("importantLayer", "origin", StyleOrigin.IMPORTANT);
        g.constant("sheetLayer.value", "#ff0000ff")
                .constant("inlineLayer.value", "#ff00ff00")
                .constant("importantLayer.value", "#ffff0000")
                .then("entry", "element");

        var exec = new GraphExecutor(g.graph());
        // Applied lowest-first, so a naive "last write wins" implementation would end up on red
        // by accident. Ordering the flow this way makes the assertion mean something.
        exec.executeFrom(g.node("entry"));
        UIElement element = exec.evaluate(g.outputOf("element.element"), UIElement.class);

        exec.executeFrom(g.node("sheetLayer"));
        assertEq(helper, "only the sheet layer is set", 0xFF0000FF, colour(element));

        exec.executeFrom(g.node("inlineLayer"));
        assertEq(helper, "inline beats stylesheet", 0xFF00FF00, colour(element));

        exec.executeFrom(g.node("importantLayer"));
        assertEq(helper, "important beats inline", 0xFFFF0000, colour(element));
        helper.succeed();
    }

    /** Removing the graph's own value at one layer lets the layer beneath show through again. */
    public static void removingOneLayerRevealsTheOneBelow(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("sheetLayer", UIStyleNodes.LssSet.class)
                .add("inlineLayer", UIStyleNodes.LssSet.class)
                .add("undo", UIStyleNodes.LssRemove.class);
        for (String node : new String[]{"sheetLayer", "inlineLayer", "undo"}) {
            g.option(node, "property", "color").wire(node + ".element", "element.element");
        }
        g.option("sheetLayer", "origin", StyleOrigin.STYLESHEET)
                .option("inlineLayer", "origin", StyleOrigin.INLINE)
                .option("undo", "origin", StyleOrigin.INLINE)
                .constant("sheetLayer.value", "#ff0000ff")
                .constant("inlineLayer.value", "#ff00ff00")
                .then("entry", "element", "sheetLayer", "inlineLayer", "undo");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        UIElement element = exec.evaluate(g.outputOf("element.element"), UIElement.class);
        assertEq(helper, "the stylesheet value is back", 0xFF0000FF, colour(element));
        helper.succeed();
    }

    /**
     * A declaration block resolves to exactly what LDLib2's own declaration parser produces.
     *
     * <p>Compared against the parser rather than against hand-picked numbers, because "the same as
     * the {@code style="…"} attribute means" is the actual contract — this node and the xml attribute
     * are supposed to be the same operation.</p>
     *
     * <p>The xml attribute itself is not used as the reference, despite being the thing being
     * matched: {@code UIElement.loadXml} skips inline styles entirely when {@code isServer()}, and a
     * game test runs on the server thread. The node deliberately has no such guard — it writes the
     * style bag directly, which is data rather than rendering — so on this thread the two genuinely
     * differ, and asserting they agree would be asserting something false.</p>
     */
    public static void anLssBlockMatchesTheDeclarationParser(GameTestHelper helper) {
        String declarations = "color: #ff3355aa; opacity: 0.5;";
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("block", UIStyleNodes.LssBlock.class);
        g.wire("block.element", "element.element")
                .option("block", "origin", StyleOrigin.INLINE)
                .constant("block.declarations", declarations)
                .then("entry", "element", "block");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        UIElement element = exec.evaluate(g.outputOf("element.element"), UIElement.class);

        assertEq(helper, "two declarations applied", 2,
                (int) exec.evaluate(g.outputOf("block.applied"), Integer.class));

        var expected = Stylesheet.parseStyleValues(declarations);
        for (var entry : expected.entrySet()) {
            assertEq(helper, "property " + entry.getKey().name,
                    entry.getValue().compute(),
                    element.getStyleBag().computeCandidate(entry.getKey()));
        }
        helper.succeed();
    }

    /** An unknown property in a block is skipped, and the rest of the block still applies. */
    public static void anUnknownDeclarationDoesNotFailTheBlock(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("block", UIStyleNodes.LssBlock.class);
        g.wire("block.element", "element.element")
                .constant("block.declarations", "not-a-real-property: 3; color: #ff112233;")
                .then("entry", "element", "block");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        assertEq(helper, "only the real one applied", 1,
                (int) exec.evaluate(g.outputOf("block.applied"), Integer.class));
        assertEq(helper, "and it applied correctly", 0xFF112233,
                colour(exec.evaluate(g.outputOf("element.element"), UIElement.class)));
        helper.succeed();
    }

    /**
     * A number <em>wired</em> into a colour property is rendered as {@code #AARRGGBB}, not as a decimal.
     *
     * <p>The value comes over a wire rather than from the pin's own constant, because that is the case
     * the conversion exists for: an ARGB int is how every other colour in this graph travels, and the
     * LSS colour parser does not accept a bare signed integer. Typing into the pin gives a String,
     * which is passed through untouched.</p>
     */
    public static void anIntegerWiredIntoAColourIsFormattedAsHex(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("set", UIStyleNodes.LssSet.class);
        g.variable("argb", int.class, 0xFF3366CC, VariableKind.INPUT)
                .option("set", "property", "color")
                .wire("set.element", "element.element")
                .wire("set.value", "argb")
                .then("entry", "element", "set");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        assertTrue(helper, "it was accepted", exec.evaluate(g.outputOf("set.ok"), Boolean.class));
        assertEq(helper, "and kept its ARGB value", 0xFF3366CC,
                colour(exec.evaluate(g.outputOf("element.element"), UIElement.class)));
        helper.succeed();
    }

    /** Class names go on, come off, and toggle. */
    public static void classOperationsBehave(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("add", UIStyleNodes.ClassNames.class)
                .add("remove", UIStyleNodes.ClassNames.class)
                .add("replace", UIStyleNodes.ClassNames.class);
        for (String node : new String[]{"add", "remove", "replace"}) {
            g.wire(node + ".element", "element.element");
        }
        g.then("entry", "element");
        g.option("add", "op", UIStyleNodes.ClassOp.ADD).constant("add.classes", "alpha beta")
                .option("remove", "op", UIStyleNodes.ClassOp.REMOVE).constant("remove.classes", "alpha")
                .option("replace", "op", UIStyleNodes.ClassOp.SET).constant("replace.classes", "gamma");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        UIElement element = exec.evaluate(g.outputOf("element.element"), UIElement.class);

        exec.executeFrom(g.node("add"));
        assertTrue(helper, "alpha added", element.hasClass("alpha"));
        assertTrue(helper, "beta added", element.hasClass("beta"));

        exec.executeFrom(g.node("remove"));
        assertFalse(helper, "alpha removed", element.hasClass("alpha"));
        assertTrue(helper, "beta untouched", element.hasClass("beta"));

        exec.executeFrom(g.node("replace"));
        assertFalse(helper, "set replaced beta", element.hasClass("beta"));
        assertTrue(helper, "with gamma", element.hasClass("gamma"));
        helper.succeed();
    }

    /**
     * A parsed stylesheet, matched by hand, reaches the elements its selectors name — and only those.
     *
     * <p>Covers the pipeline the style engine owns on the client, in the one place it can be checked
     * without a screen.</p>
     */
    public static void applyStylesheetMatchesSelectors(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("parse", UIDocNodes.ParseXml.class)
                .add("sheet", UIStylesheetNodes.Parse.class)
                .add("apply", UIStylesheetNodes.ApplyStylesheet.class);
        g.constant("parse.xml", "<button id=\"ok\" class=\"primary\"/><label id=\"caption\"/>")
                .constant("sheet.lss", ".primary { color: #ff00ff00; }")
                .wire("apply.element", "parse.root")
                .wire("apply.stylesheet", "sheet.stylesheet")
                .then("entry", "parse", "sheet", "apply");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));

        assertEq(helper, "one rule parsed", 1,
                (int) exec.evaluate(g.outputOf("sheet.rules"), Integer.class));
        UIElement root = exec.evaluate(g.outputOf("parse.root"), UIElement.class);
        UIElement button = root.getChildren().getFirst();
        UIElement label = root.getChildren().get(1);

        assertEq(helper, "the .primary rule reached the button", 0xFF00FF00, colour(button));
        assertFalse(helper, "and did not reach the label", colour(label) == 0xFF00FF00);
        assertTrue(helper, "the node reported a match",
                exec.evaluate(g.outputOf("apply.matched"), Integer.class) > 0);
        helper.succeed();
    }

    /** A local stylesheet is recorded on the element even where no engine will resolve it. */
    public static void aLocalStylesheetIsAttachedAndDetached(GameTestHelper helper) {
        var g = KGGraphBuilder.blueprint()
                .add("entry", EntryNode.class)
                .add("element", UIElementNodes.New.class)
                .add("attach", UIStylesheetNodes.LocalStylesheet.class)
                .add("clear", UIStylesheetNodes.LocalStylesheet.class);
        g.wire("attach.element", "element.element")
                .wire("clear.element", "element.element")
                .constant("attach.lss", ".x { color: #ff000000; }")
                .option("attach", "op", UIStylesheetNodes.LocalOp.ADD)
                .option("clear", "op", UIStylesheetNodes.LocalOp.CLEAR)
                .then("entry", "element");

        var exec = new GraphExecutor(g.graph());
        exec.executeFrom(g.node("entry"));
        UIElement element = exec.evaluate(g.outputOf("element.element"), UIElement.class);

        exec.executeFrom(g.node("attach"));
        assertEq(helper, "one local sheet", 1, element.getLocalStylesheets().size());

        exec.executeFrom(g.node("clear"));
        assertEq(helper, "cleared", 0, element.getLocalStylesheets().size());
        helper.succeed();
    }

    /**
     * The computed colour of an element, or {@code 0} when nothing has set one.
     *
     * <p>An {@code int} rather than an {@code Integer} so the assertions read as numbers; {@code 0}
     * is safe as the "unset" marker because it is fully transparent black, which no test sets.</p>
     */
    private static int colour(UIElement element) {
        Integer value = element.getStyleBag().computeCandidate(PropertyRegistry.COLOR);
        return value == null ? 0 : value;
    }
}
